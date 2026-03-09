<?php

namespace App\Ai\Agents;

use Chimit\Prompt;
use Illuminate\Contracts\JsonSchema\JsonSchema;
use Laravel\Ai\Contracts\Agent;
use Laravel\Ai\Contracts\HasStructuredOutput;
use Laravel\Ai\Promptable;
use Stringable;

class CandidateExtractor implements Agent, HasStructuredOutput
{
    use Promptable;

    /**
     * Get the instructions that the agent should follow.
     */
    public function instructions(): Stringable|string
    {
        return Prompt::get('cv/extract-candidate-info');
    }

    /**
     * Define the structured output schema for the extracted candidate.
     */
    public function schema(JsonSchema $schema): array
    {
        return [
            'first_name' => $schema->string()->required(),
            'last_name' => $schema->string()->required(),
            'email' => $schema->string()->required(),
            'phone' => $schema->string()->required(),
            'summary' => $schema->string()->required(),
        ];
    }
}
