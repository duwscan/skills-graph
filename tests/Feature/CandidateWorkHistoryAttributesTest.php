<?php

namespace Tests\Feature;

use App\Models\Candidate;
use App\Models\Cv;
use App\Models\WorkHistory;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class CandidateWorkHistoryAttributesTest extends TestCase
{
    use RefreshDatabase;

    public function test_candidate_computed_work_history_attributes(): void
    {
        $candidate = Candidate::factory()->create();

        $cv = Cv::create([
            'candidate_id' => $candidate->id,
            'basic_info' => '',
            'experiences' => '',
            'educations' => '',
            'certifications' => '',
            'projects' => '',
            'awards' => '',
            'skills' => '',
            'metatdata_blocks' => [],
        ]);

        WorkHistory::create([
            'candidate_id' => $candidate->id,
            'cv_id' => $cv->id,
            'company_name' => 'Old Corp',
            'position' => 'Junior Dev',
            'start_date' => '2018-01-01',
            'end_date' => '2019-01-01',
            'responsibilities' => 'Did some work.',
        ]);

        WorkHistory::create([
            'candidate_id' => $candidate->id,
            'cv_id' => $cv->id,
            'company_name' => 'Middle Corp',
            'position' => 'Mid Dev',
            'start_date' => '2019-02-01',
            'end_date' => '2020-06-01',
            'responsibilities' => 'Did more work.',
        ]);

        WorkHistory::create([
            'candidate_id' => $candidate->id,
            'cv_id' => $cv->id,
            'company_name' => 'New Corp',
            'position' => 'Senior Dev',
            'start_date' => '2021-03-01',
            'end_date' => null,
            'responsibilities' => 'Leading projects.',
        ]);

        $candidate->load('workHistories');

        $this->assertSame('New Corp', $candidate->current_or_last_company);
        $this->assertSame('2018-01-01', $candidate->first_work_start_date);
        $this->assertSame('2020-06-01', $candidate->last_work_end_date);

        $asArray = $candidate->toArray();

        $this->assertArrayHasKey('current_or_last_company', $asArray);
        $this->assertArrayHasKey('first_work_start_date', $asArray);
        $this->assertArrayHasKey('last_work_end_date', $asArray);
    }
}
