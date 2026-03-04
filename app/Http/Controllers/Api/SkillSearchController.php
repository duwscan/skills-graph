<?php

namespace App\Http\Controllers\Api;

use App\Ai\HybridSearchService;
use App\Http\Controllers\Controller;
use App\Http\Requests\SkillSearchRequest;
use App\Http\Responses\ApiResponse;
use Illuminate\Http\JsonResponse;

class SkillSearchController extends Controller
{
    use ApiResponse;

    public function __construct(private readonly HybridSearchService $hybridSearchService) {}

    public function __invoke(SkillSearchRequest $request): JsonResponse
    {
        $data = $request->searchParameters();

        $result = $this->hybridSearchService->search(
            $data['q'],
            $data['category'],
            $data['status'],
            $data['limit'],
        );

        return $this->successResponse($result);
    }
}
