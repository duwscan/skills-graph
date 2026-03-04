<?php

namespace App\Ai;

use App\Models\Skill;
use Illuminate\Support\Collection;

class HybridSearchService
{
    public function __construct(
        private readonly EmbeddingService $embeddingService,
        private readonly VectorSearchService $vectorSearch,
        private readonly TypesenseSearchService $typesenseSearch,
    ) {}

    /**
     * Perform hybrid keyword + semantic search over skills.
     *
     * @return array{
     *     results: list<array{
     *         skill: Skill,
     *         score: float,
     *         match_type: string,
     *         highlights: array<string, mixed>
     *     }>,
     *     total: int,
     *     query_time_ms: int
     * }
     */
    public function search(string $query, ?string $category, ?string $status, ?int $limit = null): array
    {
        $limit ??= (int) config('skills_graph.pagination_default_limit', 20);

        $filters = [
            'category' => $category,
            'status' => $status,
        ];

        $keyword = $this->typesenseSearch->search($query, $filters, $limit);

        $embedding = $this->embeddingService->embed($query);
        $semanticSkills = $this->vectorSearch->findSimilarSkills($embedding, $limit, $filters);

        $semantic = $semanticSkills->values()->map(function (Skill $skill, int $index): array {
            return [
                'skill_id' => $skill->id,
                'similarity' => (float) $skill->similarity,
                'rank' => $index + 1,
                'skill' => $skill,
            ];
        });

        $k = 60;

        /** @var Collection<string, array{skill: Skill, score: float, match_type: string, highlights: array<string, mixed>}> $merged */
        $merged = collect();

        foreach ($keyword['results'] as $index => $hit) {
            $skillId = $hit['skill_id'];
            $rank = $index + 1;
            $score = 1.0 / ($k + $rank);

            /** @var Skill|null $skill */
            $skill = Skill::query()->find($skillId);

            if ($skill === null) {
                continue;
            }

            $existing = $merged->get($skillId, [
                'skill' => $skill,
                'score' => 0.0,
                'match_type' => 'keyword',
                'highlights' => [],
            ]);

            $existing['score'] += $score;
            $existing['match_type'] = 'keyword';
            $existing['highlights'] = $hit['highlights'];

            $merged->put($skillId, $existing);
        }

        foreach ($semantic as $item) {
            $skill = $item['skill'];
            $skillId = $item['skill_id'];
            $rank = $item['rank'];
            $score = 1.0 / ($k + $rank);

            $existing = $merged->get($skillId, [
                'skill' => $skill,
                'score' => 0.0,
                'match_type' => 'semantic',
                'highlights' => [],
            ]);

            $existing['score'] += $score;

            if ($existing['match_type'] === 'keyword') {
                $existing['match_type'] = 'both';
            } else {
                $existing['match_type'] = 'semantic';
            }

            $merged->put($skillId, $existing);
        }

        $sorted = $merged
            ->values()
            ->sortByDesc('score')
            ->take($limit)
            ->values()
            ->all();

        $queryTimeMs = $keyword['search_time_ms'] ?? 0;

        return [
            'results' => $sorted,
            'total' => count($sorted),
            'query_time_ms' => $queryTimeMs,
        ];
    }
}
