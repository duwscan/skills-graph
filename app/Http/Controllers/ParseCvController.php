<?php

namespace App\Http\Controllers;

use App\Features\CVPraser;
use App\Features\ExtractCandidateFromCv;
use App\Features\ExtractWorkHistoryFromCv;
use App\Http\Requests\ParseCvRequest;
use App\Http\Responses\ApiResponse;
use Illuminate\Support\Facades\DB;

class ParseCvController extends Controller
{
    use ApiResponse;

    /**
     * Handle the incoming request.
     */
    public function __invoke(
        ParseCvRequest $request,
        CVPraser $cvPraser,
        ExtractCandidateFromCv $extractCandidateFromCv,
        ExtractWorkHistoryFromCv $extractWorkHistoryFromCv,
    ) {
        $file = $request->file('file');

        $result = DB::transaction(function () use ($file, $cvPraser, $extractCandidateFromCv, $extractWorkHistoryFromCv): array {
            $cv = $cvPraser->handle(file: $file);
            $candidate = $extractCandidateFromCv->handle($cv);

            // Attach the created / updated candidate back to the CV
            $cv->candidate_id = $candidate->id;
            $cv->save();

            $workHistories = $extractWorkHistoryFromCv->handle($cv);

            return [
                'cv' => $cv,
                'candidate' => $candidate,
                'work_histories' => $workHistories,
            ];
        });

        return $this->successResponse($result, 'CV parsed successfully');
    }
}
