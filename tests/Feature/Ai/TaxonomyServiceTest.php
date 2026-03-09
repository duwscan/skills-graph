<?php

namespace Tests\Feature\Ai;

use App\Ai\Agents\SkillEnricher;
use App\Ai\Agents\SkillGenerator;
use App\Ai\Agents\SkillNormalizer;
use App\Ai\Agents\TaxonomyDesigner;
use App\Ai\TaxonomyService;
use Illuminate\Support\Facades\File;
use Tests\TestCase;

class TaxonomyServiceTest extends TestCase
{
    private string $outputPath;

    protected function setUp(): void
    {
        parent::setUp();

        $this->outputPath = storage_path('app/testing/taxonomy_'.uniqid());
        File::makeDirectory($this->outputPath, 0755, true);
    }

    protected function tearDown(): void
    {
        File::deleteDirectory($this->outputPath);

        parent::tearDown();
    }

    public function test_design_taxonomy_calls_agent_and_writes_file(): void
    {
        TaxonomyDesigner::fake([
            $this->fakeTaxonomyMap(),
        ]);

        $service = new TaxonomyService($this->outputPath);
        $result = $service->designTaxonomy(['IT', 'Finance']);

        $this->assertArrayHasKey('domains', $result);
        $this->assertCount(2, $result['domains']);
        $this->assertEquals('IT', $result['domains'][0]['domain']);

        $this->assertFileExists($this->outputPath.'/taxonomy_map.json');

        TaxonomyDesigner::assertPrompted(function ($prompt) {
            return str_contains($prompt->prompt, 'taxonomy');
        });
    }

    public function test_generate_skill_batch_calls_agent_and_writes_file(): void
    {
        SkillGenerator::fake([
            $this->fakeSkillBatch(),
        ]);

        $service = new TaxonomyService($this->outputPath);
        $result = $service->generateSkillBatch(
            domain: 'IT',
            category: 'Software Engineering',
            subcategory: 'Backend Development',
            targetCount: 5,
        );

        $this->assertArrayHasKey('skills', $result);
        $this->assertNotEmpty($result['skills']);

        $batchDir = $this->outputPath.'/skill_batches';
        $this->assertTrue(File::isDirectory($batchDir));

        $batchFiles = File::files($batchDir);
        $this->assertNotEmpty($batchFiles);
    }

    public function test_normalize_skills_calls_agent_and_writes_file(): void
    {
        SkillNormalizer::fake([
            $this->fakeNormalizedResult(),
        ]);

        $service = new TaxonomyService($this->outputPath);
        $result = $service->normalizeSkills([
            ['canonical_name' => 'REST API Development', 'skill_type' => 'hard_skill', 'description' => 'Building REST APIs'],
            ['canonical_name' => 'RESTful API Design', 'skill_type' => 'hard_skill', 'description' => 'Designing REST APIs'],
        ]);

        $this->assertArrayHasKey('merged_skills', $result);
        $this->assertArrayHasKey('removed_duplicates', $result);

        $this->assertFileExists($this->outputPath.'/skills_normalized.json');
    }

    public function test_enrich_skills_calls_agent_and_writes_file(): void
    {
        SkillEnricher::fake([
            $this->fakeEnrichedResult(),
        ]);

        $service = new TaxonomyService($this->outputPath);
        $result = $service->enrichSkills([
            ['canonical_name' => 'Docker', 'domain' => 'IT', 'category' => 'DevOps', 'subcategory' => 'Containerization'],
        ]);

        $this->assertArrayHasKey('skills', $result);

        $this->assertFileExists($this->outputPath.'/skills_enriched.json');
    }

    public function test_load_taxonomy_map_returns_null_when_no_file(): void
    {
        $service = new TaxonomyService($this->outputPath);

        $this->assertNull($service->loadTaxonomyMap());
    }

    public function test_load_taxonomy_map_returns_data_when_file_exists(): void
    {
        File::put(
            $this->outputPath.'/taxonomy_map.json',
            json_encode($this->fakeTaxonomyMap())
        );

        $service = new TaxonomyService($this->outputPath);
        $result = $service->loadTaxonomyMap();

        $this->assertNotNull($result);
        $this->assertArrayHasKey('domains', $result);
    }

    public function test_load_all_batch_skills_reads_batch_files(): void
    {
        $batchDir = $this->outputPath.'/skill_batches';
        File::makeDirectory($batchDir, 0755, true);
        File::put($batchDir.'/it_devops_containerization.json', json_encode($this->fakeSkillBatch()));

        $service = new TaxonomyService($this->outputPath);
        $skills = $service->loadAllBatchSkills();

        $this->assertNotEmpty($skills);
        $this->assertArrayHasKey('canonical_name', $skills[0]);
    }

    public function test_load_all_batch_skills_returns_empty_when_no_directory(): void
    {
        $service = new TaxonomyService($this->outputPath);
        $skills = $service->loadAllBatchSkills();

        $this->assertEmpty($skills);
    }

    public function test_load_normalized_returns_null_when_no_file(): void
    {
        $service = new TaxonomyService($this->outputPath);

        $this->assertNull($service->loadNormalized());
    }

    public function test_load_enriched_returns_null_when_no_file(): void
    {
        $service = new TaxonomyService($this->outputPath);

        $this->assertNull($service->loadEnriched());
    }

    public function test_load_enriched_prefers_final_over_enriched(): void
    {
        File::put(
            $this->outputPath.'/skills_final.json',
            json_encode(['skills' => [['canonical_name' => 'From Final']]])
        );
        File::put(
            $this->outputPath.'/skills_enriched.json',
            json_encode(['skills' => [['canonical_name' => 'From Enriched']]])
        );

        $service = new TaxonomyService($this->outputPath);
        $result = $service->loadEnriched();

        $this->assertNotNull($result);
        $this->assertEquals('From Final', $result['skills'][0]['canonical_name']);
    }

    public function test_taxonomy_designer_agent_has_constructor_params(): void
    {
        $agent = new TaxonomyDesigner(['IT', 'Finance', 'F&B']);

        $this->assertEquals(['IT', 'Finance', 'F&B'], $agent->domains);
    }

    public function test_skill_generator_agent_has_constructor_params(): void
    {
        $agent = new SkillGenerator(
            domain: 'IT',
            category: 'Software Engineering',
            subcategory: 'Backend Development',
        );

        $this->assertEquals('IT', $agent->domain);
        $this->assertEquals('Software Engineering', $agent->category);
        $this->assertEquals('Backend Development', $agent->subcategory);
    }

    public function test_skill_generator_agent_instructions_include_context(): void
    {
        $agent = new SkillGenerator(
            domain: 'IT',
            category: 'Software Engineering',
            subcategory: 'Backend',
        );

        $instructions = (string) $agent->instructions();

        $this->assertStringContainsString('IT', $instructions);
        $this->assertStringContainsString('Software Engineering', $instructions);
        $this->assertStringContainsString('Backend', $instructions);
    }

    public function test_auto_fake_generates_valid_structured_output(): void
    {
        TaxonomyDesigner::fake();

        $service = new TaxonomyService($this->outputPath);
        $result = $service->designTaxonomy(['IT']);

        $this->assertArrayHasKey('domains', $result);
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
                            'category' => 'Software Engineering',
                            'subcategories' => ['Backend Development', 'Frontend Development'],
                        ],
                        [
                            'category' => 'DevOps',
                            'subcategories' => ['Containerization', 'CI/CD'],
                        ],
                    ],
                ],
                [
                    'domain' => 'Finance',
                    'categories' => [
                        [
                            'category' => 'Accounting',
                            'subcategories' => ['Financial Reporting', 'Tax Compliance'],
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
                    'description' => 'Container runtime for application isolation.',
                    'parent_skill' => null,
                    'related_skills' => ['Kubernetes'],
                    'keywords' => ['container', 'docker'],
                    'common_roles' => ['DevOps Engineer'],
                    'language' => 'en',
                    'status' => 'active',
                ],
                [
                    'canonical_name' => 'Kubernetes',
                    'aliases' => ['K8s'],
                    'skill_type' => 'tool_skill',
                    'description' => 'Container orchestration platform.',
                    'parent_skill' => null,
                    'related_skills' => ['Docker'],
                    'keywords' => ['k8s', 'orchestration'],
                    'common_roles' => ['DevOps Engineer', 'SRE'],
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
                    'canonical_name' => 'REST API Development',
                    'aliases' => ['RESTful API Design', 'REST API Design'],
                    'skill_type' => 'hard_skill',
                    'description' => 'Designing and implementing REST-based APIs.',
                    'parent_skill' => 'API Development',
                    'related_skills' => ['OpenAPI', 'JSON'],
                    'keywords' => ['rest', 'api', 'restful'],
                    'common_roles' => ['Backend Developer'],
                    'domain' => 'IT',
                    'category' => 'Software Engineering',
                    'subcategory' => 'Backend Development',
                    'language' => 'en',
                    'status' => 'active',
                ],
            ],
            'removed_duplicates' => [
                [
                    'removed' => 'RESTful API Design',
                    'merged_into' => 'REST API Development',
                    'reason' => 'near_duplicate',
                ],
            ],
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
                    'aliases' => ['Docker Engine', 'Docker CE', 'Docker Desktop'],
                    'description' => 'Container runtime platform for building, shipping, and running applications in isolated environments.',
                    'parent_skill' => 'Containerization',
                    'related_skills' => ['Kubernetes', 'Docker Compose', 'Container Security'],
                    'keywords' => ['container', 'docker', 'devops', 'virtualization'],
                    'common_roles' => ['DevOps Engineer', 'SRE', 'Backend Developer'],
                    'confidence_seed' => 0.95,
                    'market_relevance_score' => 0.92,
                ],
            ],
        ];
    }
}
