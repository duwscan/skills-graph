<?php

namespace App\Ai\Agents;

use Chimit\Prompt;
use Illuminate\Contracts\JsonSchema\JsonSchema;
use Laravel\Ai\Contracts\Agent;
use Laravel\Ai\Contracts\HasStructuredOutput;
use Laravel\Ai\Promptable;
use Stringable;

class CvParser implements Agent, HasStructuredOutput
{
    use Promptable;

    /**
     * Get the instructions that the agent should follow.
     */
    public function instructions(): Stringable|string
    {
        return Prompt::get('cv/parse-cv-sections');
    }

    /**
     * Define the structured output schema for the parsed CV.
     */
    public function schema(JsonSchema $schema): array
    {
        return [
            'basic_info' => $schema->string()->required(),
            'experiences' => $schema->string()->required(),
            'educations' => $schema->string()->required(),
            'certifications' => $schema->string()->required(),
            'projects' => $schema->string()->required(),
            'awards' => $schema->string()->required(),
            'skills' => $schema->string()->required(),
            'metatdata_blocks' => $schema->array()
                ->items($schema->string())
                ->required(),
        ];
    }
}
