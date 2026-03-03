<?php

use App\Http\Controllers\ParseCvController;
use Illuminate\Support\Facades\Route;

Route::post('/cv/parse', ParseCvController::class)
    ->name('api.cv.parse');
