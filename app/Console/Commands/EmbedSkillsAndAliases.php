<?php

namespace App\Console\Commands;

use App\Ai\EmbeddingService;
use App\Models\Skill;
use App\Models\SkillAlias;
use Illuminate\Console\Command;
use Illuminate\Support\Collection;
use Throwable;

class EmbedSkillsAndAliases extends Command
{
    /**
     * @var string
     */
    protected $signature = 'skills:embed
        {--chunk=50 : Number of records to process per batch}
        {--force : Re-embed records that already have embeddings}';

    /**
     * @var string
     */
    protected $description = 'Generate embedding vectors for all Skills and SkillAliases that are missing them';

    public function __construct(private readonly EmbeddingService $embeddingService)
    {
        parent::__construct();
    }

    public function handle(): int
    {
        set_time_limit(0);
        ini_set('max_execution_time', '0');
        ini_set('memory_limit', '-1');

        $chunkSize = (int) $this->option('chunk');
        $force = (bool) $this->option('force');

        $this->embedSkills($chunkSize, $force);
        $this->embedSkillAliases($chunkSize, $force);

        $this->components->info('All embeddings generated successfully.');

        return self::SUCCESS;
    }

    private function embedSkills(int $chunkSize, bool $force): void
    {
        $query = Skill::query()->orderBy('id');

        if (! $force) {
            $query->whereNull('embedding');
        }

        $total = $query->count();

        if ($total === 0) {
            $this->components->info('No skills to embed.');

            return;
        }

        $this->components->info("Embedding {$total} skills…");
        $bar = $this->output->createProgressBar($total);
        $bar->start();
        $processed = 0;

        $query->chunk($chunkSize, function (Collection $skills) use ($bar, &$processed): void {
            /** @var Collection<int, Skill> $skills */
            $texts = $skills->map(fn (Skill $skill): string => $skill->description
                ? "{$skill->canonical_name} — {$skill->description}"
                : $skill->canonical_name
            )->all();

            try {
                $embeddings = $this->embeddingService->embedMany($texts);
            } catch (Throwable $e) {
                $this->components->error("Embedding failed: {$e->getMessage()}");

                return;
            }

            foreach ($skills->values() as $index => $skill) {
                $skill->embedding = $embeddings[$index];
                $skill->saveQuietly();
            }

            $processed += $skills->count();
            $bar->advance($skills->count());
        });

        $bar->finish();
        $this->newLine();
        $this->components->info("Embedded {$processed} skills.");
    }

    private function embedSkillAliases(int $chunkSize, bool $force): void
    {
        $query = SkillAlias::query()->orderBy('id');

        if (! $force) {
            $query->whereNull('alias_embedding');
        }

        $total = $query->count();

        if ($total === 0) {
            $this->components->info('No skill aliases to embed.');

            return;
        }

        $this->components->info("Embedding {$total} skill aliases…");
        $bar = $this->output->createProgressBar($total);
        $bar->start();
        $processed = 0;

        $query->chunk($chunkSize, function (Collection $aliases) use ($bar, &$processed): void {
            /** @var Collection<int, SkillAlias> $aliases */
            $texts = $aliases->map(fn (SkillAlias $alias): string => $alias->surface_form)->all();

            try {
                $embeddings = $this->embeddingService->embedMany($texts);
            } catch (Throwable $e) {
                $this->components->error("Embedding failed: {$e->getMessage()}");

                return;
            }

            foreach ($aliases->values() as $index => $alias) {
                $alias->alias_embedding = $embeddings[$index];
                $alias->saveQuietly();
            }

            $processed += $aliases->count();
            $bar->advance($aliases->count());
        });

        $bar->finish();
        $this->newLine();
        $this->components->info("Embedded {$processed} skill aliases.");
    }
}
