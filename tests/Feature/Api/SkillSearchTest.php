<?php

namespace Tests\Feature\Api;

use App\Ai\HybridSearchService;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Mockery\MockInterface;
use Tests\TestCase;

class SkillSearchTest extends TestCase
{
    use RefreshDatabase;

    public function test_requires_query_parameter(): void
    {
        /** @var User $user */
        $user = User::factory()->create();

        $this->actingAs($user, 'api')
            ->getJson('/api/skills/search')
            ->assertStatus(422);
    }

    public function test_returns_hybrid_results_structure(): void
    {
        /** @var User $user */
        $user = User::factory()->create();

        $payload = [
            'results' => [],
            'total' => 0,
            'query_time_ms' => 5,
        ];

        $this->mock(HybridSearchService::class, function (MockInterface $mock) use ($payload): void {
            $mock->shouldReceive('search')
                ->once()
                ->andReturn($payload);
        });

        $response = $this->actingAs($user, 'api')
            ->getJson('/api/skills/search?q=test');

        $response
            ->assertOk()
            ->assertJson([
                'success' => true,
                'data' => $payload,
            ]);
    }
}
