<?php

namespace App\Ai;

use App\Models\Skill;
use App\Models\SkillAlias;
use Illuminate\Database\Eloquent\Collection as EloquentCollection;
use Illuminate\Support\Collection;

class VectorSearchService
{
    public function __construct(
        private readonly EmbeddingService $embeddingService,
    ) {}

    /**
     * Find similar active skills for the given embedding.
     *
     * @param  list<float>  $embedding
     * @param  array{
     *     category?: string|null,
     *     exclude_ids?: list<string>
     * }  $filters
     * @return EloquentCollection<int, Skill>
     */
    public function findSimilarSkills(array $embedding, int $limit, array $filters = []): EloquentCollection
    {
        $vector = $this->toVectorLiteral($embedding);

        $query = Skill::query()
            ->selectRaw('skills.*, 1 - (embedding <=> ?::vector) as similarity', [$vector])
            ->where('status', 'active')
            ->whereNotNull('embedding');

        if (isset($filters['category']) && $filters['category'] !== null) {
            $query->where('category', $filters['category']);
        }

        if (! empty($filters['exclude_ids'])) {
            $query->whereNotIn('id', $filters['exclude_ids']);
        }

        return $query
            ->orderByRaw('embedding <=> ?::vector asc', [$vector])
            ->limit($limit)
            ->get();
    }

    /**
     * Find similar aliases for the given embedding, including parent skill info.
     *
     * @param  list<float>  $embedding
     * @return Collection<int, array{
     *     alias: SkillAlias,
     *     skill: Skill,
     *     similarity: float
     * }>
     */
    public function findSimilarAliases(array $embedding, int $limit): Collection
    {
        $vector = $this->toVectorLiteral($embedding);

        /** @var Collection<int, array{alias: SkillAlias, skill: Skill, similarity: float}> $results */
        $results = SkillAlias::query()
            ->selectRaw('skill_aliases.*, 1 - (alias_embedding <=> ?::vector) as similarity', [$vector])
            ->join('skills', 'skills.id', '=', 'skill_aliases.skill_id')
            ->whereNotNull('alias_embedding')
            ->orderByRaw('alias_embedding <=> ?::vector asc', [$vector])
            ->limit($limit)
            ->get(['skill_aliases.*', 'skills.*'])
            ->map(function ($row): array {
                /** @var SkillAlias $alias */
                $alias = new SkillAlias((array) $row);

                /** @var Skill $skill */
                $skill = new Skill((array) $row);

                return [
                    'alias' => $alias,
                    'skill' => $skill,
                    'similarity' => (float) $row->similarity,
                ];
            });

        return $results;
    }

    /**
     * RAG retrieval: top candidate skills for a chunk embedding.
     *
     * @param  list<float>  $chunkEmbedding
     * @return Collection<int, array{
     *     id: string,
     *     external_id: string,
     *     canonical_name: string,
     *     description: string|null,
     *     similarity: float
     * }>
     */
    public function findCandidatesForChunk(array $chunkEmbedding, ?int $limit = null): Collection
    {
        $limit ??= (int) config('skills_graph.rag_candidate_limit', 100);

        $skills = $this->findSimilarSkills($chunkEmbedding, $limit);

        return $skills->map(static function (Skill $skill): array {
            return [
                'id' => $skill->id,
                'external_id' => $skill->external_id,
                'canonical_name' => $skill->canonical_name,
                'description' => $skill->description,
                'similarity' => (float) $skill->similarity,
            ];
        });
    }

    /**
     * Check if a candidate skill is a duplicate based on semantic similarity.
     *
     * @return array{
     *     isDuplicate: bool,
     *     matches: list<array{
     *         skill: Skill,
     *         similarity: float
     *     }>
     * }
     */
    public function checkDuplicate(string $name, ?string $description = null): array
    {
        $text = $description !== null && $description !== ''
            ? "{$name}: {$description}"
            : $name;

        $embedding = $this->embeddingService->embed($text);

        $limit = (int) config('skills_graph.rag_candidate_limit', 100);

        $skills = $this->findSimilarSkills($embedding, $limit);

        $duplicateThreshold = (float) config('skills_graph.similarity_duplicate_threshold', 0.9);
        $reviewThreshold = (float) config('skills_graph.similarity_review_threshold', 0.7);

        $matches = [];
        $isDuplicate = false;

        foreach ($skills as $skill) {
            $similarity = (float) $skill->similarity;

            $matches[] = [
                'skill' => $skill,
                'similarity' => $similarity,
            ];

            if ($similarity >= $duplicateThreshold) {
                $isDuplicate = true;
            }
        }

        if (! $isDuplicate && $matches !== []) {
            $topSimilarity = $matches[0]['similarity'];

            if ($topSimilarity >= $reviewThreshold && $topSimilarity < $duplicateThreshold) {
                // For now we only surface matches; classification into
                // "review" vs "new" is handled by higher-level services.
            }
        }

        return [
            'isDuplicate' => $isDuplicate,
            'matches' => $matches,
        ];
    }

    /**
     * Compute cosine similarity between two vectors.
     *
     * @param  list<float>  $a
     * @param  list<float>  $b
     */
    public function cosineSimilarity(array $a, array $b): float
    {
        if ($a === [] || $b === [] || count($a) !== count($b)) {
            return 0.0;
        }

        $dotProduct = 0.0;
        $normA = 0.0;
        $normB = 0.0;

        foreach ($a as $index => $valueA) {
            $valueB = $b[$index];

            $dotProduct += $valueA * $valueB;
            $normA += $valueA * $valueA;
            $normB += $valueB * $valueB;
        }

        if ($normA === 0.0 || $normB === 0.0) {
            return 0.0;
        }

        return $dotProduct / (sqrt($normA) * sqrt($normB));
    }

    /**
     * Convert a PHP vector into a Postgres vector literal.
     *
     * @param  list<float>  $embedding
     */
    private function toVectorLiteral(array $embedding): string
    {
        return '['.implode(',', array_map(
            static fn (float $value): string => (string) $value,
            $embedding,
        )).']';
    }
}
