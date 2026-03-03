<?php

namespace Tests\Feature;

use App\Models\Candidate;
use App\Models\Cv;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class CandidateTest extends TestCase
{
    use RefreshDatabase;

    public function test_candidate_has_many_cvs(): void
    {
        $candidate = Candidate::factory()->create();

        $cv1 = Cv::create([
            'candidate_id' => $candidate->id,
            'basic_info' => 'Info 1',
            'experiences' => '',
            'educations' => '',
            'certifications' => '',
            'projects' => '',
            'awards' => '',
            'skills' => '',
            'metatdata_blocks' => [],
        ]);
        $cv2 = Cv::create([
            'candidate_id' => $candidate->id,
            'basic_info' => 'Info 2',
            'experiences' => '',
            'educations' => '',
            'certifications' => '',
            'projects' => '',
            'awards' => '',
            'skills' => '',
            'metatdata_blocks' => [],
        ]);

        $candidate->load('cvs');

        $this->assertCount(2, $candidate->cvs);
        $this->assertTrue($candidate->cvs->contains($cv1));
        $this->assertTrue($candidate->cvs->contains($cv2));
    }

    public function test_cv_belongs_to_candidate(): void
    {
        $candidate = Candidate::factory()->create([
            'first_name' => 'Jane',
            'last_name' => 'Doe',
            'email' => 'jane@example.com',
        ]);

        $cv = Cv::create([
            'candidate_id' => $candidate->id,
            'basic_info' => 'Bio',
            'experiences' => '',
            'educations' => '',
            'certifications' => '',
            'projects' => '',
            'awards' => '',
            'skills' => '',
            'metatdata_blocks' => [],
        ]);

        $this->assertInstanceOf(Candidate::class, $cv->candidate);
        $this->assertSame($candidate->id, $cv->candidate->id);
        $this->assertSame('Jane', $cv->candidate->first_name);
        $this->assertSame('Doe', $cv->candidate->last_name);
        $this->assertSame('jane@example.com', $cv->candidate->email);
    }
}
