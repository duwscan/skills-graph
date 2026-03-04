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
    protected $description = 'Reindex all active skills into Typesense.';

    public function __construct(private readonly TypesenseSearchService $typesense)
    {
        parent::__construct();
    }

    public function handle(): int
    {
        $this->components->info('Reindexing all active skills into Typesense...');

        $this->typesense->reindexAll();

        $this->components->info('Typesense reindex completed.');

        return self::SUCCESS;
    }
}
