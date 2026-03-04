<?php

namespace App\Ai;

use App\Models\Skill;
use Illuminate\Support\Collection;
use Typesense\Client;

class TypesenseSearchService
{
    private ?Client $client = null;

    public function __construct() {}

    public function setupCollection(): void
    {
        $client = $this->client();
        $collectionName = (string) config('typesense.collections.skills', 'skills');

        try {
            $client->collections[$collectionName]->delete();
        } catch (\Throwable $e) {
            // Collection may not exist yet; ignore.
        }

        $schema = [
            'name' => $collectionName,
            'fields' => [
                ['name' => 'id', 'type' => 'string'],
                ['name' => 'external_id', 'type' => 'string', 'optional' => true],
                ['name' => 'canonical_name', 'type' => 'string'],
                ['name' => 'slug', 'type' => 'string'],
                ['name' => 'description', 'type' => 'string', 'optional' => true],
                ['name' => 'category', 'type' => 'string', 'facet' => true, 'optional' => true],
                ['name' => 'status', 'type' => 'string', 'facet' => true],
                ['name' => 'aliases', 'type' => 'string[]', 'facet' => true, 'optional' => true],
            ],
            'default_sorting_field' => 'canonical_name',
        ];

        $client->collections->create($schema);
    }

    public function indexSkill(Skill $skill): void
    {
        $client = $this->client();
        $collectionName = (string) config('typesense.collections.skills', 'skills');

        $aliases = $skill->aliases()->pluck('surface_form')->all();

        $document = [
            'id' => $skill->id,
            'external_id' => $skill->external_id,
            'canonical_name' => $skill->canonical_name,
            'slug' => $skill->slug,
            'description' => $skill->description,
            'category' => $skill->category,
            'status' => $skill->status,
            'aliases' => $aliases,
        ];

        $client->collections[$collectionName]->documents->upsert($document);
    }

    public function removeSkill(string $skillId): void
    {
        $client = $this->client();
        $collectionName = (string) config('typesense.collections.skills', 'skills');

        $client->collections[$collectionName]->documents[$skillId]->delete();
    }

    public function reindexAll(): void
    {
        $this->setupCollection();

        $client = $this->client();
        $collectionName = (string) config('typesense.collections.skills', 'skills');

        Skill::query()
            ->where('status', 'active')
            ->orderBy('id')
            ->chunk(100, function ($skills) use ($collectionName): void {
                /** @var Collection<int, Skill> $skills */
                $documents = $skills->map(function (Skill $skill): array {
                    return [
                        'id' => $skill->id,
                        'external_id' => $skill->external_id,
                        'canonical_name' => $skill->canonical_name,
                        'slug' => $skill->slug,
                        'description' => $skill->description,
                        'category' => $skill->category,
                        'status' => $skill->status,
                        'aliases' => $skill->aliases()->pluck('surface_form')->all(),
                    ];
                })->all();

                if ($documents !== []) {
                    $client->collections[$collectionName]->documents->import($documents, ['action' => 'upsert']);
                }
            });
    }

    /**
     * Search Typesense for skills.
     *
     * @return array{
     *     results: list<array{
     *         skill_id: string,
     *         score: float,
     *         highlights: array<string, mixed>
     *     }>,
     *     total: int,
     *     search_time_ms: int
     * }
     */
    public function search(string $query, array $filters = [], ?int $limit = null): array
    {
        $client = $this->client();
        $collectionName = (string) config('typesense.collections.skills', 'skills');
        $limit ??= (int) config('skills_graph.pagination_default_limit', 20);

        $filterParts = [];

        if (isset($filters['category']) && $filters['category'] !== null) {
            $filterParts[] = 'category:='.$filters['category'];
        }

        if (isset($filters['status']) && $filters['status'] !== null) {
            $filterParts[] = 'status:='.$filters['status'];
        }

        $searchParameters = [
            'q' => $query,
            'query_by' => 'canonical_name,aliases,description',
            'filter_by' => implode(' && ', $filterParts),
            'per_page' => $limit,
        ];

        $response = $client->collections[$collectionName]->documents->search($searchParameters);

        $hits = $response['hits'] ?? [];

        $results = [];

        foreach ($hits as $hit) {
            $doc = $hit['document'] ?? [];

            $results[] = [
                'skill_id' => $doc['id'] ?? '',
                'score' => (float) ($hit['text_match'] ?? 0),
                'highlights' => $hit['highlights'] ?? [],
            ];
        }

        return [
            'results' => $results,
            'total' => (int) ($response['found'] ?? 0),
            'search_time_ms' => (int) ($response['search_time_ms'] ?? 0),
        ];
    }

    private function client(): Client
    {
        if ($this->client !== null) {
            return $this->client;
        }

        $apiKey = (string) config('typesense.api_key');

        if ($apiKey === '') {
            throw new \RuntimeException('Typesense API key is not configured (TYPESENSE_API_KEY).');
        }

        $this->client = new Client([
            'api_key' => $apiKey,
            'nodes' => [
                [
                    'host' => (string) config('typesense.host', 'localhost'),
                    'port' => (int) config('typesense.port', 8108),
                    'protocol' => (string) config('typesense.protocol', 'http'),
                ],
            ],
            'connection_timeout_seconds' => 2,
        ]);

        return $this->client;
    }
}
