<?php

namespace Tests\Feature;

use App\Models\Candidate;
use App\Models\JobExpectation;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class CandidateJobExpectationApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_show_returns_job_expectation(): void
    {
        $candidate = Candidate::factory()->create();
        $jobExpectation = JobExpectation::factory()->for($candidate)->create([
            'salary_min' => 30_000_000,
            'salary_max' => 50_000_000,
            'work_type' => 'remote',
        ]);

        $response = $this->getJson(
            "/api/candidates/{$candidate->id}/job-expectation",
            $this->authHeaders()
        );

        $response->assertOk()
            ->assertJsonPath('data.id', $jobExpectation->id)
            ->assertJsonPath('data.salary_min', 30000000.0)
            ->assertJsonPath('data.work_type', 'remote');
    }

    public function test_show_returns_404_when_no_job_expectation(): void
    {
        $candidate = Candidate::factory()->create();

        $response = $this->getJson(
            "/api/candidates/{$candidate->id}/job-expectation",
            $this->authHeaders()
        );

        $response->assertNotFound();
    }

    public function test_store_creates_job_expectation(): void
    {
        $candidate = Candidate::factory()->create();

        $payload = [
            'salary_min' => 25_000_000,
            'salary_max' => 40_000_000,
            'salary_currency' => 'VND',
            'work_type' => 'hybrid',
            'preferred_location' => 'Ho Chi Minh City',
        ];

        $response = $this->postJson(
            "/api/candidates/{$candidate->id}/job-expectation",
            $payload,
            $this->authHeaders()
        );

        $response->assertCreated()
            ->assertJsonPath('data.salary_min', 25000000.0)
            ->assertJsonPath('data.work_type', 'hybrid');

        $this->assertDatabaseHas('job_expectations', [
            'candidate_id' => $candidate->id,
            'work_type' => 'hybrid',
        ]);
    }

    public function test_store_returns_409_when_job_expectation_already_exists(): void
    {
        $candidate = Candidate::factory()->create();
        JobExpectation::factory()->for($candidate)->create();

        $response = $this->postJson(
            "/api/candidates/{$candidate->id}/job-expectation",
            [
                'salary_min' => 20_000_000,
                'work_type' => 'remote',
            ],
            $this->authHeaders()
        );

        $response->assertStatus(409)
            ->assertJsonPath('success', false);
    }

    public function test_update_modifies_existing_job_expectation(): void
    {
        $candidate = Candidate::factory()->create();
        JobExpectation::factory()->for($candidate)->create([
            'salary_min' => 20_000_000,
            'work_type' => 'on_site',
        ]);

        $response = $this->putJson(
            "/api/candidates/{$candidate->id}/job-expectation",
            [
                'salary_min' => 35_000_000,
                'work_type' => 'remote',
            ],
            $this->authHeaders()
        );

        $response->assertOk()
            ->assertJsonPath('data.salary_min', 35000000.0)
            ->assertJsonPath('data.work_type', 'remote');
    }

    public function test_update_creates_job_expectation_when_none_exists(): void
    {
        $candidate = Candidate::factory()->create();

        $response = $this->putJson(
            "/api/candidates/{$candidate->id}/job-expectation",
            [
                'salary_min' => 30_000_000,
                'work_type' => 'remote',
            ],
            $this->authHeaders()
        );

        $response->assertOk()
            ->assertJsonPath('data.salary_min', 30000000.0);

        $this->assertDatabaseHas('job_expectations', [
            'candidate_id' => $candidate->id,
        ]);
    }

    public function test_destroy_deletes_job_expectation(): void
    {
        $candidate = Candidate::factory()->create();
        JobExpectation::factory()->for($candidate)->create();

        $response = $this->deleteJson(
            "/api/candidates/{$candidate->id}/job-expectation",
            [],
            $this->authHeaders()
        );

        $response->assertOk();

        $this->assertDatabaseMissing('job_expectations', [
            'candidate_id' => $candidate->id,
        ]);
    }

    public function test_destroy_returns_404_when_no_job_expectation(): void
    {
        $candidate = Candidate::factory()->create();

        $response = $this->deleteJson(
            "/api/candidates/{$candidate->id}/job-expectation",
            [],
            $this->authHeaders()
        );

        $response->assertNotFound();
    }
}
