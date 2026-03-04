<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\StoreCandidateRequest;
use App\Http\Requests\UpdateCandidateRequest;
use App\Http\Resources\CandidateResource;
use App\Http\Responses\ApiResponse;
use App\Models\Candidate;
use Illuminate\Http\JsonResponse;

class CandidateController extends Controller
{
    use ApiResponse;

    /**
     * Display a listing of the resource.
     */
    public function index(): JsonResponse
    {
        $candidates = Candidate::query()
            ->latest()
            ->paginate(15)
            ->through(fn (Candidate $candidate) => new CandidateResource($candidate));

        return $this->paginatedResponse($candidates);
    }

    /**
     * Store a newly created resource in storage.
     */
    public function store(StoreCandidateRequest $request): JsonResponse
    {
        $candidate = Candidate::query()->create($request->validated());

        return $this->successResponse(
            new CandidateResource($candidate->refresh()),
            'Candidate created successfully',
            201
        );
    }

    /**
     * Display the specified resource.
     */
    public function show(Candidate $candidate): JsonResponse
    {
        return $this->successResponse(new CandidateResource($candidate));
    }

    /**
     * Update the specified resource in storage.
     */
    public function update(UpdateCandidateRequest $request, Candidate $candidate): JsonResponse
    {
        $candidate->update($request->validated());

        return $this->successResponse(
            new CandidateResource($candidate->fresh())
        );
    }

    /**
     * Remove the specified resource from storage.
     */
    public function destroy(Candidate $candidate): JsonResponse
    {
        $candidate->delete();

        return $this->successResponse(null, 'Candidate deleted successfully');
    }
}
