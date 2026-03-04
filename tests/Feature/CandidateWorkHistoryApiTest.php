<?php

namespace Tests\Feature;

use App\Models\Candidate;
use App\Models\WorkHistory;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class CandidateWorkHistoryApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_index_returns_work_histories_for_candidate(): void
    {
        $candidate = Candidate::factory()->create();
        WorkHistory::factory()->count(2)->create([
            'candidate_id' => $candidate->id,
            'cv_id' => null,
        ]);

        $response = $this->getJson(
            "/api/candidates/{$candidate->id}/work-histories",
            $this->authHeaders()
        );

        $response->assertOk()
            ->assertJsonPath('success', true)
            ->assertJsonCount(2, 'data');
    }

    public function test_store_creates_work_history(): void
    {
        $candidate = Candidate::factory()->create();

        $payload = [
            'company_name' => 'Acme Corp',
            'position' => 'Senior Developer',
            'start_date' => '2020-01-01',
            'end_date' => '2023-12-31',
            'responsibilities' => 'Led development team',
        ];

        $response = $this->postJson(
            "/api/candidates/{$candidate->id}/work-histories",
            $payload,
            $this->authHeaders()
        );

        $response->assertCreated()
            ->assertJsonPath('data.company_name', 'Acme Corp')
            ->assertJsonPath('data.position', 'Senior Developer')
            ->assertJsonPath('data.candidate_id', $candidate->id);

        $this->assertDatabaseHas('work_histories', [
            'candidate_id' => $candidate->id,
            'company_name' => 'Acme Corp',
        ]);
    }

    public function test_store_validates_required_fields(): void
    {
        $candidate = Candidate::factory()->create();

        $response = $this->postJson(
            "/api/candidates/{$candidate->id}/work-histories",
            [],
            $this->authHeaders()
        );

        $response->assertUnprocessable()
            ->assertJsonValidationErrors(['company_name', 'position']);
    }

    public function test_show_returns_work_history(): void
    {
        $candidate = Candidate::factory()->create();
        $workHistory = WorkHistory::factory()->create([
            'candidate_id' => $candidate->id,
            'cv_id' => null,
            'company_name' => 'Test Company',
        ]);

        $response = $this->getJson(
            "/api/candidates/{$candidate->id}/work-histories/{$workHistory->id}",
            $this->authHeaders()
        );

        $response->assertOk()
            ->assertJsonPath('data.id', $workHistory->id)
            ->assertJsonPath('data.company_name', 'Test Company');
    }

    public function test_show_returns_404_when_work_history_belongs_to_different_candidate(): void
    {
        $candidate1 = Candidate::factory()->create();
        $candidate2 = Candidate::factory()->create();
        $workHistory = WorkHistory::factory()->create([
            'candidate_id' => $candidate2->id,
            'cv_id' => null,
        ]);

        $response = $this->getJson(
            "/api/candidates/{$candidate1->id}/work-histories/{$workHistory->id}",
            $this->authHeaders()
        );

        $response->assertNotFound();
    }

    public function test_update_modifies_work_history(): void
    {
        $candidate = Candidate::factory()->create();
        $workHistory = WorkHistory::factory()->create([
            'candidate_id' => $candidate->id,
            'cv_id' => null,
            'company_name' => 'Old Company',
        ]);

        $response = $this->putJson(
            "/api/candidates/{$candidate->id}/work-histories/{$workHistory->id}",
            [
                'company_name' => 'New Company',
                'position' => $workHistory->position,
            ],
            $this->authHeaders()
        );

        $response->assertOk()
            ->assertJsonPath('data.company_name', 'New Company');

        $workHistory->refresh();
        $this->assertSame('New Company', $workHistory->company_name);
    }

    public function test_destroy_deletes_work_history(): void
    {
        $candidate = Candidate::factory()->create();
        $workHistory = WorkHistory::factory()->create([
            'candidate_id' => $candidate->id,
            'cv_id' => null,
        ]);

        $response = $this->deleteJson(
            "/api/candidates/{$candidate->id}/work-histories/{$workHistory->id}",
            [],
            $this->authHeaders()
        );

        $response->assertOk();

        $this->assertDatabaseMissing('work_histories', ['id' => $workHistory->id]);
    }
}
