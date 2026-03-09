<?php

namespace App\Console\Commands;

use App\Ai\TaxonomyService;
use Illuminate\Console\Command;

class GenerateSkillTaxonomy extends Command
{
    /**
     * @var string
     */
    protected $signature = 'skills:generate-taxonomy
        {--phase=all : Phase to run (taxonomy, skills, normalize, enrich, import, all)}
        {--domain=* : Specific domains to generate (it, finance, fnb)}
        {--provider= : AI provider override}
        {--dry-run : Generate files but skip DB import}
        {--resume : Resume from last completed batch}';

    /**
     * @var string
     */
    protected $description = 'Generate structured skill taxonomy data using LLM agents';

    public function handle(): int
    {
        set_time_limit(0);
        ini_set('max_execution_time', '0');
        ini_set('memory_limit', '-1');

        $phase = (string) $this->option('phase');
        $domainOptions = (array) $this->option('domain');
        $provider = $this->option('provider') ? (string) $this->option('provider') : null;
        $dryRun = (bool) $this->option('dry-run');
        $resume = (bool) $this->option('resume');

        $domains = $this->resolveDomains($domainOptions);

        $this->components->info('Starting taxonomy generation for: '.implode(', ', $domains));
        $this->components->info("Phase: {$phase} | Dry run: ".($dryRun ? 'yes' : 'no').' | Resume: '.($resume ? 'yes' : 'no'));

        $effectivePhase = $dryRun && $phase === 'all' ? 'enrich' : $phase;

        if ($dryRun && $phase === 'import') {
            $this->components->warn('Dry run mode: skipping import phase.');

            return self::SUCCESS;
        }

        $service = new TaxonomyService;

        $progressBar = null;

        $service->runPipeline(
            domains: $domains,
            phase: $effectivePhase,
            resume: $resume,
            provider: $provider,
            onProgress: function (string $event, array $data) use (&$progressBar): void {
                match ($event) {
                    'phase_start' => $this->handlePhaseStart($data),
                    'phase_complete' => $this->handlePhaseComplete($data, $progressBar),
                    'batch_complete' => $this->handleBatchComplete($data, $progressBar),
                    default => null,
                };
            },
        );

        if (! $dryRun && in_array($phase, ['all', 'import'], true)) {
            $service->runPipeline(
                domains: $domains,
                phase: 'import',
                provider: $provider,
                onProgress: function (string $event, array $data) use (&$progressBar): void {
                    match ($event) {
                        'phase_start' => $this->handlePhaseStart($data),
                        'phase_complete' => $this->handlePhaseComplete($data, $progressBar),
                        default => null,
                    };
                },
            );
        }

        $this->newLine();
        $this->components->info('Taxonomy generation complete.');

        return self::SUCCESS;
    }

    /**
     * @param  list<string>  $domainOptions
     * @return list<string>
     */
    private function resolveDomains(array $domainOptions): array
    {
        if ($domainOptions === []) {
            return ['IT', 'Finance', 'F&B'];
        }

        $mapping = [
            'it' => 'IT',
            'finance' => 'Finance',
            'fnb' => 'F&B',
            'f&b' => 'F&B',
        ];

        return array_values(array_map(
            fn (string $d): string => $mapping[strtolower($d)] ?? $d,
            $domainOptions,
        ));
    }

    /**
     * @param  array<string, mixed>  $data
     */
    private function handlePhaseStart(array $data): void
    {
        $phase = $data['phase'] ?? 'unknown';
        $labels = [
            'taxonomy' => 'Designing taxonomy map...',
            'skills' => 'Generating skill batches...',
            'normalize' => 'Normalizing and deduplicating...',
            'enrich' => 'Enriching skill metadata...',
            'import' => 'Importing to database...',
        ];

        $this->newLine();
        $this->components->info($labels[$phase] ?? "Running phase: {$phase}");
    }

    /**
     * @param  array<string, mixed>  $data
     */
    private function handlePhaseComplete(array $data, mixed &$progressBar): void
    {
        if ($progressBar !== null) {
            $progressBar->finish();
            $progressBar = null;
            $this->newLine();
        }

        $phase = $data['phase'] ?? 'unknown';

        match ($phase) {
            'taxonomy' => $this->components->info('Taxonomy map created with '.($data['domains'] ?? 0).' domains.'),
            'skills' => $this->components->info('Generated '.($data['total_skills'] ?? 0).' skills total.'),
            'normalize' => $this->components->info(
                'Normalization complete: '.($data['merged_count'] ?? 0).' skills retained, '
                .($data['removed_count'] ?? 0).' duplicates removed.'
            ),
            'enrich' => $this->components->info('Enriched '.($data['enriched_count'] ?? 0).' skills.'),
            'import' => $this->components->info('Imported '.($data['imported_count'] ?? 0).' skills to database.'),
            default => null,
        };
    }

    /**
     * @param  array<string, mixed>  $data
     */
    private function handleBatchComplete(array $data, mixed &$progressBar): void
    {
        $total = $data['total'] ?? 0;

        if ($progressBar === null && $total > 0) {
            $progressBar = $this->output->createProgressBar($total);
            $progressBar->start();
        }

        if ($progressBar !== null) {
            $progressBar->advance();
        }

        $domain = $data['domain'] ?? '';
        $category = $data['category'] ?? '';
        $subcategory = $data['subcategory'] ?? '';
        $count = $data['count'] ?? 0;

        $this->line("  [{$domain} > {$category} > {$subcategory}] {$count} skills", verbosity: 'v');
    }
}
