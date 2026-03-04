<?php

namespace App\Http\Controllers;

use App\Enums\RoleAlias;
use App\Http\Requests\LoginRequest;
use App\Http\Requests\RegisterRequest;
use App\Http\Responses\ApiResponse;
use App\Models\User;
use Illuminate\Http\JsonResponse;

class AuthController extends Controller
{
    use ApiResponse;

    /**
     * Handle the incoming login request.
     */
    public function login(LoginRequest $request): JsonResponse
    {
        $credentials = $request->only(['email', 'password']);

        if (! $token = auth('api')->attempt($credentials)) {
            return $this->errorResponse('Invalid credentials.', 401);
        }

        return $this->respondWithToken($token);
    }

    /**
     * Handle the incoming registration request.
     */
    public function register(RegisterRequest $request): JsonResponse
    {
        $user = User::query()->create([
            'name' => $request->validated('name'),
            'email' => $request->validated('email'),
            'password' => $request->validated('password'),
        ]);

        $user->assignRole(config('permission.default_role', RoleAlias::User->value));

        $token = auth('api')->login($user);

        return $this->respondWithToken($token);
    }

    /**
     * Get the authenticated user.
     */
    public function me(): JsonResponse
    {
        $user = auth('api')->user();

        return $this->successResponse($user, 'User retrieved successfully.');
    }

    /**
     * Log the user out (invalidate the token).
     */
    public function logout(): JsonResponse
    {
        auth('api')->logout();

        return $this->successResponse(null, 'Successfully logged out.');
    }

    /**
     * Refresh a token.
     */
    public function refresh(): JsonResponse
    {
        $token = auth('api')->refresh();

        return $this->respondWithToken($token);
    }

    /**
     * Get the token array structure.
     */
    protected function respondWithToken(string $token): JsonResponse
    {
        $ttl = (int) config('jwt.ttl', 60);

        return $this->successResponse([
            'access_token' => $token,
            'token_type' => 'bearer',
            'expires_in' => $ttl * 60,
        ], 'Authentication successful.');
    }
}
