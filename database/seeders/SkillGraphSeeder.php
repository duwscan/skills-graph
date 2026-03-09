<?php

namespace Database\Seeders;

use App\Ai\TaxonomyService;
use App\Models\LocaleConfig;
use Carbon\CarbonImmutable;
use Carbon\CarbonInterface;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

class SkillGraphSeeder extends Seeder
{
    use WithoutModelEvents;

    private const TARGET_SKILL_COUNT = 15000;

    /**
     * @var array<string, bool>
     */
    private array $usedSlugs = [];

    /**
     * @var array<string, int>
     */
    private array $usedPaths = [];

    private int $externalIdCounter = 1;

    /**
     * Run the database seeds.
     *
     * Uses taxonomy-mode (LLM-generated data) when available, falling back to legacy ESCO mode.
     */
    public function run(): void
    {
        $this->truncateSkillGraphTables();
        $this->seedLocaleConfig();

        if ($this->hasTaxonomyData()) {
            $this->seedFromTaxonomy();

            return;
        }
    }

    private function hasTaxonomyData(): bool
    {
        $candidates = [
            database_path('seeders/data/taxonomy/skills_final.json'),
            database_path('seeders/data/taxonomy/skills_enriched.json'),
        ];

        foreach ($candidates as $path) {
            if (is_file($path)) {
                return true;
            }
        }

        return false;
    }

    private function seedFromTaxonomy(): void
    {
        $service = new TaxonomyService;
        $enriched = $service->loadEnriched();
        $taxonomyMap = $service->loadTaxonomyMap();
        $service->importToDatabase($enriched['skills'], $taxonomyMap);
    }
    private function seedLocaleConfig(): void
    {
        $localeRows = [
            LocaleConfig::factory()->make([
                'locale' => 'en',
                'display_name' => 'English',
                'is_active' => true,
                'coverage_pct' => 100,
            ])->getAttributes(),
            LocaleConfig::factory()->make([
                'locale' => 'en-US',
                'display_name' => 'English (United States)',
                'is_active' => true,
                'coverage_pct' => 88,
            ])->getAttributes(),
            LocaleConfig::factory()->make([
                'locale' => 'en-GB',
                'display_name' => 'English (United Kingdom)',
                'is_active' => true,
                'coverage_pct' => 84,
            ])->getAttributes(),
        ];

        LocaleConfig::query()->upsert(
            values: $localeRows,
            uniqueBy: ['locale'],
            update: ['display_name', 'is_active', 'coverage_pct'],
        );
    }

    private function truncateSkillGraphTables(): void
    {
        DB::table('skill_co_occurrences')->delete();
        DB::table('skill_relationships')->delete();
        DB::table('skill_aliases')->delete();
        DB::table('skills')->delete();
        DB::table('locale_config')->delete();
    }
}
