<?php

namespace App\Console\Commands;

use App\Ai\TypesenseSearchService;
use Illuminate\Console\Command;

class TypesenseSetup extends Command
{
    /**
     * @var string
     */
    protected $signature = 'search:setup';

    /**
     * @var string
     */
    protected $description = 'Set up the Typesense skills collection.';

    public function __construct(private readonly TypesenseSearchService $typesense)
    {
        parent::__construct();
    }

    public function handle(): int
    {
        $this->components->info('Setting up Typesense skills collection...');

        $this->typesense->setupCollection();

        $this->components->info('Typesense skills collection ready.');

        return self::SUCCESS;
    }
}
