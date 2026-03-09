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

        $skills = $this->seedSkills();

        $this->seedAliases($skills);
        $this->seedRelationships($skills);
        $this->seedCoOccurrences($skills);
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

        if ($enriched === null || ($enriched['skills'] ?? []) === []) {
            $this->command?->getOutput()?->writeln('<comment>Taxonomy data file found but empty, falling back to legacy mode.</comment>');
            $skills = $this->seedSkills();
            $this->seedAliases($skills);
            $this->seedRelationships($skills);
            $this->seedCoOccurrences($skills);

            return;
        }

        $taxonomyMap = $service->loadTaxonomyMap();
        $service->importToDatabase($enriched['skills'], $taxonomyMap);
    }

    /**
     * @return list<array{path_prefix:string,root_name:string,default_category:string,contexts:list<string>,core_skills:list<string>}>
     */
    private function domainBlueprints(): array
    {
        return [
            [
                'path_prefix' => 'tech.software.backend',
                'root_name' => 'Backend Development',
                'default_category' => 'domain',
                'contexts' => ['FinTech', 'E-commerce', 'SaaS', 'Healthcare'],
                'core_skills' => [
                    'PHP',
                    'Laravel',
                    'Symfony',
                    'Node.js API Development',
                    'REST API Design',
                    'GraphQL APIs',
                    'Microservices Architecture',
                    'Redis Caching',
                    'PostgreSQL Optimization',
                    'MySQL Performance Tuning',
                    'Message Queues',
                    'Event-Driven Architecture',
                    'Domain-Driven Design',
                    'OAuth 2.0',
                    'Authentication and Authorization',
                    'Unit Testing',
                    'Integration Testing',
                    'API Versioning',
                    'Docker for Backend',
                    'Clean Architecture',
                ],
            ],
            [
                'path_prefix' => 'tech.software.frontend',
                'root_name' => 'Frontend Development',
                'default_category' => 'domain',
                'contexts' => ['E-commerce', 'Media', 'EdTech', 'Mobile Web'],
                'core_skills' => [
                    'HTML5',
                    'CSS3',
                    'JavaScript',
                    'TypeScript',
                    'React',
                    'Vue.js',
                    'Angular',
                    'Next.js',
                    'State Management',
                    'Responsive Web Design',
                    'Accessibility Standards',
                    'Web Performance Optimization',
                    'Tailwind CSS',
                    'Component Design Systems',
                    'Frontend Testing',
                    'End-to-End Testing',
                    'Progressive Web Apps',
                    'Browser Debugging',
                    'Figma to Code',
                    'UI Animation',
                ],
            ],
            [
                'path_prefix' => 'tech.software.devops',
                'root_name' => 'DevOps and Cloud Operations',
                'default_category' => 'domain',
                'contexts' => ['SaaS', 'Enterprise', 'Streaming', 'High Traffic'],
                'core_skills' => [
                    'Linux Administration',
                    'Git',
                    'CI/CD Pipelines',
                    'Docker',
                    'Kubernetes',
                    'Terraform',
                    'Ansible',
                    'AWS Architecture',
                    'Azure Cloud Services',
                    'Google Cloud Platform',
                    'Infrastructure as Code',
                    'Observability',
                    'Prometheus',
                    'Grafana',
                    'Nginx Administration',
                    'Security Hardening',
                    'Secrets Management',
                    'Disaster Recovery',
                    'Cost Optimization',
                    'SRE Practices',
                ],
            ],
            [
                'path_prefix' => 'tech.data',
                'root_name' => 'Data and AI',
                'default_category' => 'domain',
                'contexts' => ['Retail', 'Banking', 'Healthcare', 'Manufacturing'],
                'core_skills' => [
                    'SQL',
                    'Python for Data',
                    'R Programming',
                    'Data Modeling',
                    'ETL Pipelines',
                    'Data Warehousing',
                    'Apache Spark',
                    'Airflow Orchestration',
                    'Feature Engineering',
                    'Machine Learning',
                    'Deep Learning',
                    'Natural Language Processing',
                    'Computer Vision',
                    'MLOps',
                    'A/B Testing',
                    'Statistical Analysis',
                    'Tableau',
                    'Power BI',
                    'Experiment Tracking',
                    'Vector Databases',
                ],
            ],
            [
                'path_prefix' => 'business.finance',
                'root_name' => 'Finance Operations',
                'default_category' => 'domain',
                'contexts' => ['Corporate', 'Banking', 'Insurance', 'Investment'],
                'core_skills' => [
                    'Financial Accounting',
                    'Management Accounting',
                    'Budget Planning',
                    'Financial Forecasting',
                    'Corporate Valuation',
                    'Cash Flow Analysis',
                    'Investment Analysis',
                    'Risk Assessment',
                    'Internal Audit',
                    'IFRS Reporting',
                    'Tax Compliance',
                    'Treasury Management',
                    'Portfolio Management',
                    'Credit Analysis',
                    'Derivatives Basics',
                    'Fraud Detection',
                    'Financial Modeling',
                    'Excel for Finance',
                    'SAP Finance',
                    'Cost Control',
                ],
            ],
            [
                'path_prefix' => 'business.marketing',
                'root_name' => 'Marketing and Growth',
                'default_category' => 'domain',
                'contexts' => ['SaaS', 'D2C', 'Marketplace', 'Media'],
                'core_skills' => [
                    'Market Research',
                    'Brand Strategy',
                    'Content Marketing',
                    'Copywriting',
                    'SEO',
                    'SEM',
                    'Performance Marketing',
                    'Social Media Marketing',
                    'Email Marketing',
                    'CRM Campaigns',
                    'Marketing Automation',
                    'Conversion Rate Optimization',
                    'Web Analytics',
                    'Google Analytics',
                    'Affiliate Marketing',
                    'Influencer Marketing',
                    'Product Marketing',
                    'Customer Segmentation',
                    'Marketing Attribution',
                    'A/B Testing for Marketing',
                ],
            ],
            [
                'path_prefix' => 'business.sales',
                'root_name' => 'Sales Excellence',
                'default_category' => 'domain',
                'contexts' => ['B2B', 'B2C', 'Enterprise', 'Channel Sales'],
                'core_skills' => [
                    'Lead Generation',
                    'Prospecting',
                    'B2B Sales',
                    'B2C Sales',
                    'Consultative Selling',
                    'Sales Negotiation',
                    'Pipeline Management',
                    'CRM Management',
                    'Salesforce Administration',
                    'HubSpot CRM',
                    'Account Management',
                    'Key Account Planning',
                    'Sales Forecasting',
                    'Pre-Sales Discovery',
                    'Solution Presentation',
                    'Objection Handling',
                    'Closing Techniques',
                    'Customer Retention',
                    'Upselling and Cross-selling',
                    'Sales Enablement',
                ],
            ],
            [
                'path_prefix' => 'people.hr',
                'root_name' => 'Human Resources',
                'default_category' => 'soft_skill',
                'contexts' => ['Technology', 'Manufacturing', 'Healthcare', 'Retail'],
                'core_skills' => [
                    'Talent Sourcing',
                    'Interviewing Techniques',
                    'Competency Mapping',
                    'Workforce Planning',
                    'Onboarding Design',
                    'HRIS Administration',
                    'Payroll Operations',
                    'Compensation and Benefits',
                    'Employee Relations',
                    'Performance Management',
                    'Learning and Development',
                    'Succession Planning',
                    'Employer Branding',
                    'Labor Law Compliance',
                    'HR Analytics',
                    'Organizational Development',
                    'Change Management in HR',
                    'Diversity Equity Inclusion',
                    'Coaching for Managers',
                    'Conflict Resolution',
                ],
            ],
            [
                'path_prefix' => 'healthcare',
                'root_name' => 'Healthcare Delivery',
                'default_category' => 'domain',
                'contexts' => ['Hospital', 'Clinic', 'Telehealth', 'Pharma'],
                'core_skills' => [
                    'Patient Care Coordination',
                    'Clinical Documentation',
                    'Nursing Fundamentals',
                    'Medication Safety',
                    'Infection Prevention',
                    'Emergency Triage',
                    'Telemedicine Operations',
                    'Healthcare Compliance',
                    'Medical Coding',
                    'ICD 10 Coding',
                    'Electronic Health Records',
                    'Clinical Quality Assurance',
                    'Pharmacy Operations',
                    'Pharmacovigilance',
                    'Medical Device Handling',
                    'Clinical Research Support',
                    'Biostatistics Basics',
                    'Hospital Operations',
                    'Care Plan Management',
                    'Public Health Surveillance',
                ],
            ],
            [
                'path_prefix' => 'manufacturing',
                'root_name' => 'Manufacturing Operations',
                'default_category' => 'domain',
                'contexts' => ['Automotive', 'Electronics', 'FMCG', 'Heavy Industry'],
                'core_skills' => [
                    'Production Planning',
                    'Lean Manufacturing',
                    'Six Sigma',
                    'Quality Assurance',
                    'Statistical Process Control',
                    'Root Cause Analysis',
                    'Preventive Maintenance',
                    'Industrial Safety',
                    'Supply Chain Planning',
                    'Inventory Management',
                    'Procurement Operations',
                    'Warehouse Management',
                    'Logistics Coordination',
                    'Demand Forecasting',
                    'Manufacturing ERP',
                    'CNC Operations',
                    'Process Standardization',
                    'Vendor Management',
                    'OEE Monitoring',
                    'Continuous Improvement',
                ],
            ],
            [
                'path_prefix' => 'management_soft_skills',
                'root_name' => 'Management and Soft Skills',
                'default_category' => 'soft_skill',
                'contexts' => ['Operations', 'Product', 'Engineering', 'Consulting'],
                'core_skills' => [
                    'Leadership',
                    'Strategic Thinking',
                    'Stakeholder Management',
                    'Communication Skills',
                    'Presentation Skills',
                    'Negotiation',
                    'Decision Making',
                    'Problem Solving',
                    'Critical Thinking',
                    'Time Management',
                    'Priority Management',
                    'Project Management',
                    'Program Management',
                    'Agile Delivery',
                    'Scrum Facilitation',
                    'Conflict Management',
                    'Mentoring',
                    'Coaching',
                    'Team Building',
                    'Change Leadership',
                ],
            ],
        ];
    }

    /**
     * @return list<array{path_prefix:string,root_name:string,default_category:string}>
     */
    private function domainRoots(): array
    {
        return [
            ['path_prefix' => 'tech.software.backend', 'root_name' => 'Backend Development', 'default_category' => 'domain'],
            ['path_prefix' => 'tech.software.frontend', 'root_name' => 'Frontend Development', 'default_category' => 'domain'],
            ['path_prefix' => 'tech.software.devops', 'root_name' => 'DevOps and Cloud Operations', 'default_category' => 'domain'],
            ['path_prefix' => 'tech.data', 'root_name' => 'Data and AI', 'default_category' => 'domain'],
            ['path_prefix' => 'business.finance', 'root_name' => 'Finance Operations', 'default_category' => 'domain'],
            ['path_prefix' => 'business.marketing', 'root_name' => 'Marketing and Growth', 'default_category' => 'domain'],
            ['path_prefix' => 'business.sales', 'root_name' => 'Sales Excellence', 'default_category' => 'domain'],
            ['path_prefix' => 'people.hr', 'root_name' => 'Human Resources', 'default_category' => 'soft_skill'],
            ['path_prefix' => 'healthcare', 'root_name' => 'Healthcare Delivery', 'default_category' => 'domain'],
            ['path_prefix' => 'manufacturing', 'root_name' => 'Manufacturing Operations', 'default_category' => 'domain'],
            ['path_prefix' => 'management_soft_skills', 'root_name' => 'Management and Soft Skills', 'default_category' => 'soft_skill'],
        ];
    }

    /**
     * @return list<string>
     */
    private function loadEscoCanonicalSkillCatalog(): array
    {
        $catalogPath = database_path('seeders/data/esco_skills_en.json');
        if (! is_file($catalogPath)) {
            throw new \RuntimeException('Missing ESCO skill catalog at '.$catalogPath);
        }

        $decoded = json_decode((string) file_get_contents($catalogPath), true);

        if (! is_array($decoded)) {
            throw new \RuntimeException('Invalid ESCO skill catalog payload.');
        }

        return array_values(array_filter(array_map(
            fn (mixed $item): string => is_string($item) ? trim($item) : '',
            $decoded,
        )));
    }

    private function inferDomainPrefix(string $canonicalName): string
    {
        $name = Str::lower($canonicalName);

        $keywordMap = [
            'tech.software.backend' => ['api', 'backend', 'server', 'database', 'sql', 'postgres', 'mysql', 'php', 'java', 'c#', 'dotnet', 'laravel', 'spring', 'symfony', 'node', 'graphql', 'microservice'],
            'tech.software.frontend' => ['frontend', 'front end', 'ui', 'ux', 'css', 'html', 'javascript', 'typescript', 'react', 'vue', 'angular', 'web design', 'web development'],
            'tech.software.devops' => ['devops', 'cloud', 'kubernetes', 'docker', 'terraform', 'ansible', 'linux', 'network', 'infrastructure', 'ci/cd', 'observability', 'monitoring', 'aws', 'azure', 'google cloud'],
            'tech.data' => ['data', 'machine learning', 'deep learning', 'artificial intelligence', 'analytics', 'statistics', 'etl', 'bi', 'tableau', 'power bi', 'python', 'spark', 'airflow', 'nlp'],
            'business.finance' => ['finance', 'financial', 'accounting', 'budget', 'tax', 'audit', 'valuation', 'credit', 'treasury', 'ifrs', 'cash flow', 'investment', 'fraud'],
            'business.marketing' => ['marketing', 'seo', 'sem', 'campaign', 'brand', 'content', 'social media', 'email marketing', 'advertising', 'market research', 'customer segmentation'],
            'business.sales' => ['sales', 'sell', 'prospect', 'lead generation', 'account management', 'crm', 'negotiation', 'closing', 'upselling', 'cross-selling'],
            'people.hr' => ['human resources', 'hr ', 'recruitment', 'talent', 'onboarding', 'payroll', 'compensation', 'benefits', 'labor law', 'employee relations', 'workforce'],
            'healthcare' => ['healthcare', 'clinical', 'patient', 'nursing', 'medical', 'pharma', 'hospital', 'medic', 'diagnose', 'therapy', 'pharmacovigilance'],
            'manufacturing' => ['manufacturing', 'production', 'lean', 'six sigma', 'quality control', 'supply chain', 'inventory', 'warehouse', 'procurement', 'logistics', 'cnc', 'industrial'],
        ];

        foreach ($keywordMap as $domainPrefix => $keywords) {
            foreach ($keywords as $keyword) {
                if (str_contains($name, $keyword)) {
                    return $domainPrefix;
                }
            }
        }

        return 'management_soft_skills';
    }

    /**
     * @return list<array{id:string,canonical_name:string,slug:string,path:string,status:string,category:string,domain_prefix:string,base_key:string,level:string,is_root:bool}>
     */
    private function seedSkills(): array
    {
        $domains = $this->domainRoots();
        $domainLookup = [];
        $skillsToInsert = [];
        $skillRecords = [];
        $timestamp = now();

        foreach ($domains as $domain) {
            $rootCanonical = $domain['root_name'];
            $rootPath = $this->makeUniquePath($domain['path_prefix']);

            $rootSkill = $this->buildSkillPayload(
                canonicalName: $rootCanonical,
                path: $rootPath,
                domainPrefix: $domain['path_prefix'],
                baseKey: $domain['path_prefix'].'|root',
                level: 'root',
                isRoot: true,
                defaultCategory: $domain['default_category'],
                index: count($skillRecords),
                timestamp: $timestamp,
            );

            $skillsToInsert[] = $rootSkill['attributes'];
            $skillRecords[] = $rootSkill['record'];
            $domainLookup[$domain['path_prefix']] = $domain;
        }

        $catalog = $this->loadEscoCanonicalSkillCatalog();
        $maxCatalogItems = max(0, self::TARGET_SKILL_COUNT - count($skillRecords));
        $catalog = array_slice($catalog, 0, $maxCatalogItems);

        foreach ($catalog as $catalogIndex => $canonicalName) {
            $normalizedCanonicalName = $this->normalizeCanonicalName($canonicalName);
            if ($normalizedCanonicalName === '') {
                continue;
            }

            $domainPrefix = $this->inferDomainPrefix($normalizedCanonicalName);
            $domainDefaults = $domainLookup[$domainPrefix] ?? [
                'path_prefix' => 'management_soft_skills',
                'default_category' => 'soft_skill',
            ];

            $baseSegment = $this->normalizePathSegment($normalizedCanonicalName);
            $basePath = $this->makeUniquePath($domainPrefix.'.'.$baseSegment);
            $baseKey = $domainPrefix.'|'.$baseSegment;

            $skill = $this->buildSkillPayload(
                canonicalName: $normalizedCanonicalName,
                path: $basePath,
                domainPrefix: $domainPrefix,
                baseKey: $baseKey,
                level: 'base',
                isRoot: false,
                defaultCategory: $domainDefaults['default_category'],
                index: count($skillRecords) + $catalogIndex,
                timestamp: $timestamp,
            );

            $skillsToInsert[] = $skill['attributes'];
            $skillRecords[] = $skill['record'];
        }

        foreach ($skillsToInsert as &$skillToInsert) {
            if (is_array($skillToInsert['metadata'])) {
                $skillToInsert['metadata'] = json_encode($skillToInsert['metadata'], JSON_UNESCAPED_UNICODE);
            }
        }

        unset($skillToInsert);

        foreach (array_chunk($skillsToInsert, 250) as $chunk) {
            DB::table('skills')->insert($chunk);
        }

        return $skillRecords;
    }

    /**
     * @param  list<array{id:string,canonical_name:string,slug:string,path:string,status:string,category:string,domain_prefix:string,base_key:string,level:string,is_root:bool}>  $skills
     */
    private function seedAliases(array $skills): void
    {
        $rows = [];
        $timestamp = now();
        $surfaceKeyRegistry = [];

        foreach ($skills as $index => $skill) {
            $aliasCandidates = $this->buildAliasCandidates($skill['canonical_name']);
            $aliasTargetCount = 1 + ($index % 3);
            $added = 0;

            foreach ($aliasCandidates as $candidateIndex => $alias) {
                if ($added >= $aliasTargetCount) {
                    break;
                }

                $locale = 'en';
                if ($candidateIndex > 0 && ($index + $candidateIndex) % 7 === 0) {
                    $locale = 'en-US';
                } elseif ($candidateIndex > 0 && ($index + $candidateIndex) % 11 === 0) {
                    $locale = 'en-GB';
                }

                $surfaceKey = $skill['id'].'|'.$locale.'|'.$this->normalizeSurfaceForm($alias);
                if (isset($surfaceKeyRegistry[$surfaceKey])) {
                    continue;
                }

                $source = match (($index + $candidateIndex) % 9) {
                    0, 1, 2, 3 => 'curated',
                    4, 5, 6 => 'llm_discovered',
                    default => 'user_submitted',
                };

                $aliasAttributes = [
                    'id' => (string) Str::uuid(),
                    'skill_id' => $skill['id'],
                    'surface_form' => $alias,
                    'locale' => $locale,
                    'source' => $source,
                    'is_primary' => $candidateIndex === 0 && $index % 3 === 0,
                    'alias_embedding' => null,
                    'created_at' => $timestamp,
                    'updated_at' => $timestamp,
                ];

                $rows[] = $aliasAttributes;
                $surfaceKeyRegistry[$surfaceKey] = true;
                $added++;
            }
        }

        foreach (array_chunk($rows, 500) as $chunk) {
            DB::table('skill_aliases')->insert($chunk);
        }
    }

    /**
     * @param  list<array{id:string,canonical_name:string,slug:string,path:string,status:string,category:string,domain_prefix:string,base_key:string,level:string,is_root:bool}>  $skills
     */
    private function seedRelationships(array $skills): void
    {
        $pathToSkillId = [];
        $baseSkillIdByKey = [];
        $fundamentalsSkillIdByBaseKey = [];
        $activeSkillIdsByDomain = [];

        foreach ($skills as $skill) {
            $pathToSkillId[$skill['path']] = $skill['id'];

            if ($skill['level'] === 'base') {
                $baseSkillIdByKey[$skill['base_key']] = $skill['id'];
            }

            if ($skill['level'] === 'fundamentals') {
                $fundamentalsSkillIdByBaseKey[$skill['base_key']] = $skill['id'];
            }

            if ($skill['status'] === 'active' && ! $skill['is_root']) {
                $activeSkillIdsByDomain[$skill['domain_prefix']][] = $skill['id'];
            }
        }

        $rows = [];
        $relationshipCounter = 0;
        $timestamp = now();
        $flushRows = function () use (&$rows): void {
            if ($rows === []) {
                return;
            }

            DB::table('skill_relationships')->insertOrIgnore($rows);
            $rows = [];
        };

        foreach ($skills as $skill) {
            $parentSkillId = $this->findClosestParentSkillId($skill['path'], $pathToSkillId);
            if ($parentSkillId === null) {
                continue;
            }

            $this->addRelationship(
                rows: $rows,
                sourceSkillId: $parentSkillId,
                targetSkillId: $skill['id'],
                relationshipType: 'parent_of',
                counter: $relationshipCounter,
                timestamp: $timestamp,
            );

            $this->addRelationship(
                rows: $rows,
                sourceSkillId: $skill['id'],
                targetSkillId: $parentSkillId,
                relationshipType: 'child_of',
                counter: $relationshipCounter,
                timestamp: $timestamp,
            );

            if (count($rows) >= 1000) {
                $flushRows();
            }
        }

        foreach ($activeSkillIdsByDomain as $domainSkillIds) {
            $domainSkillCount = count($domainSkillIds);
            if ($domainSkillCount < 2) {
                continue;
            }

            foreach ($domainSkillIds as $index => $sourceSkillId) {
                $relatedLimit = 2 + ($index % 3);

                for ($offset = 1; $offset <= $relatedLimit; $offset++) {
                    $targetSkillId = $domainSkillIds[($index + ($offset * 2)) % $domainSkillCount];

                    $this->addRelationship(
                        rows: $rows,
                        sourceSkillId: $sourceSkillId,
                        targetSkillId: $targetSkillId,
                        relationshipType: 'related_to',
                        counter: $relationshipCounter,
                        timestamp: $timestamp,
                    );

                    if (count($rows) >= 1000) {
                        $flushRows();
                    }
                }
            }
        }

        foreach ($skills as $skill) {
            if (! in_array($skill['level'], ['advanced', 'applied', 'specialization', 'practitioner'], true)) {
                continue;
            }

            $baseSkillId = $baseSkillIdByKey[$skill['base_key']] ?? null;
            if ($baseSkillId !== null) {
                $this->addRelationship(
                    rows: $rows,
                    sourceSkillId: $skill['id'],
                    targetSkillId: $baseSkillId,
                    relationshipType: 'requires',
                    counter: $relationshipCounter,
                    timestamp: $timestamp,
                );
            }

            $fundamentalsSkillId = $fundamentalsSkillIdByBaseKey[$skill['base_key']] ?? null;
            if ($fundamentalsSkillId !== null && $fundamentalsSkillId !== $baseSkillId) {
                $this->addRelationship(
                    rows: $rows,
                    sourceSkillId: $skill['id'],
                    targetSkillId: $fundamentalsSkillId,
                    relationshipType: 'requires',
                    counter: $relationshipCounter,
                    timestamp: $timestamp,
                );
            }

            if ($skill['status'] === 'merged' && $baseSkillId !== null && $baseSkillId !== $skill['id']) {
                $this->addRelationship(
                    rows: $rows,
                    sourceSkillId: $skill['id'],
                    targetSkillId: $baseSkillId,
                    relationshipType: 'superseded_by',
                    counter: $relationshipCounter,
                    timestamp: $timestamp,
                );
            }

            if (count($rows) >= 1000) {
                $flushRows();
            }
        }

        $flushRows();
    }

    /**
     * @param  list<array{id:string,canonical_name:string,slug:string,path:string,status:string,category:string,domain_prefix:string,base_key:string,level:string,is_root:bool}>  $skills
     */
    private function seedCoOccurrences(array $skills): void
    {
        $activeSkillIdsByDomain = [];

        foreach ($skills as $skill) {
            if ($skill['status'] !== 'active' || $skill['is_root']) {
                continue;
            }

            $activeSkillIdsByDomain[$skill['domain_prefix']][] = $skill['id'];
        }

        if ($activeSkillIdsByDomain === []) {
            return;
        }

        $domainPrefixes = array_keys($activeSkillIdsByDomain);
        $domainCount = count($domainPrefixes);
        $pairKeys = [];
        $rows = [];
        $timestamp = now();
        $now = CarbonImmutable::now();
        $flushRows = function () use (&$rows): void {
            if ($rows === []) {
                return;
            }

            DB::table('skill_co_occurrences')->insertOrIgnore($rows);
            $rows = [];
        };

        foreach ($domainPrefixes as $domainIndex => $domainPrefix) {
            $domainSkillIds = $activeSkillIdsByDomain[$domainPrefix];
            $domainSkillCount = count($domainSkillIds);

            if ($domainSkillCount < 2) {
                continue;
            }

            $nextDomainPrefix = $domainPrefixes[($domainIndex + 1) % $domainCount];
            $nextDomainSkillIds = $activeSkillIdsByDomain[$nextDomainPrefix] ?? [];
            $nextDomainCount = count($nextDomainSkillIds);

            foreach ($domainSkillIds as $skillIndex => $skillAId) {
                $inDomainLinks = min($domainSkillCount - 1, 2 + ($skillIndex % 4));

                for ($step = 1; $step <= $inDomainLinks; $step++) {
                    $targetIndex = ($skillIndex + ($step * 7)) % $domainSkillCount;
                    $skillBId = $domainSkillIds[$targetIndex];
                    $seed = (($domainIndex + 1) * 100000) + (($skillIndex + 1) * 17) + $step;

                    $this->addCoOccurrence(
                        rows: $rows,
                        pairKeys: $pairKeys,
                        skillAId: $skillAId,
                        skillBId: $skillBId,
                        seed: $seed,
                        now: $now,
                        timestamp: $timestamp,
                    );

                    if (count($rows) >= 1000) {
                        $flushRows();
                    }
                }

                if ($nextDomainCount > 0 && $skillIndex % 3 === 0) {
                    $crossIndex = (($skillIndex + 1) * 5 + $domainIndex) % $nextDomainCount;
                    $crossSkillId = $nextDomainSkillIds[$crossIndex];
                    $seed = (($domainIndex + 1) * 200000) + (($skillIndex + 1) * 13);

                    $this->addCoOccurrence(
                        rows: $rows,
                        pairKeys: $pairKeys,
                        skillAId: $skillAId,
                        skillBId: $crossSkillId,
                        seed: $seed,
                        now: $now,
                        timestamp: $timestamp,
                    );

                    if (count($rows) >= 1000) {
                        $flushRows();
                    }
                }
            }
        }

        $flushRows();
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

    /**
     * @param  array<string, mixed>  $skillByPath
     */
    private function findClosestParentSkillId(string $path, array $skillByPath): ?string
    {
        $segments = explode('.', $path);

        while (count($segments) > 1) {
            array_pop($segments);
            $candidatePath = implode('.', $segments);

            if (isset($skillByPath[$candidatePath])) {
                return $skillByPath[$candidatePath];
            }
        }

        return null;
    }

    /**
     * @param  list<array<string, mixed>>  $rows
     */
    private function addRelationship(
        array &$rows,
        string $sourceSkillId,
        string $targetSkillId,
        string $relationshipType,
        int &$counter,
        CarbonInterface $timestamp,
    ): void {
        if ($sourceSkillId === $targetSkillId) {
            return;
        }

        $attributes = $this->relationshipAttributes($relationshipType, $counter);

        $rows[] = [
            'id' => (string) Str::uuid(),
            'source_skill_id' => $sourceSkillId,
            'target_skill_id' => $targetSkillId,
            'relationship_type' => $relationshipType,
            'confidence' => $attributes['confidence'],
            'weight' => $attributes['weight'],
            'provenance' => $attributes['provenance'],
            'status' => $attributes['status'],
            'created_at' => $timestamp,
            'updated_at' => $timestamp,
        ];

        $counter++;
    }

    /**
     * @return array{confidence:float,weight:float,provenance:string,status:string}
     */
    private function relationshipAttributes(string $relationshipType, int $counter): array
    {
        if (in_array($relationshipType, ['parent_of', 'child_of'], true)) {
            return [
                'confidence' => $this->scaledFloat(0.9, 1.0, $counter),
                'weight' => $this->scaledFloat(0.85, 1.0, $counter + 7),
                'provenance' => 'human_curated',
                'status' => $counter % 29 === 0 ? 'pending_review' : 'active',
            ];
        }

        if ($relationshipType === 'requires') {
            return [
                'confidence' => $this->scaledFloat(0.75, 0.96, $counter),
                'weight' => $this->scaledFloat(0.7, 0.95, $counter + 11),
                'provenance' => $counter % 4 === 0 ? 'human_curated' : 'llm_predicted',
                'status' => $counter % 21 === 0 ? 'pending_review' : 'active',
            ];
        }

        if ($relationshipType === 'related_to') {
            $provenance = match ($counter % 5) {
                0 => 'human_curated',
                1, 2 => 'llm_predicted',
                3 => 'embedding_similarity',
                default => 'empirical',
            };

            return [
                'confidence' => $this->scaledFloat(0.6, 0.9, $counter),
                'weight' => $this->scaledFloat(0.55, 0.9, $counter + 17),
                'provenance' => $provenance,
                'status' => $counter % 17 === 0 ? 'pending_review' : 'active',
            ];
        }

        return [
            'confidence' => $this->scaledFloat(0.9, 1.0, $counter),
            'weight' => $this->scaledFloat(0.8, 0.96, $counter + 13),
            'provenance' => 'human_curated',
            'status' => 'active',
        ];
    }

    /**
     * @param  list<array<string, mixed>>  $rows
     * @param  array<string, bool>  $pairKeys
     */
    private function addCoOccurrence(
        array &$rows,
        array &$pairKeys,
        string $skillAId,
        string $skillBId,
        int $seed,
        CarbonImmutable $now,
        CarbonInterface $timestamp,
    ): void {
        if ($skillAId === $skillBId) {
            return;
        }

        if (strcmp($skillAId, $skillBId) > 0) {
            [$skillAId, $skillBId] = [$skillBId, $skillAId];
        }

        $pairKey = $skillAId.'|'.$skillBId;
        if (isset($pairKeys[$pairKey])) {
            return;
        }

        $coOccurrenceCount = 2 + ($seed % 24);

        $rows[] = [
            'id' => (string) Str::uuid(),
            'skill_a_id' => $skillAId,
            'skill_b_id' => $skillBId,
            'co_occurrence_count' => $coOccurrenceCount,
            'source_type_counts' => json_encode($this->buildSourceTypeCounts($coOccurrenceCount, $seed), JSON_UNESCAPED_UNICODE),
            'last_seen_at' => $now->subDays($seed % 330),
            'created_at' => $timestamp,
        ];

        $pairKeys[$pairKey] = true;
    }

    /**
     * @return array{cv:int,jd:int,course:int}
     */
    private function buildSourceTypeCounts(int $totalCount, int $seed): array
    {
        $base = intdiv($totalCount, 3);
        $remainder = $totalCount % 3;
        $counts = ['cv' => $base, 'jd' => $base, 'course' => $base];
        $orders = [
            ['cv', 'jd', 'course'],
            ['jd', 'course', 'cv'],
            ['course', 'cv', 'jd'],
        ];
        $order = $orders[$seed % count($orders)];

        for ($index = 0; $index < $remainder; $index++) {
            $counts[$order[$index]]++;
        }

        return $counts;
    }

    /**
     * @return array{attributes:array<string,mixed>,record:array{id:string,canonical_name:string,slug:string,path:string,status:string,category:string,domain_prefix:string,base_key:string,level:string,is_root:bool}}
     */
    private function buildSkillPayload(
        string $canonicalName,
        string $path,
        string $domainPrefix,
        string $baseKey,
        string $level,
        bool $isRoot,
        string $defaultCategory,
        int $index,
        \Illuminate\Support\Carbon $timestamp,
    ): array {
        $id = (string) Str::uuid();
        $externalId = sprintf('SK-%06d', $this->externalIdCounter++);
        $slug = $this->makeUniqueSlug($canonicalName);
        $category = $this->determineCategory($canonicalName, $defaultCategory, $domainPrefix);
        $status = $this->determineSkillStatus($index, $isRoot, $level);

        $attributes = [
            'id' => $id,
            'external_id' => $externalId,
            'canonical_name' => $canonicalName,
            'slug' => $slug,
            'description' => sprintf('%s competency in %s.', $canonicalName, str_replace('.', ' > ', $domainPrefix)),
            'status' => $status,
            'category' => $category,
            'path' => $path,
            'embedding' => null,
            'source' => match ($index % 4) {
                0, 1 => 'curator',
                2 => 'llm_discovered',
                default => 'import',
            },
            'metadata' => json_encode([
                'domain_prefix' => $domainPrefix,
                'base_key' => $baseKey,
                'level' => $level,
                'is_root' => $isRoot,
                'seed_batch' => 'skill_graph_v2_large_scale',
            ], JSON_UNESCAPED_UNICODE),
        ];

        $attributes['created_at'] = $timestamp;
        $attributes['updated_at'] = $timestamp;

        return [
            'attributes' => $attributes,
            'record' => [
                'id' => $id,
                'canonical_name' => $canonicalName,
                'slug' => $slug,
                'path' => $path,
                'status' => $status,
                'category' => $category,
                'domain_prefix' => $domainPrefix,
                'base_key' => $baseKey,
                'level' => $level,
                'is_root' => $isRoot,
            ],
        ];
    }

    private function determineCategory(string $canonicalName, string $defaultCategory, string $domainPrefix): string
    {
        if (str_starts_with($domainPrefix, 'management_soft_skills') || str_starts_with($domainPrefix, 'people.hr')) {
            return 'soft_skill';
        }

        $normalized = Str::lower($canonicalName);

        if (preg_match('/\b(cpa|cfa|acca|certification|certified|pmp|itil|iso|rn)\b/', $normalized) === 1) {
            return 'certification';
        }

        if (preg_match('/\b(agile|scrum|kanban|lean|six sigma|framework|method|protocol|sop)\b/', $normalized) === 1) {
            return 'methodology';
        }

        if (preg_match('/\b(php|python|javascript|typescript|kotlin|swift|java|sql|r programming|english)\b/', $normalized) === 1) {
            return 'language';
        }

        if (preg_match('/\b(laravel|symfony|react|vue|angular|next\.js|docker|kubernetes|terraform|ansible|spark|airflow|tableau|power bi|salesforce|hubspot|figma|sap|nginx|grafana|prometheus)\b/', $normalized) === 1) {
            return 'tool';
        }

        return $defaultCategory;
    }

    private function determineSkillStatus(int $index, bool $isRoot, string $level): string
    {
        if ($isRoot) {
            return 'active';
        }

        if ($level === 'advanced' && $index % 8 === 0) {
            return 'candidate';
        }

        if ($index % 53 === 0) {
            return 'merged';
        }

        if ($index % 29 === 0) {
            return 'deprecated';
        }

        if ($index % 7 === 0) {
            return 'candidate';
        }

        return 'active';
    }

    /**
     * @param  list<string>  $contexts
     * @return array{name:string,suffix:string,level:string}
     */
    private function buildVariantNameAndSuffix(string $baseCanonicalName, array $contexts, int $variantRound, int $seedIndex): array
    {
        $context = $contexts[($variantRound + $seedIndex) % count($contexts)];

        $focusAreas = [
            'Implementation',
            'Optimization',
            'Architecture',
            'Governance',
            'Automation',
            'Troubleshooting',
            'Analytics',
            'Integration',
            'Security',
            'Performance',
            'Compliance',
            'Planning',
            'Operations',
            'Monitoring',
            'Risk Management',
            'Quality Assurance',
        ];
        $proficiencyLevels = ['Intermediate', 'Advanced', 'Expert', 'Strategic', 'Operational'];

        $focus = $focusAreas[(($variantRound * 3) + $seedIndex) % count($focusAreas)];
        $secondaryFocus = $focusAreas[(($variantRound * 5) + $seedIndex + 7) % count($focusAreas)];
        $proficiency = $proficiencyLevels[(($variantRound * 2) + $seedIndex) % count($proficiencyLevels)];

        $patternIndex = $variantRound % 20;

        return match ($patternIndex) {
            0 => ['name' => "Fundamentals of {$baseCanonicalName}", 'suffix' => 'fundamentals_'.$this->normalizePathSegment($context), 'level' => 'fundamentals'],
            1 => ['name' => "Introduction to {$baseCanonicalName}", 'suffix' => 'introduction_'.$this->normalizePathSegment($context), 'level' => 'introduction'],
            2 => ['name' => "Advanced {$baseCanonicalName}", 'suffix' => 'advanced_'.$this->normalizePathSegment($focus), 'level' => 'advanced'],
            3 => ['name' => "Applied {$baseCanonicalName}", 'suffix' => 'applied_'.$this->normalizePathSegment($focus), 'level' => 'applied'],
            4 => ['name' => "{$baseCanonicalName} for {$context}", 'suffix' => 'for_'.$this->normalizePathSegment($context), 'level' => 'specialization'],
            5 => ['name' => "{$baseCanonicalName} {$focus}", 'suffix' => $this->normalizePathSegment($focus), 'level' => 'practitioner'],
            6 => ['name' => "{$focus} in {$baseCanonicalName}", 'suffix' => $this->normalizePathSegment($focus).'_in_'.$this->normalizePathSegment($context), 'level' => 'practitioner'],
            7 => ['name' => "{$baseCanonicalName} {$secondaryFocus}", 'suffix' => $this->normalizePathSegment($secondaryFocus), 'level' => 'practitioner'],
            8 => ['name' => "{$proficiency} {$baseCanonicalName} Practice", 'suffix' => $this->normalizePathSegment($proficiency).'_practice', 'level' => 'advanced'],
            9 => ['name' => "{$baseCanonicalName} Strategy", 'suffix' => 'strategy_'.$this->normalizePathSegment($context), 'level' => 'specialization'],
            10 => ['name' => "{$baseCanonicalName} Operations", 'suffix' => 'operations_'.$this->normalizePathSegment($context), 'level' => 'practitioner'],
            11 => ['name' => "{$baseCanonicalName} Governance", 'suffix' => 'governance_'.$this->normalizePathSegment($context), 'level' => 'specialization'],
            12 => ['name' => "{$baseCanonicalName} Performance Optimization", 'suffix' => 'performance_optimization_'.$this->normalizePathSegment($context), 'level' => 'practitioner'],
            13 => ['name' => "{$baseCanonicalName} Security and Compliance", 'suffix' => 'security_compliance_'.$this->normalizePathSegment($context), 'level' => 'specialization'],
            14 => ['name' => "{$baseCanonicalName} Integration", 'suffix' => 'integration_'.$this->normalizePathSegment($context), 'level' => 'practitioner'],
            15 => ['name' => "{$baseCanonicalName} Automation", 'suffix' => 'automation_'.$this->normalizePathSegment($context), 'level' => 'practitioner'],
            16 => ['name' => "{$baseCanonicalName} Monitoring and Troubleshooting", 'suffix' => 'monitoring_troubleshooting_'.$this->normalizePathSegment($context), 'level' => 'practitioner'],
            17 => ['name' => "{$baseCanonicalName} Risk Management", 'suffix' => 'risk_management_'.$this->normalizePathSegment($context), 'level' => 'specialization'],
            18 => ['name' => "{$context} {$baseCanonicalName} Standards", 'suffix' => $this->normalizePathSegment($context).'_standards', 'level' => 'specialization'],
            default => ['name' => "{$baseCanonicalName} Quality Assurance", 'suffix' => 'quality_assurance_'.$this->normalizePathSegment($context), 'level' => 'practitioner'],
        };
    }

    /**
     * @return list<string>
     */
    private function buildAliasCandidates(string $canonicalName): array
    {
        $candidates = [];

        $stripped = preg_replace('/^(Fundamentals of|Introduction to|Advanced|Applied)\s+/i', '', $canonicalName) ?? $canonicalName;
        if ($stripped !== $canonicalName) {
            $candidates[] = $stripped;
        }

        $acronym = $this->acronymFor($canonicalName);
        if ($acronym !== null) {
            $candidates[] = $acronym;
        }

        $candidates[] = str_replace('&', 'and', $canonicalName);
        $candidates[] = str_replace('-', ' ', $canonicalName);

        if (! str_contains(Str::lower($stripped), 'skill')) {
            $candidates[] = $stripped.' Skill';
        }

        if (! str_contains(Str::lower($stripped), 'competency')) {
            $candidates[] = $stripped.' Competency';
        }

        if (str_contains(Str::lower($canonicalName), ' and ')) {
            $candidates[] = str_ireplace(' and ', ' & ', $canonicalName);
        }

        $uniqueCandidates = [];
        $seen = [];

        foreach ($candidates as $candidate) {
            $candidate = trim($candidate);
            if ($candidate === '') {
                continue;
            }

            if (Str::lower($candidate) === Str::lower($canonicalName)) {
                continue;
            }

            $key = Str::lower($candidate);
            if (isset($seen[$key])) {
                continue;
            }

            $uniqueCandidates[] = $candidate;
            $seen[$key] = true;
        }

        if ($uniqueCandidates === []) {
            return [$canonicalName.' Skill'];
        }

        return $uniqueCandidates;
    }

    private function acronymFor(string $name): ?string
    {
        $segments = preg_split('/[^A-Za-z0-9]+/', $name) ?: [];
        $segments = array_values(array_filter($segments));

        if (count($segments) < 2) {
            return null;
        }

        $letters = '';
        foreach ($segments as $segment) {
            $letters .= Str::upper(Str::substr($segment, 0, 1));
        }

        $letters = preg_replace('/[^A-Z0-9]/', '', $letters) ?? '';

        if (strlen($letters) < 2 || strlen($letters) > 6) {
            return null;
        }

        return $letters;
    }

    private function normalizeCanonicalName(string $canonicalName): string
    {
        return trim(preg_replace('/\s+/', ' ', $canonicalName) ?? $canonicalName);
    }

    private function makeUniqueSlug(string $canonicalName): string
    {
        $baseSlug = Str::slug($canonicalName);
        if ($baseSlug === '') {
            $baseSlug = 'skill';
        }

        $slug = $baseSlug;
        $counter = 2;

        while (isset($this->usedSlugs[$slug])) {
            $slug = $baseSlug.'-'.$counter;
            $counter++;
        }

        $this->usedSlugs[$slug] = true;

        return $slug;
    }

    private function makeUniquePath(string $path): string
    {
        $segments = array_values(array_filter(explode('.', $path), static fn (string $segment): bool => $segment !== ''));
        $normalizedSegments = array_map(fn (string $segment): string => $this->normalizePathSegment($segment), $segments);
        $normalizedPath = implode('.', $normalizedSegments);

        if (! isset($this->usedPaths[$normalizedPath])) {
            $this->usedPaths[$normalizedPath] = 1;

            return $normalizedPath;
        }

        $counter = ++$this->usedPaths[$normalizedPath];

        return $normalizedPath.'_'.Str::padLeft((string) $counter, 2, '0');
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
            $normalized = 'node';
        }

        if (preg_match('/^[0-9]/', $normalized) === 1) {
            $normalized = 'n_'.$normalized;
        }

        if (strlen($normalized) > 60) {
            $normalized = substr($normalized, 0, 60);
            $normalized = rtrim($normalized, '_');
        }

        if ($normalized === '') {
            return 'node';
        }

        return $normalized;
    }

    private function normalizeSurfaceForm(string $surfaceForm): string
    {
        return Str::lower(trim(preg_replace('/\s+/', ' ', $surfaceForm) ?? $surfaceForm));
    }

    private function scaledFloat(float $min, float $max, int $seed): float
    {
        $position = (($seed * 17) % 100) / 100;

        return round($min + (($max - $min) * $position), 4);
    }
}
