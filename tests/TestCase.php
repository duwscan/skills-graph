<?php

namespace Tests;

use App\Models\User;
use Illuminate\Foundation\Testing\TestCase as BaseTestCase;

abstract class TestCase extends BaseTestCase
{
    /**
     * Get JWT auth headers for the given user.
     *
     * @return array<string, string>
     */
    protected function authHeaders(?User $user = null): array
    {
        $user ??= User::factory()->create();
        $token = auth('api')->login($user);

        return [
            'Authorization' => 'Bearer '.$token,
        ];
    }
}
