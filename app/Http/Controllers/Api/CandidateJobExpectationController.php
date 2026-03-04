<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\StoreJobExpectationRequest;
use App\Http\Requests\UpdateJobExpectationRequest;
use App\Http\Resources\JobExpectationResource;
use App\Http\Responses\ApiResponse;
use App\Models\Candidate;
use Illuminate\Http\JsonResponse;
use Symfony\Component\HttpFoundation\Response;

class CandidateJobExpectationController extends Controller
{
    use ApiResponse;

    /**
     * Display the specified resource.
     */
    public function show(Candidate $candidate): JsonResponse
    {
        $jobExpectation = $candidate->jobExpectation;

        if ($jobExpectation === null) {
            abort(404);
        }

        return $this->successResponse(new JobExpectationResource($jobExpectation));
    }

    /**
     * Store a newly created resource in storage.
     */
    public function store(StoreJobExpectationRequest $request, Candidate $candidate): JsonResponse
    {
        if ($candidate->jobExpectation !== null) {
            return $this->errorResponse(
                'Job expectation already exists for this candidate',
                Response::HTTP_CONFLICT
            );
        }

        $jobExpectation = $candidate->jobExpectation()->create($request->validated());

        return $this->successResponse(
            new JobExpectationResource($jobExpectation),
            'Job expectation created successfully',
            201
        );
    }

    /**
     * Update the specified resource in storage.
     */
    public function update(UpdateJobExpectationRequest $request, Candidate $candidate): JsonResponse
    {
        $jobExpectation = $candidate->jobExpectation;

        if ($jobExpectation === null) {
            $jobExpectation = $candidate->jobExpectation()->create($request->validated());
        } else {
            $jobExpectation->update($request->validated());
        }

        return $this->successResponse(new JobExpectationResource($jobExpectation->fresh()));
    }

    /**
     * Remove the specified resource from storage.
     */
    public function destroy(Candidate $candidate): JsonResponse
    {
        $jobExpectation = $candidate->jobExpectation;

        if ($jobExpectation === null) {
            abort(404);
        }

        $jobExpectation->delete();

        return $this->successResponse(null, 'Job expectation deleted successfully');
    }
}
