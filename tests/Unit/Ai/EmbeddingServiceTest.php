<?php

namespace Tests\Unit\Ai;

use App\Ai\EmbeddingService;
use InvalidArgumentException;
use Laravel\Ai\Embeddings;
use Laravel\Ai\Prompts\EmbeddingsPrompt;
use Tests\TestCase;

class EmbeddingServiceTest extends TestCase
{
    protected function setUp(): void
    {
        parent::setUp();

        config()->set('ai.default_for_embeddings', 'ollama_openai');
        config()->set('ai.embedding_model', 'mxbai-embed-large');
        config()->set('ai.embedding_dimensions', 1024);
    }

    public function test_embed_returns_a_vector_for_a_single_string(): void
    {
        Embeddings::fake();

        $service = new EmbeddingService;
        $vector = $service->embed('Laravel is great');

        $this->assertIsArray($vector);
        $this->assertNotEmpty($vector);
        $this->assertContainsOnly('float', $vector);

        Embeddings::assertGenerated(
            fn (EmbeddingsPrompt $prompt): bool => $prompt->contains('Laravel is great')
        );
    }

    public function test_embed_many_returns_one_vector_per_input(): void
    {
        Embeddings::fake();

        $service = new EmbeddingService;
        $vectors = $service->embedMany(['PHP', 'Python', 'JavaScript']);

        $this->assertCount(3, $vectors);

        foreach ($vectors as $vector) {
            $this->assertIsArray($vector);
            $this->assertNotEmpty($vector);
        }
    }

    public function test_embed_passes_configured_dimensions(): void
    {
        Embeddings::fake();

        $service = new EmbeddingService;
        $service->embed('test dimensions');

        Embeddings::assertGenerated(
            fn (EmbeddingsPrompt $prompt): bool => $prompt->dimensions === 1024
        );
    }

    public function test_embed_many_filters_empty_strings(): void
    {
        Embeddings::fake();

        $service = new EmbeddingService;
        $vectors = $service->embedMany(['valid', '', '  ', 'also valid']);

        $this->assertCount(2, $vectors);

        Embeddings::assertGenerated(
            fn (EmbeddingsPrompt $prompt): bool => $prompt->contains('valid') && $prompt->contains('also valid')
        );
    }

    public function test_embed_throws_for_empty_string(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('Cannot generate an embedding for an empty string.');

        $service = new EmbeddingService;
        $service->embed('');
    }

    public function test_embed_many_throws_when_all_inputs_are_empty(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('No valid texts provided for embedding generation.');

        $service = new EmbeddingService;
        $service->embedMany(['', '  ', "\t"]);
    }

    public function test_embed_respects_explicit_provider_and_model(): void
    {
        Embeddings::fake();

        $service = new EmbeddingService;
        $service->embed('custom provider', provider: 'openai', model: 'text-embedding-3-small', dimensions: 1536);

        Embeddings::assertGenerated(
            fn (EmbeddingsPrompt $prompt): bool => $prompt->dimensions === 1536
                && $prompt->contains('custom provider')
        );
    }

    public function test_embed_disables_cache_when_false(): void
    {
        Embeddings::fake();

        $service = new EmbeddingService;
        $service->embed('no cache', cache: false);

        Embeddings::assertGenerated(
            fn (EmbeddingsPrompt $prompt): bool => $prompt->contains('no cache')
        );
    }
}
