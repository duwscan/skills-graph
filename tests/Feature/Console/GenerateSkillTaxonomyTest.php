<?php

namespace Tests\Feature\Console;

use App\Ai\Agents\SkillEnricher;
use App\Ai\Agents\SkillGenerator;
use App\Ai\Agents\SkillNormalizer;
use App\Ai\Agents\TaxonomyDesigner;
use Illuminate\Support\Facades\File;
use Tests\TestCase;

class GenerateSkillTaxonomyTest extends TestCase
{
    private string $taxonomyPath;

    protected function setUp(): void
    {
        parent::setUp();

        $this->taxonomyPath = database_path('seeders/data/taxonomy');

        if (File::isDirectory($this->taxonomyPath)) {
            File::cleanDirectory($this->taxonomyPath);
        }
    }

    protected function tearDown(): void
    {
        if (File::isDirectory($this->taxonomyPath)) {
            File::cleanDirectory($this->taxonomyPath);
            File::put($this->taxonomyPath.'/.gitkeep', '');
        }

        parent::tearDown();
    }

    public function test_command_runs_taxonomy_phase_successfully(): void
    {
        TaxonomyDesigner::fake([
            $this->fakeTaxonomyMap(),
        ]);

        $this->artisan('skills:generate-taxonomy', [
            '--phase' => 'taxonomy',
            '--domain' => ['it'],
        ])->assertSuccessful();

        TaxonomyDesigner::assertPrompted(function ($prompt) {
            return str_contains($prompt->prompt, 'taxonomy');
        });

        $this->assertFileExists($this->taxonomyPath.'/taxonomy_map.json');
    }

    public function test_command_dry_run_import_only_is_skipped(): void
    {
        $this->artisan('skills:generate-taxonomy', [
            '--phase' => 'import',
            '--dry-run' => true,
        ])->assertSuccessful();
    }

    public function test_command_resolves_domain_aliases(): void
    {
        TaxonomyDesigner::fake([
            $this->fakeTaxonomyMap(),
        ]);

        $this->artisan('skills:generate-taxonomy', [
            '--phase' => 'taxonomy',
            '--domain' => ['fnb'],
        ])->assertSuccessful();

        TaxonomyDesigner::assertPrompted(function ($prompt) {
            return str_contains($prompt->prompt, 'taxonomy');
        });
    }

    public function test_command_defaults_to_all_domains(): void
    {
        TaxonomyDesigner::fake([
            $this->fakeTaxonomyMap(),
        ]);

        $this->artisan('skills:generate-taxonomy', [
            '--phase' => 'taxonomy',
        ])->assertSuccessful();

        TaxonomyDesigner::assertPrompted(function ($prompt) {
            return str_contains($prompt->prompt, 'taxonomy');
        });
    }

    public function test_command_skills_phase_generates_batches(): void
    {
        File::put(
            $this->taxonomyPath.'/taxonomy_map.json',
            json_encode($this->fakeTaxonomyMap())
        );

        SkillGenerator::fake([
            $this->fakeSkillBatch(),
        ]);

        $this->artisan('skills:generate-taxonomy', [
            '--phase' => 'skills',
            '--domain' => ['it'],
        ])->assertSuccessful();

        $batchDir = $this->taxonomyPath.'/skill_batches';
        $this->assertTrue(File::isDirectory($batchDir));
    }

    public function test_command_normalize_phase(): void
    {
        $batchDir = $this->taxonomyPath.'/skill_batches';
        File::makeDirectory($batchDir, 0755, true);
        File::put($batchDir.'/it_devops_containerization.json', json_encode($this->fakeSkillBatch()));

        SkillNormalizer::fake([
            $this->fakeNormalizedResult(),
        ]);

        $this->artisan('skills:generate-taxonomy', [
            '--phase' => 'normalize',
        ])->assertSuccessful();

        $this->assertFileExists($this->taxonomyPath.'/skills_normalized.json');
    }

    public function test_command_enrich_phase(): void
    {
        File::put(
            $this->taxonomyPath.'/skills_normalized.json',
            json_encode($this->fakeNormalizedResult())
        );

        SkillEnricher::fake([
            $this->fakeEnrichedResult(),
        ]);

        $this->artisan('skills:generate-taxonomy', [
            '--phase' => 'enrich',
        ])->assertSuccessful();
    }

    /**
     * @return array{domains: list<array{domain: string, categories: list<array{category: string, subcategories: list<string>}>}>}
     */
    private function fakeTaxonomyMap(): array
    {
        return [
            'domains' => [
                [
                    'domain' => 'IT',
                    'categories' => [
                        [
                            'category' => 'DevOps',
                            'subcategories' => ['Containerization'],
                        ],
                    ],
                ],
            ],
        ];
    }

    /**
     * @return array{domain: string, category: string, subcategory: string, skills: list<array<string, mixed>>}
     */
    private function fakeSkillBatch(): array
    {
        return [
            'domain' => 'IT',
            'category' => 'DevOps',
            'subcategory' => 'Containerization',
            'skills' => [
                [
                    'canonical_name' => 'Docker',
                    'aliases' => ['Docker Engine'],
                    'skill_type' => 'tool_skill',
                    'description' => 'Container runtime.',
                    'parent_skill' => null,
                    'related_skills' => [],
                    'keywords' => ['docker'],
                    'common_roles' => ['DevOps Engineer'],
                    'language' => 'en',
                    'status' => 'active',
                ],
            ],
        ];
    }

    /**
     * @return array{merged_skills: list<array<string, mixed>>, removed_duplicates: list<array<string, mixed>>}
     */
    private function fakeNormalizedResult(): array
    {
        return [
            'merged_skills' => [
                [
                    'canonical_name' => 'Docker',
                    'aliases' => ['Docker Engine'],
                    'skill_type' => 'tool_skill',
                    'description' => 'Container runtime.',
                    'parent_skill' => null,
                    'related_skills' => [],
                    'keywords' => ['docker'],
                    'common_roles' => ['DevOps Engineer'],
                    'domain' => 'IT',
                    'category' => 'DevOps',
                    'subcategory' => 'Containerization',
                    'language' => 'en',
                    'status' => 'active',
                ],
            ],
            'removed_duplicates' => [],
        ];
    }

    /**
     * @return array{skills: list<array<string, mixed>>}
     */
    private function fakeEnrichedResult(): array
    {
        return [
            'skills' => [
                [
                    'canonical_name' => 'Docker',
                    'aliases' => ['Docker Engine', 'Docker CE'],
                    'description' => 'Container runtime for application isolation.',
                    'parent_skill' => 'Containerization',
                    'related_skills' => ['Kubernetes'],
                    'keywords' => ['container', 'docker'],
                    'common_roles' => ['DevOps Engineer', 'SRE'],
                    'confidence_seed' => 0.95,
                    'market_relevance_score' => 0.92,
                ],
            ],
        ];
    }
}
