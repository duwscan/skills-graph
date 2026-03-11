<?php

namespace App\Console\Commands;

use App\Ai\TypesenseSearchService;
use Illuminate\Console\Command;

class TypesenseReindex extends Command
{
    /**
     * @var string
     */
    protected $signature = 'search:reindex';

    /**
     * @var string
     */
    protected $description = 'Drop the current Typesense skills collection and rebuild it from all active skills.';

    public function __construct(private readonly TypesenseSearchService $typesense)
    {
        parent::__construct();
    }

    public function handle(): int
    {
        $this->components->info('Dropping the current Typesense skills collection and rebuilding it from all active skills...');

        $this->typesense->reindexAll();

        $this->components->info('Typesense reindex completed with a fresh collection rebuild.');

        return self::SUCCESS;
    }
}
