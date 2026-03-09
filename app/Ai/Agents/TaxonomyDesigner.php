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

#[MaxTokens(8192)]
#[Temperature(0.4)]
class TaxonomyDesigner implements Agent, HasStructuredOutput
{
    use Promptable;

    /**
     * @param  list<string>  $domains
     */
    public function __construct(public array $domains = []) {}

    /**
     * Get the instructions that the agent should follow.
     */
    public function instructions(): Stringable|string
    {
        $domainList = $this->domains !== []
            ? implode(', ', $this->domains)
            : 'the requested domains';

        return Prompt::get('taxonomy/design-taxonomy-map', [
            'domainList' => $domainList,
        ]);
    }

    /**
     * Define the structured output schema for the taxonomy map.
     */
    public function schema(JsonSchema $schema): array
    {
        return [
            'domains' => $schema->array()
                ->items(
                    $schema->object([
                        'domain' => $schema->string()->required(),
                        'categories' => $schema->array()
                            ->items(
                                $schema->object([
                                    'category' => $schema->string()->required(),
                                    'subcategories' => $schema->array()
                                        ->items($schema->string())
                                        ->required(),
                                ])->withoutAdditionalProperties()
                            )
                            ->required(),
                    ])->withoutAdditionalProperties()
                )
                ->required(),
        ];
    }
}
