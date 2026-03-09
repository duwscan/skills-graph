<?php

namespace App\Ai\Agents;

use Chimit\Prompt;
use Illuminate\Contracts\JsonSchema\JsonSchema;
use Laravel\Ai\Contracts\Agent;
use Laravel\Ai\Contracts\HasStructuredOutput;
use Laravel\Ai\Promptable;
use Stringable;

class WorkHistoryExtractor implements Agent, HasStructuredOutput
{
    use Promptable;

    /**
     * Get the instructions that the agent should follow.
     */
    public function instructions(): Stringable|string
    {
        return Prompt::get('cv/extract-work-history');
    }

    /**
     * Define the structured output schema for work history entries.
     */
    public function schema(JsonSchema $schema): array
    {
        return [
            'items' => $schema->array()
                ->items(
                    $schema->object([
                        'company_name' => $schema->string()->required(),
                        'position' => $schema->string()->required(),
                        'start_date' => $schema->string()->required(),
                        'end_date' => $schema->string()->required(),
                        'responsibilities' => $schema->string()->required(),
                    ])->withoutAdditionalProperties()
                )
                ->required(),
        ];
    }
}
