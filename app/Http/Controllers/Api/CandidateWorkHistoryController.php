<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\StoreWorkHistoryRequest;
use App\Http\Requests\UpdateWorkHistoryRequest;
use App\Http\Resources\WorkHistoryResource;
use App\Http\Responses\ApiResponse;
use App\Models\Candidate;
use App\Models\WorkHistory;
use Illuminate\Http\JsonResponse;

class CandidateWorkHistoryController extends Controller
{
    use ApiResponse;

    /**
     * Display a listing of the resource.
     */
    public function index(Candidate $candidate): JsonResponse
    {
        $workHistories = $candidate->workHistories()->latest('start_date')->get();

        return $this->successResponse(WorkHistoryResource::collection($workHistories));
    }

    /**
     * Store a newly created resource in storage.
     */
    public function store(StoreWorkHistoryRequest $request, Candidate $candidate): JsonResponse
    {
        $workHistory = $candidate->workHistories()->create($request->validated());

        return $this->successResponse(
            new WorkHistoryResource($workHistory),
            'Work history created successfully',
            201
        );
    }

    /**
     * Display the specified resource.
     */
    public function show(Candidate $candidate, WorkHistory $workHistory): JsonResponse
    {
        return $this->successResponse(new WorkHistoryResource($workHistory));
    }

    /**
     * Update the specified resource in storage.
     */
    public function update(UpdateWorkHistoryRequest $request, Candidate $candidate, WorkHistory $workHistory): JsonResponse
    {
        $workHistory->update($request->validated());

        return $this->successResponse(new WorkHistoryResource($workHistory->fresh()));
    }

    /**
     * Remove the specified resource from storage.
     */
    public function destroy(Candidate $candidate, WorkHistory $workHistory): JsonResponse
    {
        $workHistory->delete();

        return $this->successResponse(null, 'Work history deleted successfully');
    }
}
