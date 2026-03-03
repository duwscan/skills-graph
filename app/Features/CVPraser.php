<?php

namespace App\Features;

use App\Ai\Agents\CvParser;
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
     * Invoke the class instance.
     */
    public function __invoke(UploadedFile|string $file): Cv
    {
        $attachments = $file instanceof UploadedFile
            ? [$file]
            : [Files\Document::fromPath((string) $file)];

        $response = $this->agent->prompt(
            'Parse the attached CV file and return raw text blocks for each section.',
            attachments: $attachments,
        );

        return Cv::create([
            'basic_info' => (string) ($response['basic_info'] ?? ''),
            'experiences' => (string) ($response['experiences'] ?? ''),
            'educations' => (string) ($response['educations'] ?? ''),
            'certifications' => (string) ($response['certifications'] ?? ''),
            'projects' => (string) ($response['projects'] ?? ''),
            'awards' => (string) ($response['awards'] ?? ''),
            'skills' => (string) ($response['skills'] ?? ''),
            'metatdata_blocks' => $response['metatdata_blocks'] ?? [],
        ]);
    }
}
