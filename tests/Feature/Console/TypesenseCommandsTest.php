<?php

namespace Tests\Feature\Console;

use App\Ai\TypesenseSearchService;
use Mockery\MockInterface;
use Tests\TestCase;

class TypesenseCommandsTest extends TestCase
{
    public function test_search_setup_calls_typesense_service(): void
    {
        $this->mock(TypesenseSearchService::class, function (MockInterface $mock): void {
            $mock->shouldReceive('setupCollection')
                ->once();
        });

        $this->artisan('search:setup')
            ->assertSuccessful();
    }

    public function test_search_reindex_calls_typesense_service(): void
    {
        $this->mock(TypesenseSearchService::class, function (MockInterface $mock): void {
            $mock->shouldReceive('reindexAll')
                ->once();
        });

        $this->artisan('search:reindex')
            ->assertSuccessful();
    }
}
