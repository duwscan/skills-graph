<?php

namespace Tests\Unit\Ai;

use App\Ai\VectorSearchService;
use App\Models\Skill;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Laravel\Ai\Embeddings;
use Laravel\Ai\Prompts\EmbeddingsPrompt;
use Tests\TestCase;

class VectorSearchServiceTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();

        config()->set('skills_graph.rag_candidate_limit', 5);
        config()->set('skills_graph.similarity_duplicate_threshold', 0.9);
        config()->set('skills_graph.similarity_review_threshold', 0.7);
    }

    public function test_cosine_similarity_returns_expected_values(): void
    {
        $service = $this->makeService();

        $this->assertSame(1.0, $service->cosineSimilarity([1.0, 0.0], [1.0, 0.0]));
        $this->assertSame(0.0, $service->cosineSimilarity([1.0, 0.0], [0.0, 1.0]));
    }

    public function test_find_candidates_for_chunk_returns_skills_ordered_by_similarity(): void
    {
        if ($this->usingSqlite()) {
            $this->markTestSkipped('Vector operations are only available on PostgreSQL.');
        }

        $baseVector = $this->unitVector(0);
        $otherVector = $this->unitVector(1);

        $first = Skill::factory()->create([
            'embedding' => $baseVector,
            'status' => 'active',
        ]);

        $second = Skill::factory()->create([
            'embedding' => $otherVector,
            'status' => 'active',
        ]);

        $service = $this->makeService();

        $candidates = $service->findCandidatesForChunk($baseVector, 2);

        $this->assertCount(2, $candidates);
        $this->assertSame($first->id, $candidates[0]['id']);
        $this->assertSame($second->id, $candidates[1]['id']);
    }

    public function test_check_duplicate_uses_similarity_thresholds(): void
    {
        $baseVector = $this->unitVector(0);

        Skill::factory()->create([
            'canonical_name' => 'Machine Learning',
            'embedding' => $baseVector,
            'status' => 'active',
        ]);

        Embeddings::fake(function (EmbeddingsPrompt $prompt) use ($baseVector): array {
            return [$baseVector];
        });

        /** @var VectorSearchService $service */
        $service = $this->app->make(VectorSearchService::class);

        $result = $service->checkDuplicate('Machine Learning');

        $this->assertTrue($result['isDuplicate']);
        $this->assertNotEmpty($result['matches']);
    }

    private function makeService(): VectorSearchService
    {
        /** @var VectorSearchService $service */
        $service = $this->app->make(VectorSearchService::class);

        return $service;
    }

    /**
     * Build a sparse unit vector in the configured embedding space.
     *
     * @return list<float>
     */
    private function unitVector(int $index): array
    {
        $dimensions = (int) config('ai.embedding_dimensions', 1024);

        $vector = array_fill(0, $dimensions, 0.0);
        $vector[$index] = 1.0;

        return $vector;
    }

    private function usingSqlite(): bool
    {
        return DB::connection()->getDriverName() === 'sqlite';
    }
}
