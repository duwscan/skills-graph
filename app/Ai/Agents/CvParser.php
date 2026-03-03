<?php

namespace App\Ai\Agents;

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
        return <<<'PROMPT'
You are an expert CV parsing assistant.

You will receive the CV as an attached document file (PDF, DOCX, TXT, or OCR output).

Your job is to:
- Identify and group the raw text for each of these high‑level sections:
  - basic_info (name, title, contact, summary, etc.)
  - experiences
  - educations
  - certifications
  - projects
  - awards
  - skills
- metatdata_blocks (all remaining content blocks that do not clearly belong to the sections above)
- metatdata_blocks may include layout artefacts, repeated headers / footers, or miscellaneous notes.
- For each field, return the corresponding raw block of text exactly as it appears in the CV text.
- You may normalize obviously broken line breaks or duplicated spaces if it improves readability.
- If a section does not exist, return an empty string for that field.

Only use the provided schema keys and do not add extra fields.
PROMPT;
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
