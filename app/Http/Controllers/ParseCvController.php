<?php

namespace App\Http\Controllers;

use App\Features\CVPraser;
use App\Http\Requests\ParseCvRequest;
use App\Http\Responses\ApiResponse;

class ParseCvController extends Controller
{
    use ApiResponse;

    /**
     * Handle the incoming request.
     */
    public function __invoke(ParseCvRequest $request, CVPraser $cvPraser)
    {
        $file = $request->file('file');

        $cv = $cvPraser($file);

        return $this->successResponse($cv, 'CV parsed successfully');
    }
}
