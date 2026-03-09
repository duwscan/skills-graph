<?php

namespace App\Console\Commands;

use Illuminate\Console\Command;

class SeedSkillsFromTaxonomy extends Command
{
    /**
     * @var string
     */
    protected $signature = 'skills:seed
        {--domain=* : Specific domains to generate (it, finance, fnb)}
        {--provider= : AI provider override}
        {--dry-run : Generate JSON files but skip DB import}
        {--resume : Resume from last completed batch}';

    /**
     * @var string
     */
    protected $description = 'Run the LLM taxonomy generator pipeline to seed skills into the graph.';

    public function handle(): int
    {
        $dryRun = (bool) $this->option('dry-run');

        $arguments = [
            '--phase' => 'all',
        ];

        $domains = (array) $this->option('domain');
        if ($domains !== []) {
            $arguments['--domain'] = $domains;
        }

        $provider = $this->option('provider');
        if ($provider !== null && $provider !== '') {
            $arguments['--provider'] = (string) $provider;
        }

        if ((bool) $this->option('resume')) {
            $arguments['--resume'] = true;
        }

        if ($dryRun) {
            $arguments['--dry-run'] = true;
        }

        $this->components->info('Seeding skills using the LLM taxonomy pipeline...');

        return $this->call('skills:generate-taxonomy', $arguments);
    }
}
