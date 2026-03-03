<?php

namespace App\Features;

use App\Ai\Agents\WorkHistoryExtractor;
use App\Models\Cv;
use App\Models\WorkHistory;

class ExtractWorkHistoryFromCv
{
    /**
     * Create a new class instance.
     */
    public function __construct(
        protected WorkHistoryExtractor $extractor,
    ) {}

    /**
     * Extract and persist work history entries from the CV's experiences section.
     *
     * @return array<int, WorkHistory>
     */
    public function handle(Cv $cv): array
    {
        if (! $cv->candidate_id) {
            return [];
        }

        $experiences = (string) $cv->experiences;

        if ($experiences === '') {
            return [];
        }

        $response = $this->extractor->prompt(
            "Extract work history entries from this CV experiences section:\n\n".$experiences,
        );

        $items = $response['items'] ?? [];

        if (! is_iterable($items)) {
            return [];
        }

        $histories = [];

        foreach ($items as $item) {
            $start = (string) ($item['start_date'] ?? '');
            $end = (string) ($item['end_date'] ?? '');

            $histories[] = WorkHistory::create([
                'candidate_id' => $cv->candidate_id,
                'cv_id' => $cv->id,
                'company_name' => (string) ($item['company_name'] ?? ''),
                'position' => (string) ($item['position'] ?? ''),
                'start_date' => $start !== '' ? $start : null,
                'end_date' => $end !== '' ? $end : null,
                'responsibilities' => (string) ($item['responsibilities'] ?? ''),
            ]);
        }

        return $histories;
    }
}
