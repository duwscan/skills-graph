<?php

namespace App\Ai\Agents;

use Chimit\Prompt;
use Illuminate\Contracts\JsonSchema\JsonSchema;
use Laravel\Ai\Attributes\MaxTokens;
use Laravel\Ai\Attributes\Temperature;
use Laravel\Ai\Contracts\Agent;
use Laravel\Ai\Contracts\HasStructuredOutput;
use Laravel\Ai\Promptable;
use Stringable;

#[MaxTokens(16384)]
#[Temperature(0.5)]
class SkillGenerator implements Agent, HasStructuredOutput
{
    use Promptable;

    /**
     * @param  list<string>  $existingSkills  Canonical names already generated (for continuation-aware batching)
     */
    public function __construct(
        public string $domain = '',
        public string $category = '',
        public string $subcategory = '',
        public array $existingSkills = [],
    ) {}

    /**
     * Get the instructions that the agent should follow.
     */
    public function instructions(): Stringable|string
    {
        $contextBlock = '';

        if ($this->domain !== '' || $this->category !== '' || $this->subcategory !== '') {
            $parts = array_filter([
                $this->domain !== '' ? "Domain: {$this->domain}" : null,
                $this->category !== '' ? "Category: {$this->category}" : null,
                $this->subcategory !== '' ? "Subcategory: {$this->subcategory}" : null,
            ]);
            $contextBlock = "Current taxonomy context:\n".implode("\n", $parts)."\n\n";
        }

        $existingBlock = '';

        if ($this->existingSkills !== []) {
            $existingJson = json_encode($this->existingSkills, JSON_UNESCAPED_UNICODE);
            $existingBlock = <<<EXISTING

Already generated canonical skills (do NOT repeat or paraphrase):
{$existingJson}

If a new concept is too similar to an existing skill, skip it. Return only net-new skills.
EXISTING;
        }

        return Prompt::get('taxonomy/generate-skill-batch', [
            'contextBlock' => $contextBlock,
            'existingBlock' => $existingBlock,
        ]);
    }

    /**
     * Define the structured output schema for generated skills.
     */
    public function schema(JsonSchema $schema): array
    {
        return [
            'domain' => $schema->string()->required(),
            'category' => $schema->string()->required(),
            'subcategory' => $schema->string()->required(),
            'skills' => $schema->array()
                ->items(
                    $schema->object([
                        'canonical_name' => $schema->string()->required(),
                        'aliases' => $schema->array()
                            ->items($schema->string())
                            ->required(),
                        'skill_type' => $schema->string()->required(),
                        'description' => $schema->string()->required(),
                        'parent_skill' => $schema->string()->nullable(),
                        'related_skills' => $schema->array()
                            ->items($schema->string())
                            ->required(),
                        'keywords' => $schema->array()
                            ->items($schema->string())
                            ->required(),
                        'common_roles' => $schema->array()
                            ->items($schema->string())
                            ->required(),
                        'language' => $schema->string()->required(),
                        'status' => $schema->string()->required(),
                    ])->withoutAdditionalProperties()
                )
                ->required(),
        ];
    }
}
