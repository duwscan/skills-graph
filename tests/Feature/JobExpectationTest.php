<?php

namespace Tests\Feature;

use App\Models\Candidate;
use App\Models\JobExpectation;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class JobExpectationTest extends TestCase
{
    use RefreshDatabase;

    public function test_candidate_has_one_job_expectation(): void
    {
        $candidate = Candidate::factory()->create();

        $expectation = JobExpectation::factory()->for($candidate)->create([
            'salary_min' => 30_000_000,
            'salary_max' => 50_000_000,
            'work_type' => 'remote',
        ]);

        $candidate->load('jobExpectation');

        $this->assertInstanceOf(JobExpectation::class, $candidate->jobExpectation);
        $this->assertSame($expectation->id, $candidate->jobExpectation->id);
        $this->assertSame(30_000_000.0, $candidate->jobExpectation->salary_min);
        $this->assertSame(50_000_000.0, $candidate->jobExpectation->salary_max);
        $this->assertSame('remote', $candidate->jobExpectation->work_type);
    }

    public function test_job_expectation_belongs_to_candidate(): void
    {
        $candidate = Candidate::factory()->create([
            'first_name' => 'John',
            'last_name' => 'Doe',
            'email' => 'john@example.com',
        ]);

        $expectation = JobExpectation::factory()->for($candidate)->create([
            'preferred_location' => 'Ho Chi Minh City',
        ]);

        $this->assertInstanceOf(Candidate::class, $expectation->candidate);
        $this->assertSame($candidate->id, $expectation->candidate->id);
        $this->assertSame('John', $expectation->candidate->first_name);
        $this->assertSame('Ho Chi Minh City', $expectation->preferred_location);
    }

    public function test_job_expectation_casts_benefits_priorities_as_array(): void
    {
        $candidate = Candidate::factory()->create();

        $expectation = JobExpectation::factory()->for($candidate)->create([
            'benefits_priorities' => ['health_insurance', 'remote_equipment'],
        ]);

        $this->assertIsArray($expectation->benefits_priorities);
        $this->assertSame(['health_insurance', 'remote_equipment'], $expectation->benefits_priorities);
    }
}
