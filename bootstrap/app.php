<?php

use App\Http\Middleware\RequestId;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;
use Spatie\Permission\Middleware\PermissionMiddleware;
use Spatie\Permission\Middleware\RoleMiddleware;
use Spatie\Permission\Middleware\RoleOrPermissionMiddleware;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        $middleware->append(RequestId::class);
        $middleware->validateCsrfTokens(except: ['api/*']);
        $middleware->alias([
            'role' => RoleMiddleware::class,
            'permission' => PermissionMiddleware::class,
            'role_or_permission' => RoleOrPermissionMiddleware::class,
        ]);
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        $exceptions->render(function (\Throwable $e, Request $request) {
            if (! $request->expectsJson() && ! $request->is('api/*')) {
                return null;
            }

            $requestId = $request->attributes->get('request_id')
                ?? $request->headers->get('X-Request-Id')
                ?? Str::uuid()->toString();

            if ($e instanceof ValidationException) {
                $response = response()->json([
                    'success' => false,
                    'message' => 'Validation failed.',
                    'errors' => (object) $e->errors(),
                    'meta' => (object) [
                        'request_id' => $requestId,
                    ],
                ], 422);

                return $response->header('X-Request-Id', $requestId);
            }

            $status = $e instanceof HttpExceptionInterface ? $e->getStatusCode() : 500;

            $response = response()->json([
                'success' => false,
                'message' => $e->getMessage() ?: 'Server Error',
                'errors' => (object) [],
                'meta' => (object) [
                    'request_id' => $requestId,
                ],
            ], $status);

            return $response->header('X-Request-Id', $requestId);
        });
    })->create();
