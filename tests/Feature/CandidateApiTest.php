<?php

namespace Tests\Feature;

use App\Models\Candidate;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class CandidateApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_index_returns_paginated_candidates(): void
    {
        Candidate::factory()->count(3)->create();

        $response = $this->getJson('/api/candidates', $this->authHeaders());

        $response->assertOk()
            ->assertJsonStructure([
                'success',
                'message',
                'data',
                'meta' => [
                    'current_page',
                    'last_page',
                    'per_page',
                    'total',
                ],
            ])
            ->assertJsonPath('success', true)
            ->assertJsonCount(3, 'data');
    }

    public function test_store_creates_candidate(): void
    {
        $payload = [
            'first_name' => 'John',
            'last_name' => 'Doe',
            'email' => 'john.doe@example.com',
            'phone' => '+84123456789',
            'summary' => 'Experienced developer',
        ];

        $response = $this->postJson('/api/candidates', $payload, $this->authHeaders());

        $response->assertCreated()
            ->assertJsonPath('success', true)
            ->assertJsonPath('data.first_name', 'John')
            ->assertJsonPath('data.last_name', 'Doe')
            ->assertJsonPath('data.email', 'john.doe@example.com');

        $this->assertDatabaseHas('candidates', [
            'email' => 'john.doe@example.com',
        ]);
    }

    public function test_store_validates_required_fields(): void
    {
        $response = $this->postJson('/api/candidates', [], $this->authHeaders());

        $response->assertUnprocessable()
            ->assertJsonValidationErrors(['first_name', 'last_name', 'email']);
    }

    public function test_show_returns_candidate(): void
    {
        $candidate = Candidate::factory()->create([
            'first_name' => 'Jane',
            'last_name' => 'Smith',
            'email' => 'jane@example.com',
        ]);

        $response = $this->getJson("/api/candidates/{$candidate->id}", $this->authHeaders());

        $response->assertOk()
            ->assertJsonPath('data.id', $candidate->id)
            ->assertJsonPath('data.first_name', 'Jane')
            ->assertJsonPath('data.email', 'jane@example.com');
    }

    public function test_show_returns_404_for_nonexistent_candidate(): void
    {
        $response = $this->getJson('/api/candidates/99999', $this->authHeaders());

        $response->assertNotFound();
    }

    public function test_update_modifies_candidate(): void
    {
        $candidate = Candidate::factory()->create([
            'first_name' => 'Old',
            'last_name' => 'Name',
            'email' => 'old@example.com',
        ]);

        $response = $this->putJson("/api/candidates/{$candidate->id}", [
            'first_name' => 'New',
            'last_name' => 'Name',
            'email' => 'new@example.com',
        ], $this->authHeaders());

        $response->assertOk()
            ->assertJsonPath('data.first_name', 'New')
            ->assertJsonPath('data.email', 'new@example.com');

        $candidate->refresh();
        $this->assertSame('New', $candidate->first_name);
        $this->assertSame('new@example.com', $candidate->email);
    }

    public function test_destroy_deletes_candidate(): void
    {
        $candidate = Candidate::factory()->create();

        $response = $this->deleteJson("/api/candidates/{$candidate->id}", [], $this->authHeaders());

        $response->assertOk()
            ->assertJsonPath('success', true);

        $this->assertDatabaseMissing('candidates', ['id' => $candidate->id]);
    }

    public function test_unauthenticated_requests_are_rejected(): void
    {
        $response = $this->getJson('/api/candidates');

        $response->assertUnauthorized();
    }
}
