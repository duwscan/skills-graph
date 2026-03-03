<?php

namespace App\Ai\Agents;

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
        return <<<'PROMPT'
You are an expert CV information extraction assistant.

You will receive the raw "basic_info" text section extracted from a candidate CV.

Your task is to extract and normalize the candidate's personal information into structured fields:
- first_name
- last_name
- email
- phone
- summary

Guidelines:
- Parse the candidate's name from the text, inferring first and last name from the full name if needed.
- Normalize email and phone to common formats and remove surrounding noise text.
- summary should be a concise professional summary or headline from the basic info; if not present, use an empty string.
- If you cannot confidently extract a field, return an empty string for that field.
- Only use the provided schema keys and do not add extra fields.
PROMPT;
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
