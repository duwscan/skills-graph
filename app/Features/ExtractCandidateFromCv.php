<?php

namespace App\Features;

use App\Ai\Agents\CandidateExtractor;
use App\Models\Candidate;
use App\Models\Cv;

class ExtractCandidateFromCv
{
    /**
     * Create a new class instance.
     */
    public function __construct(
        protected CandidateExtractor $extractor,
    ) {}

    /**
     * Extract or update a candidate from the CV's basic_info section.
     *
     * @param  Candidate|int|null  $candidate  Existing candidate or ID to update. If null, a new candidate is created.
     */
    public function handle(Cv $cv, Candidate|int|null $candidate = null): Candidate
    {
        $basicInfo = (string) $cv->basic_info;

        if ($candidate !== null) {
            $model = $candidate instanceof Candidate ? $candidate : Candidate::findOrFail($candidate);

            return $this->updateCandidateFromBasicInfo($model, $basicInfo);
        }

        return $this->createCandidateFromBasicInfo($basicInfo);
    }

    protected function createCandidateFromBasicInfo(string $basicInfo): Candidate
    {
        $extracted = $this->extractor->prompt(
            "Extract candidate personal information from this CV basic_info section:\n\n".$basicInfo,
        );

        return Candidate::create(attributes: [
            'first_name' => (string) ($extracted['first_name'] ?? ''),
            'last_name' => (string) ($extracted['last_name'] ?? ''),
            'email' => (string) ($extracted['email'] ?? ''),
            'phone' => (string) ($extracted['phone'] ?? ''),
            'summary' => (string) ($extracted['summary'] ?? ''),
        ]);
    }

    protected function updateCandidateFromBasicInfo(Candidate $candidate, string $basicInfo): Candidate
    {
        $extracted = $this->extractor->prompt(
            "Extract candidate personal information from this CV basic_info section:\n\n".$basicInfo,
        );

        $candidate->update([
            'first_name' => (string) ($extracted['first_name'] ?? $candidate->first_name),
            'last_name' => (string) ($extracted['last_name'] ?? $candidate->last_name),
            'email' => (string) ($extracted['email'] ?? $candidate->email),
            'phone' => (string) ($extracted['phone'] ?? $candidate->phone),
            'summary' => (string) ($extracted['summary'] ?? $candidate->summary),
        ]);

        return $candidate;
    }
}
