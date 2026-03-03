<?php

namespace App\Http\Controllers;

use App\Features\CVPraser;
use App\Features\ExtractCandidateFromCv;
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
    ) {
        $file = $request->file('file');

        $result = DB::transaction(function () use ($file, $cvPraser, $extractCandidateFromCv): array {
            $cv = $cvPraser->handle(file: $file);
            $candidate = $extractCandidateFromCv->handle($cv);

            return [
                'cv' => $cv,
                'candidate' => $candidate,
            ];
        });

        return $this->successResponse($result, 'CV parsed successfully');
    }
}
