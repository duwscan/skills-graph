<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Symfony\Component\HttpFoundation\Response;

class RequestId
{
    /**
     * Handle an incoming HTTP request and ensure it has a request ID.
     */
    public function handle(Request $request, Closure $next): Response
    {
        $requestId = $request->headers->get(key: 'X-Request-Id')
            ?? $request->attributes->get('request_id');

        if (! is_string($requestId) || $requestId === '') {
            $requestId = Str::uuid()->toString();
        }

        $request->attributes->set('request_id', $requestId);

        /** @var Response $response */
        $response = $next($request);
        $response->headers->set('X-Request-Id', $requestId);

        return $response;
    }
}
