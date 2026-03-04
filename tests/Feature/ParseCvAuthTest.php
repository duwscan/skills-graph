<?php

namespace Tests\Feature;

use Database\Seeders\RolePermissionSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Tests\TestCase;

class ParseCvAuthTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->seed(RolePermissionSeeder::class);
    }

    public function test_parse_cv_returns_401_when_unauthenticated(): void
    {
        $file = UploadedFile::fake()->create('cv.pdf', 100, 'application/pdf');

        $response = $this->post('/api/cv/parse', [
            'file' => $file,
        ]);

        $response->assertUnauthorized();
    }

    public function test_parse_cv_accepts_authenticated_request(): void
    {
        $file = UploadedFile::fake()->create('cv.pdf', 100, 'application/pdf');

        $response = $this->post('/api/cv/parse', [
            'file' => $file,
        ], $this->authHeaders());

        $response->assertSuccessful();
    }
}
