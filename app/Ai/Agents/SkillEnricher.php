<?php

namespace App\Ai\Agents;

use Chimit\Prompt;
use Illuminate\Contracts\JsonSchema\JsonSchema;
use Laravel\Ai\Contracts\Agent;
use Laravel\Ai\Contracts\HasStructuredOutput;
use Laravel\Ai\Promptable;
use Stringable;

class SkillEnricher implements Agent, HasStructuredOutput
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
        return Prompt::get('taxonomy/enrich-skill-metadata');
    }

    /**
     * Define the structured output schema for enriched skills.
     */
    public function schema(JsonSchema $schema): array
    {
        return [
            'skills' => $schema->array()
                ->items(
                    $schema->object([
                        'canonical_name' => $schema->string()->required(),
                        'aliases' => $schema->array()
                            ->items($schema->string())
                            ->required(),
                        'description' => $schema->string()->required(),
                        'parent_skill' => $schema->string()->required(),
                        'related_skills' => $schema->array()
                            ->items($schema->string())
                            ->required(),
                        'keywords' => $schema->array()
                            ->items($schema->string())
                            ->required(),
                        'common_roles' => $schema->array()
                            ->items($schema->string())
                            ->required(),
                        'confidence_seed' => $schema->number()->required(),
                        'market_relevance_score' => $schema->number()->required(),
                    ])->withoutAdditionalProperties()
                )
                ->required(),
        ];
    }
}
