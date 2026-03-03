<?php

namespace App\Http\Responses;

use Illuminate\Contracts\Pagination\LengthAwarePaginator;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

trait ApiResponse
{
    /**
     * Build a standard successful JSON API response.
     */
    protected function successResponse(
        mixed $data = null,
        string $message = 'OK',
        int $status = 200,
        array $meta = [],
    ): JsonResponse {
        $requestId = $this->resolveRequestId();

        $response = response()->json([
            'success' => true,
            'message' => $message,
            'data' => $data,
            'meta' => (object) array_merge($meta, [
                'request_id' => $requestId,
            ]),
        ], $status);

        return $response->header('X-Request-Id', $requestId);
    }

    /**
     * Build a standard error JSON API response.
     */
    protected function errorResponse(
        string $message,
        int $status = 400,
        array $errors = [],
        array $meta = [],
    ): JsonResponse {
        $requestId = $this->resolveRequestId();

        $response = response()->json([
            'success' => false,
            'message' => $message,
            'errors' => (object) $errors,
            'meta' => (object) array_merge($meta, [
                'request_id' => $requestId,
            ]),
        ], $status);

        return $response->header('X-Request-Id', $requestId);
    }

    /**
     * Build a standardized paginated JSON API response.
     */
    protected function paginatedResponse(
        LengthAwarePaginator $paginator,
        string $message = 'OK',
        int $status = 200,
        array $meta = [],
    ): JsonResponse {
        $requestId = $this->resolveRequestId();

        $response = response()->json([
            'success' => true,
            'message' => $message,
            'data' => $paginator->items(),
            'meta' => (object) array_merge($meta, [
                'request_id' => $requestId,
                'current_page' => $paginator->currentPage(),
                'last_page' => $paginator->lastPage(),
                'per_page' => $paginator->perPage(),
                'total' => $paginator->total(),
            ]),
        ], $status);

        return $response->header('X-Request-Id', $requestId);
    }

    /**
     * Resolve or generate a request identifier for tracing.
     */
    protected function resolveRequestId(): string
    {
        /** @var Request|null $request */
        $request = request();

        $existingId = $request?->headers->get('X-Request-Id')
            ?? $request?->attributes->get('request_id');

        if (is_string($existingId) && $existingId !== '') {
            return $existingId;
        }

        return Str::uuid()->toString();
    }
}
