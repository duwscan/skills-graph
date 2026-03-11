<?php

namespace App\Ai;

use App\Ai\Agents\SkillEnricher;
use App\Ai\Agents\SkillGenerator;
use App\Ai\Agents\SkillNormalizer;
use App\Ai\Agents\TaxonomyDesigner;
use Closure;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\File;
use Illuminate\Support\Str;
use Laravel\Ai\Responses\StructuredAgentResponse;

class TaxonomyService
{
    private const DEFAULT_BATCH_SIZE = 100;

    private const NORMALIZE_CHUNK_SIZE = 100;

    private const ENRICH_CHUNK_SIZE = 20;

    private string $outputPath;

    public function __construct(string $outputPath = '')
    {
        $this->outputPath = $outputPath !== ''
            ? $outputPath
            : database_path('seeders/data/taxonomy');
    }

    /**
     * Generate a taxonomy map (domains > categories > subcategories).
     *
     * @param  list<string>  $domains
     * @param  \Closure(string, array<string, mixed>): void|null  $onLogOutput
     * @return array{domains: list<array{domain: string, categories: list<array{category: string, subcategories: list<string>}>}>}
     */
    public function designTaxonomy(array $domains, ?string $provider = null, ?\Closure $onLogOutput = null): array
    {
        $agent = new TaxonomyDesigner($domains);

        $prompt = 'Design a practical labor-market skill taxonomy for the specified domains. '
            .'Create 8 to 15 categories per domain and 5 to 12 subcategories per category.';

        /** @var StructuredAgentResponse $response */
        $response = $provider !== null
            ? $agent->prompt($prompt, provider: $provider)
            : $agent->prompt($prompt);

        $result = $response->toArray();

        $onLogOutput?->__invoke('taxonomy', $result);

        $this->writeJson('taxonomy_map.json', $result);

        return $result;
    }

    /**
     * Generate a batch of skills for a specific taxonomy segment.
     *
     * @param  \Closure(string, array<string, mixed>, array<string, string>): void|null  $onLogOutput
     * @return array{domain: string, category: string, subcategory: string, skills: list<array<string, mixed>>}
     */
    public function generateSkillBatch(
        string $domain,
        string $category,
        string $subcategory,
        int $targetCount = self::DEFAULT_BATCH_SIZE,
        ?string $provider = null,
        ?\Closure $onLogOutput = null,
    ): array {
        $agent = new SkillGenerator(
            domain: $domain,
            category: $category,
            subcategory: $subcategory,
        );

        $prompt = "Generate {$targetCount} skills for the following taxonomy segment.\n\n"
            ."Domain: {$domain}\n"
            ."Category: {$category}\n"
            ."Subcategory: {$subcategory}\n\n"
            .'Requirements:\n'
            ."- Focus on current real-world hiring market.\n"
            ."- Exclude job titles, certifications, and degrees.\n"
            ."- Avoid duplicates and near-duplicates.\n"
            ."- Normalize synonyms into aliases.\n"
            .'- Prefer practical skills used in hiring.';

        /** @var StructuredAgentResponse $response */
        $response = $provider !== null
            ? $agent->prompt($prompt, provider: $provider)
            : $agent->prompt($prompt);

        $result = $response->toArray();

        $onLogOutput?->__invoke('skills', $result, [
            'domain' => $domain,
            'category' => $category,
            'subcategory' => $subcategory,
        ]);

        $filename = $this->batchFilename($domain, $category, $subcategory);
        $this->writeJson("skill_batches/{$filename}", $result);

        return $result;
    }

    /**
     * Normalize and deduplicate a set of skills.
     *
     * @param  list<array<string, mixed>>  $skills
     * @param  \Closure(string, array<string, mixed>, int): void|null  $onLogOutput
     * @return array{merged_skills: list<array<string, mixed>>, removed_duplicates: list<array{removed: string, merged_into: string, reason: string}>}
     */
    public function normalizeSkills(array $skills, ?string $provider = null, ?\Closure $onLogOutput = null): array
    {
        $allMerged = [];
        $allRemoved = [];
        $chunkIndex = 0;

        foreach (array_chunk($skills, self::NORMALIZE_CHUNK_SIZE) as $chunk) {
            $agent = new SkillNormalizer;

            $skillsJson = json_encode($chunk, flags: JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
            $prompt = "Normalize and deduplicate the following skill records:\n\n{$skillsJson}";

            /** @var StructuredAgentResponse $response */
            $response = $provider !== null
                ? $agent->prompt($prompt, provider: $provider)
                : $agent->prompt($prompt);

            $result = $response->toArray();

            $onLogOutput?->__invoke('normalize', $result, ['chunk' => $chunkIndex++]);

            $allMerged = array_merge($allMerged, $result['merged_skills'] ?? []);
            $allRemoved = array_merge($allRemoved, $result['removed_duplicates'] ?? []);
        }

        $output = [
            'merged_skills' => $allMerged,
            'removed_duplicates' => $allRemoved,
        ];

        $this->writeJson('skills_normalized.json', $output);

        return $output;
    }

    /**
     * Enrich skills with additional metadata.
     *
     * @param  list<array<string, mixed>>  $skills
     * @param  \Closure(string, array<string, mixed>, int): void|null  $onLogOutput
     * @return array{skills: list<array<string, mixed>>}
     */
    public function enrichSkills(array $skills, ?string $provider = null, ?\Closure $onLogOutput = null): array
    {
        $allEnriched = [];
        $chunkIndex = 0;

        foreach (array_chunk($skills, self::ENRICH_CHUNK_SIZE) as $chunk) {
            $agent = new SkillEnricher;

            $skillsJson = json_encode($chunk, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
            $prompt = "Enrich the following canonical skills with metadata:\n\n{$skillsJson}";

            /** @var StructuredAgentResponse $response */
            $response = $provider !== null
                ? $agent->prompt($prompt, provider: $provider)
                : $agent->prompt($prompt);

            $result = $response->toArray();

            $onLogOutput?->__invoke('enrich', $result, ['chunk' => $chunkIndex++]);

            $allEnriched = array_merge($allEnriched, $result['skills'] ?? []);
        }

        $output = ['skills' => $allEnriched];

        $this->writeJson('skills_enriched.json', $output);

        return $output;
    }

    /**
     * Import enriched skills into the database.
     *
     * @param  list<array<string, mixed>>  $skills
     * @param  array{domains: list<array{domain: string, categories: list<array{category: string, subcategories: list<string>}>}>}|null  $taxonomyMap
     */
    public function importToDatabase(array $skills, ?array $taxonomyMap = null): void
    {
        $this->truncateSkillGraphTables();

        $timestamp = now();
        $skillRows = [];
        $aliasRows = [];
        $relationshipQueue = [];
        $canonicalToId = [];
        $externalIdCounter = $this->nextExternalIdCounter();
        $usedSlugs = [];

        foreach ($skills as $skill) {
            $id = (string) Str::uuid();
            $canonicalName = trim($skill['canonical_name'] ?? '');

            if ($canonicalName === '') {
                continue;
            }

            $canonicalToId[$canonicalName] = $id;
            $externalId = sprintf('SK-%06d', $externalIdCounter++);

            $slug = $this->makeUniqueSlug($canonicalName, $usedSlugs);
            $domain = $skill['domain'] ?? '';
            $category = $skill['category'] ?? '';
            $subcategory = $skill['subcategory'] ?? '';
            $skillType = $this->mapSkillTypeToCategory($skill['skill_type'] ?? 'hard_skill');

            $pathSegments = array_filter([
                $this->normalizePathSegment($domain),
                $this->normalizePathSegment($category),
                $this->normalizePathSegment($subcategory),
                $this->normalizePathSegment($canonicalName),
            ]);
            $path = implode('.', $pathSegments);

            $metadata = [
                'domain' => $domain,
                'taxonomy_category' => $category,
                'subcategory' => $subcategory,
                'skill_type' => $skill['skill_type'] ?? 'hard_skill',
                'keywords' => $skill['keywords'] ?? [],
                'common_roles' => $skill['common_roles'] ?? [],
                'confidence_seed' => $skill['confidence_seed'] ?? 0.7,
                'market_relevance_score' => $skill['market_relevance_score'] ?? 0.7,
                'source_type' => 'llm_seed',
                'seed_batch' => 'taxonomy_pipeline_v1',
            ];

            $skillRows[] = [
                'id' => $id,
                'external_id' => $externalId,
                'canonical_name' => $canonicalName,
                'slug' => $slug,
                'description' => $skill['description'] ?? "{$canonicalName} skill.",
                'status' => $this->mapStatusToValid($skill['status'] ?? 'active'),
                'category' => $skillType,
                'path' => $path,
                'embedding' => null,
                'source' => 'llm_seed',
                'metadata' => json_encode($metadata, JSON_UNESCAPED_UNICODE),
                'created_at' => $timestamp,
                'updated_at' => $timestamp,
            ];

            $aliases = $skill['aliases'] ?? [];
            $seenAliasKey = [];
            foreach ($aliases as $aliasIndex => $alias) {
                $alias = trim($alias);
                if ($alias === '' || Str::lower($alias) === Str::lower($canonicalName)) {
                    continue;
                }

                $aliasKey = $id.'|'.($skill['language'] ?? 'en').'|'.$alias;
                if (isset($seenAliasKey[$aliasKey])) {
                    continue;
                }
                $seenAliasKey[$aliasKey] = true;

                $aliasRows[] = [
                    'id' => (string) Str::uuid(),
                    'skill_id' => $id,
                    'surface_form' => $alias,
                    'locale' => $skill['language'] ?? 'en',
                    'source' => 'llm_discovered',
                    'is_primary' => $aliasIndex === 0,
                    'alias_embedding' => null,
                    'created_at' => $timestamp,
                    'updated_at' => $timestamp,
                ];
            }

            if (! empty($skill['parent_skill'])) {
                $relationshipQueue[] = [
                    'child_canonical' => $canonicalName,
                    'parent_canonical' => $skill['parent_skill'],
                    'type' => 'parent_of',
                ];
            }

            foreach ($skill['related_skills'] ?? [] as $relatedName) {
                $relationshipQueue[] = [
                    'source_canonical' => $canonicalName,
                    'target_canonical' => $relatedName,
                    'type' => 'related_to',
                ];
            }
        }

        foreach (array_chunk($skillRows, 250) as $chunk) {
            DB::table(table: 'skills')->insert($chunk);
        }

        foreach (array_chunk($aliasRows, 500) as $chunk) {
            DB::table('skill_aliases')->insert($chunk);
        }

        $this->insertRelationships($relationshipQueue, $canonicalToId, $timestamp);
    }

    private function truncateSkillGraphTables(): void
    {
        DB::table('skill_co_occurrences')->delete();
        DB::table('skill_relationships')->delete();
        DB::table('skill_aliases')->delete();
        DB::table('skills')->delete();
    }

    /**
     * Run the full pipeline.
     *
     * @param  list<string>  $domains
     * @param  \Closure(string, array<string, mixed>, array<string, mixed>|null): void|null  $onLogOutput
     */
    public function runPipeline(
        array $domains,
        ?string $phase = null,
        bool $resume = false,
        ?string $provider = null,
        ?Closure $onProgress = null,
        ?Closure $onLogOutput = null,
    ): void {
        $taxonomyMap = $this->loadTaxonomyMap();
        // $phase ??= 'all';
        // $shouldRun = fn (string $p): bool => $phase === 'all' || $phase === $p;

        // $taxonomyMap = null;
        // if ($shouldRun('taxonomy')) {
        //     $this->notify($onProgress, 'phase_start', ['phase' => 'taxonomy']);

        //     $taxonomyMap = $resume ? $this->loadTaxonomyMap() : null;
        //     if ($taxonomyMap === null) {
        //         $taxonomyMap = $this->designTaxonomy($domains, $provider, $onLogOutput);
        //     }

        //     $this->notify($onProgress, 'phase_complete', ['phase' => 'taxonomy', 'domains' => count($taxonomyMap['domains'] ?? [])]);
        // }

        // $allSkills = [];
        // if ($shouldRun('skills')) {
        //     $taxonomyMap ??= $this->loadTaxonomyMap();
        //     if ($taxonomyMap === null) {
        //         throw new \RuntimeException('Taxonomy map not found. Run the taxonomy phase first.');
        //     }

        //     $this->notify($onProgress, 'phase_start', ['phase' => 'skills']);

        //     $totalBatches = $this->countBatches($taxonomyMap);
        //     $completedBatches = 0;

        //     foreach ($taxonomyMap['domains'] as $domainData) {
        //         $domainName = $domainData['domain'];

        //         foreach ($domainData['categories'] as $categoryData) {
        //             $categoryName = $categoryData['category'];

        //             foreach ($categoryData['subcategories'] as $subcategoryName) {
        //                 $batchFile = $this->batchFilename($domainName, $categoryName, $subcategoryName);

        //                 if ($resume && $this->jsonFileExists("skill_batches/{$batchFile}")) {
        //                     $cached = $this->readJson("skill_batches/{$batchFile}");
        //                     if ($cached !== null) {
        //                         $batchSkills = $cached['skills'] ?? [];
        //                         foreach ($batchSkills as $s) {
        //                             $allSkills[] = array_merge($s, [
        //                                 'domain' => $domainName,
        //                                 'category' => $categoryName,
        //                                 'subcategory' => $subcategoryName,
        //                             ]);
        //                         }
        //                         $completedBatches++;
        //                         $this->notify($onProgress, 'batch_complete', [
        //                             'domain' => $domainName,
        //                             'category' => $categoryName,
        //                             'subcategory' => $subcategoryName,
        //                             'count' => count($batchSkills),
        //                             'progress' => $completedBatches,
        //                             'total' => $totalBatches,
        //                         ]);

        //                         continue;
        //                     }
        //                 }

        //                 $targetCount = $this->targetCountForDomain($domainName, count($categoryData['subcategories']));

        //                 $batch = $this->generateSkillBatch(
        //                     domain: $domainName,
        //                     category: $categoryName,
        //                     subcategory: $subcategoryName,
        //                     targetCount: $targetCount,
        //                     provider: $provider,
        //                     onLogOutput: $onLogOutput,
        //                 );

        //                 $batchSkills = $batch['skills'] ?? [];
        //                 foreach ($batchSkills as $s) {
        //                     $allSkills[] = array_merge($s, [
        //                         'domain' => $domainName,
        //                         'category' => $categoryName,
        //                         'subcategory' => $subcategoryName,
        //                     ]);
        //                 }

        //                 $completedBatches++;
        //                 $this->notify($onProgress, 'batch_complete', [
        //                     'domain' => $domainName,
        //                     'category' => $categoryName,
        //                     'subcategory' => $subcategoryName,
        //                     'count' => count($batchSkills),
        //                     'progress' => $completedBatches,
        //                     'total' => $totalBatches,
        //                 ]);
        //             }
        //         }
        //     }

        //     $this->notify($onProgress, 'phase_complete', ['phase' => 'skills', 'total_skills' => count($allSkills)]);
        // }

        // if ($shouldRun('normalize')) {
        //     $this->notify($onProgress, 'phase_start', ['phase' => 'normalize']);

        //     if ($allSkills === []) {
        //         $allSkills = $this->loadAllBatchSkills();
        //     }

        //     $normalized = $this->normalizeSkills($allSkills, $provider, $onLogOutput);
        //     $allSkills = $normalized['merged_skills'];

        //     $this->notify($onProgress, 'phase_complete', [
        //         'phase' => 'normalize',
        //         'merged_count' => count($allSkills),
        //         'removed_count' => count($normalized['removed_duplicates']),
        //     ]);
        // }

        // if ($shouldRun('enrich')) {
        //     $this->notify($onProgress, 'phase_start', ['phase' => 'enrich']);

        //     if ($allSkills === []) {
        //         $normalized = $this->loadNormalized();
        //         $allSkills = $normalized['merged_skills'] ?? [];
        //     }

        //     $enriched = $this->enrichSkills($allSkills, $provider, $onLogOutput);
        //     $allSkills = $this->mergeEnrichedData($allSkills, $enriched['skills'] ?? []);

        //     $this->writeJson('skills_final.json', ['skills' => $allSkills]);

        //     $this->notify($onProgress, 'phase_complete', ['phase' => 'enrich', 'enriched_count' => count($allSkills)]);
        // }

        // if ($shouldRun('import')) {
        //     $this->notify($onProgress, 'phase_start', ['phase' => 'import']);

        //     if ($allSkills === []) {
        //         $final = $this->readJson('skills_final.json') ?? $this->readJson('skills_enriched.json');
        //         $allSkills = $final['skills'] ?? [];
        //     }

        //     $this->importToDatabase($allSkills, $taxonomyMap);

        //     $this->notify($onProgress, 'phase_complete', ['phase' => 'import', 'imported_count' => count($allSkills)]);
        // }
        $this->notify($onProgress, 'phase_start', ['phase' => 'import']);
        $allSkills = [];
        if ($allSkills === []) {
            $final = $this->readJson('skills_final.json') ?? $this->readJson('skills_enriched.json');
            $allSkills = $final['skills'] ?? [];
        }

        $this->importToDatabase($allSkills, $taxonomyMap);

        $this->notify($onProgress, 'phase_complete', ['phase' => 'import', 'imported_count' => count($allSkills)]);
    }

    /**
     * @return array{domains: list<array{domain: string, categories: list<array{category: string, subcategories: list<string>}>}>}|null
     */
    public function loadTaxonomyMap(): ?array
    {
        return $this->readJson('taxonomy_map.json');
    }

    /**
     * @return list<array<string, mixed>>
     */
    public function loadAllBatchSkills(): array
    {
        $batchDir = $this->outputPath.'/skill_batches';
        if (! File::isDirectory($batchDir)) {
            return [];
        }

        $skills = [];
        foreach (File::files($batchDir) as $file) {
            if ($file->getExtension() !== 'json') {
                continue;
            }

            $data = json_decode($file->getContents(), true);
            if (! is_array($data)) {
                continue;
            }

            $domain = $data['domain'] ?? '';
            $category = $data['category'] ?? '';
            $subcategory = $data['subcategory'] ?? '';

            foreach ($data['skills'] ?? [] as $skill) {
                $skills[] = array_merge($skill, [
                    'domain' => $domain,
                    'category' => $category,
                    'subcategory' => $subcategory,
                ]);
            }
        }

        return $skills;
    }

    /**
     * @return array{merged_skills: list<array<string, mixed>>, removed_duplicates: list<array<string, mixed>>}|null
     */
    public function loadNormalized(): ?array
    {
        return $this->readJson('skills_normalized.json');
    }

    /**
     * @return array{skills: list<array<string, mixed>>}|null
     */
    public function loadEnriched(): ?array
    {
        $final = $this->readJson('skills_final.json');
        if ($final !== null) {
            return $final;
        }

        return $this->readJson('skills_enriched.json');
    }

    /**
     * @param  list<array{child_canonical?: string, parent_canonical?: string, source_canonical?: string, target_canonical?: string, type: string}>  $queue
     * @param  array<string, string>  $canonicalToId
     */
    private function insertRelationships(array $queue, array $canonicalToId, mixed $timestamp): void
    {
        $rows = [];
        $seen = [];

        foreach ($queue as $rel) {
            if ($rel['type'] === 'parent_of') {
                $childId = $canonicalToId[$rel['child_canonical']] ?? null;
                $parentId = $canonicalToId[$rel['parent_canonical']] ?? null;

                if ($childId === null || $parentId === null || $childId === $parentId) {
                    continue;
                }

                $key = "{$parentId}|{$childId}|parent_of";
                if (isset($seen[$key])) {
                    continue;
                }
                $seen[$key] = true;

                $rows[] = [
                    'id' => (string) Str::uuid(),
                    'source_skill_id' => $parentId,
                    'target_skill_id' => $childId,
                    'relationship_type' => 'parent_of',
                    'confidence' => 0.9,
                    'weight' => 0.9,
                    'provenance' => 'llm_predicted',
                    'status' => 'active',
                    'created_at' => $timestamp,
                    'updated_at' => $timestamp,
                ];
            } elseif ($rel['type'] === 'related_to') {
                $sourceId = $canonicalToId[$rel['source_canonical']] ?? null;
                $targetId = $canonicalToId[$rel['target_canonical']] ?? null;

                if ($sourceId === null || $targetId === null || $sourceId === $targetId) {
                    continue;
                }

                $key = "{$sourceId}|{$targetId}|related_to";
                if (isset($seen[$key])) {
                    continue;
                }
                $seen[$key] = true;

                $rows[] = [
                    'id' => (string) Str::uuid(),
                    'source_skill_id' => $sourceId,
                    'target_skill_id' => $targetId,
                    'relationship_type' => 'related_to',
                    'confidence' => 0.75,
                    'weight' => 0.7,
                    'provenance' => 'llm_predicted',
                    'status' => 'active',
                    'created_at' => $timestamp,
                    'updated_at' => $timestamp,
                ];
            }

            if (count($rows) >= 500) {
                DB::table('skill_relationships')->insertOrIgnore($rows);
                $rows = [];
            }
        }

        if ($rows !== []) {
            DB::table('skill_relationships')->insertOrIgnore($rows);
        }
    }

    /**
     * @param  list<array<string, mixed>>  $baseSkills
     * @param  list<array<string, mixed>>  $enrichedSkills
     * @return list<array<string, mixed>>
     */
    private function mergeEnrichedData(array $baseSkills, array $enrichedSkills): array
    {
        $enrichedByName = [];
        foreach ($enrichedSkills as $enriched) {
            $name = $enriched['canonical_name'] ?? '';
            if ($name !== '') {
                $enrichedByName[Str::lower($name)] = $enriched;
            }
        }

        return array_map(function (array $skill) use ($enrichedByName): array {
            $key = Str::lower($skill['canonical_name'] ?? '');
            $enriched = $enrichedByName[$key] ?? null;

            if ($enriched === null) {
                return $skill;
            }

            return array_merge($skill, array_filter([
                'aliases' => $enriched['aliases'] ?? null,
                'description' => $enriched['description'] ?? null,
                'parent_skill' => $enriched['parent_skill'] ?? null,
                'related_skills' => $enriched['related_skills'] ?? null,
                'keywords' => $enriched['keywords'] ?? null,
                'common_roles' => $enriched['common_roles'] ?? null,
                'confidence_seed' => $enriched['confidence_seed'] ?? null,
                'market_relevance_score' => $enriched['market_relevance_score'] ?? null,
            ], fn (mixed $v): bool => $v !== null));
        }, $baseSkills);
    }

    private function targetCountForDomain(string $domain, int $subcategoryCount): int
    {
        $domainTargets = [
            'IT' => 2400,
            'Finance' => 1550,
            'F&B' => 1050,
        ];

        $totalForDomain = $domainTargets[$domain] ?? 1000;

        $perSubcategory = (int) ceil($totalForDomain / max($subcategoryCount, 1));

        return max(10, min(10, $perSubcategory));
    }

    /**
     * @param  array{domains: list<array{domain: string, categories: list<array{category: string, subcategories: list<string>}>}>}  $taxonomyMap
     */
    private function countBatches(array $taxonomyMap): int
    {
        $count = 0;

        foreach ($taxonomyMap['domains'] as $domain) {
            foreach ($domain['categories'] as $category) {
                $count += count($category['subcategories']);
            }
        }

        return $count;
    }

    private function batchFilename(string $domain, string $category, string $subcategory): string
    {
        return Str::slug("{$domain} {$category} {$subcategory}", '_').'.json';
    }

    private function mapStatusToValid(string $status): string
    {
        $validStatuses = ['candidate', 'active', 'deprecated', 'merged'];
        $normalized = strtolower(trim($status));

        if (in_array($normalized, $validStatuses, true)) {
            return $normalized;
        }

        return 'active';
    }

    private function mapSkillTypeToCategory(string $skillType): string
    {
        $validCategories = [
            'domain', 'tool', 'certification', 'soft_skill', 'methodology', 'language',
            'hard_skill', 'tool_skill', 'process_skill', 'analytical_skill',
            'compliance_skill', 'communication_skill', 'operational_skill',
        ];

        $normalized = strtolower(trim($skillType));

        if (in_array($normalized, $validCategories, true)) {
            return $normalized;
        }

        $variantMap = [
            'hard skill' => 'hard_skill',
            'tool skill' => 'tool_skill',
            'process skill' => 'process_skill',
            'analytical skill' => 'analytical_skill',
            'compliance skill' => 'compliance_skill',
            'communication skill' => 'communication_skill',
            'operational skill' => 'operational_skill',
        ];

        return $variantMap[$normalized] ?? 'hard_skill';
    }

    /**
     * @param  array<string, bool>  $usedSlugs
     */
    private function makeUniqueSlug(string $canonicalName, array &$usedSlugs): string
    {
        $baseSlug = Str::slug($canonicalName);
        if ($baseSlug === '') {
            $baseSlug = 'skill';
        }

        $slug = $baseSlug;
        $counter = 2;

        while (isset($usedSlugs[$slug])) {
            $slug = $baseSlug.'-'.$counter;
            $counter++;
        }

        $usedSlugs[$slug] = true;

        return $slug;
    }

    private function normalizePathSegment(string $segment): string
    {
        $normalized = Str::of($segment)
            ->lower()
            ->ascii()
            ->replaceMatches('/[^a-z0-9_]+/', '_')
            ->trim('_')
            ->value();

        if ($normalized === '') {
            return 'node';
        }

        if (preg_match('/^[0-9]/', $normalized) === 1) {
            $normalized = 'n_'.$normalized;
        }

        if (strlen($normalized) > 60) {
            $normalized = rtrim(substr($normalized, 0, 60), '_');
        }

        return $normalized !== '' ? $normalized : 'node';
    }

    private function nextExternalIdCounter(): int
    {
        $maxId = DB::table('skills')
            ->selectRaw('MAX(CAST(SUBSTRING(external_id FROM 4) AS INTEGER)) as max_id')
            ->value('max_id');

        return ($maxId ?? 0) + 1;
    }

    /**
     * @param  array<string, mixed>  $data
     */
    private function notify(?Closure $onProgress, string $event, array $data): void
    {
        if ($onProgress !== null) {
            $onProgress($event, $data);
        }
    }

    /**
     * @param  array<string, mixed>  $data
     */
    private function writeJson(string $relativePath, array $data): void
    {
        $fullPath = $this->outputPath.'/'.$relativePath;
        $directory = dirname($fullPath);

        if (! File::isDirectory($directory)) {
            File::makeDirectory($directory, 0755, true);
        }

        File::put($fullPath, json_encode($data, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
    }

    /**
     * @return array<string, mixed>|null
     */
    private function readJson(string $relativePath): ?array
    {
        $fullPath = $this->outputPath.'/'.$relativePath;

        if (! File::exists($fullPath)) {
            return null;
        }

        $decoded = json_decode(File::get($fullPath), true);

        return is_array($decoded) ? $decoded : null;
    }

    private function jsonFileExists(string $relativePath): bool
    {
        return File::exists($this->outputPath.'/'.$relativePath);
    }
}
