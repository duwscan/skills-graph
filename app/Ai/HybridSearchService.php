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
        $keywordResults = $keyword['results'];
        $maxKeywordScore = $keywordResults !== []
            ? (float) collect($keywordResults)->max('score')
            : 1.0;
        if ($maxKeywordScore <= 0.0) {
            $maxKeywordScore = 1.0;
        }

        /** @var Collection<string, array{skill: Skill, rrf_score: float, match_type: string, highlights: array<string, mixed>, keyword_score?: float, similarity?: float}> $merged */
        $merged = collect();

        foreach ($keywordResults as $index => $hit) {
            $skillId = $hit['skill_id'];
            $rank = $index + 1;
            $rrfScore = 1.0 / ($k + $rank);

            /** @var Skill|null $skill */
            $skill = Skill::query()->find($skillId);

            if ($skill === null) {
                continue;
            }

            $existing = $merged->get($skillId, [
                'skill' => $skill,
                'rrf_score' => 0.0,
                'match_type' => 'keyword',
                'highlights' => [],
            ]);

            $existing['rrf_score'] += $rrfScore;
            $existing['match_type'] = 'keyword';
            $existing['highlights'] = $hit['highlights'];
            $existing['keyword_score'] = (float) $hit['score'];

            $merged->put($skillId, $existing);
        }

        foreach ($semantic as $item) {
            $skill = $item['skill'];
            $skillId = $item['skill_id'];
            $rank = $item['rank'];
            $rrfScore = 1.0 / ($k + $rank);

            $existing = $merged->get($skillId, [
                'skill' => $skill,
                'rrf_score' => 0.0,
                'match_type' => 'semantic',
                'highlights' => [],
            ]);

            $existing['rrf_score'] += $rrfScore;
            $existing['skill'] = $skill;
            $existing['similarity'] = $item['similarity'];

            if ($existing['match_type'] === 'keyword') {
                $existing['match_type'] = 'both';
            } else {
                $existing['match_type'] = 'semantic';
            }

            $merged->put($skillId, $existing);
        }

        $sorted = $merged
            ->values()
            ->sortByDesc('rrf_score')
            ->take($limit)
            ->values();

        $results = $sorted->map(function (array $item) use ($maxKeywordScore): array {
            $keywordNorm = isset($item['keyword_score'])
                ? min(1.0, (float) $item['keyword_score'] / $maxKeywordScore)
                : 0.0;
            $similarity = (float) ($item['similarity'] ?? 0.0);
            $relevance = min(1.0, 0.7 * $similarity + 0.3 * $keywordNorm);

            return [
                'skill' => $item['skill'],
                'score' => round($relevance, 6),
                'match_type' => $item['match_type'],
                'highlights' => $item['highlights'],
            ];
        })->all();

        $queryTimeMs = $keyword['search_time_ms'] ?? 0;

        return [
            'results' => $results,
            'total' => count($results),
            'query_time_ms' => $queryTimeMs,
        ];
    }
}
