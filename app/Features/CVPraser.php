<?php

namespace App\Features;

use App\Ai\Agents\CvParser;
use App\Models\Candidate;
use App\Models\Cv;
use Illuminate\Http\UploadedFile;
use Laravel\Ai\Files;

class CVPraser
{
    /**
     * Create a new class instance.
     */
    public function __construct(
        protected CvParser $agent,
    ) {}

    /**
     * Handle parsing a CV file into a Cv model.
     *
     * @param  Candidate|int|null  $candidate  Optional candidate to associate the parsed CV with.
     */
    public function handle(UploadedFile|string $file, Candidate|int|null $candidate = null): Cv
    {
        $attachments = $file instanceof UploadedFile
            ? [$file]
            : [Files\Document::fromPath((string) $file)];

        $response = $this->agent->prompt(
            'Parse the attached CV file and return raw text blocks for each section.',
            attachments: $attachments,
        );

        $attributes = [
            'basic_info' => (string) ($response['basic_info'] ?? ''),
            'experiences' => (string) ($response['experiences'] ?? ''),
            'educations' => (string) ($response['educations'] ?? ''),
            'certifications' => (string) ($response['certifications'] ?? ''),
            'projects' => (string) ($response['projects'] ?? ''),
            'awards' => (string) ($response['awards'] ?? ''),
            'skills' => (string) ($response['skills'] ?? ''),
            'metatdata_blocks' => $response['metatdata_blocks'] ?? [],
        ];

        if ($candidate !== null) {
            $attributes['candidate_id'] = $candidate instanceof Candidate ? $candidate->id : $candidate;
        }

        return Cv::create($attributes);
    }
}
