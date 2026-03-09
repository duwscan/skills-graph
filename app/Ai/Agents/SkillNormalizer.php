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

class SkillNormalizer implements Agent, HasStructuredOutput
{
    use Promptable;

    /**
     * HTTP request timeout in seconds. 0 = no limit.
     */
    public function timeout(): int
    {
        return (int) config('ai.request_timeout', 0);
    }

    /**
     * Get the instructions that the agent should follow.
     */
    public function instructions(): Stringable|string
    {
        return Prompt::get('taxonomy/normalize-skills');
    }

    /**
     * Define the structured output schema for normalization results.
     */
    public function schema(JsonSchema $schema): array
    {
        return [
            'merged_skills' => $schema->array()
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
                        'domain' => $schema->string()->required(),
                        'category' => $schema->string()->required(),
                        'subcategory' => $schema->string()->required(),
                        'language' => $schema->string()->required(),
                        'status' => $schema->string()->required(),
                    ])->withoutAdditionalProperties()
                )
                ->required(),
            'removed_duplicates' => $schema->array()
                ->items(
                    $schema->object([
                        'removed' => $schema->string()->required(),
                        'merged_into' => $schema->string()->required(),
                        'reason' => $schema->string()->required(),
                    ])->withoutAdditionalProperties()
                )
                ->required(),
        ];
    }
}
