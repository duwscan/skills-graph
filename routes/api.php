<?php

use App\Enums\PermissionAlias;
use App\Http\Controllers\Api\CandidateController;
use App\Http\Controllers\Api\CandidateJobExpectationController;
use App\Http\Controllers\Api\CandidateWorkHistoryController;
use App\Http\Controllers\AuthController;
use App\Http\Controllers\ParseCvController;
use Illuminate\Support\Facades\Route;

Route::prefix('auth')->group(function (): void {
    Route::post('register', [AuthController::class, 'register'])->name('api.auth.register');
    Route::post('login', [AuthController::class, 'login'])->name('api.auth.login');

    Route::middleware('auth:api')->group(function (): void {
        Route::post('logout', [AuthController::class, 'logout'])->name('api.auth.logout');
        Route::get('me', [AuthController::class, 'me'])->name('api.auth.me');
    });

    Route::post('refresh', [AuthController::class, 'refresh'])
        ->middleware('jwt.refresh')
        ->name('api.auth.refresh');
});

Route::middleware('auth:api')->group(function (): void {
    Route::post('/cv/parse', ParseCvController::class)->name('api.cv.parse');

    Route::apiResource('candidates', CandidateController::class)
        ->middleware(permission_middleware(PermissionAlias::ManageCandidates));

    Route::apiResource('candidates.work-histories', CandidateWorkHistoryController::class)
        ->scoped()
        ->middleware(permission_middleware(PermissionAlias::ManageCandidates));

    Route::apiSingleton('candidates.job-expectation', CandidateJobExpectationController::class)
        ->creatable()
        ->middleware(permission_middleware(PermissionAlias::ManageCandidates));
});
