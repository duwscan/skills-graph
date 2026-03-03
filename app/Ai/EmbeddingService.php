<?php

namespace App\Ai;

use InvalidArgumentException;
use Laravel\Ai\Embeddings;

class EmbeddingService
{
    /**
     * Generate an embedding vector for a single text string.
     *
     * @return list<float>
     */
    public function embed(
        string $text,
        ?string $provider = null,
        ?string $model = null,
        ?int $dimensions = null,
        bool|int $cache = true,
    ): array {
        $text = trim($text);

        if ($text === '') {
            throw new InvalidArgumentException('Cannot generate an embedding for an empty string.');
        }

        return $this->embedMany([$text], $provider, $model, $dimensions, $cache)[0];
    }

    /**
     * Generate embedding vectors for multiple text strings.
     *
     * @param  list<string>  $texts
     * @return list<list<float>>
     */
    public function embedMany(
        array $texts,
        ?string $provider = null,
        ?string $model = null,
        ?int $dimensions = null,
        bool|int $cache = true,
    ): array {
        $texts = array_values(array_filter(
            array_map('trim', $texts),
            fn (string $t): bool => $t !== '',
        ));

        if ($texts === []) {
            throw new InvalidArgumentException('No valid texts provided for embedding generation.');
        }

        $provider ??= config('ai.default_for_embeddings');
        $model ??= config('ai.embedding_model');
        $dimensions ??= (int) config('ai.embedding_dimensions');

        $pending = Embeddings::for($texts);

        if ($dimensions > 0) {
            $pending->dimensions($dimensions);
        }

        if ($cache !== false) {
            $seconds = is_int($cache) ? $cache : null;
            $pending->cache(...($seconds !== null ? ['seconds' => $seconds] : []));
        }

        $response = $pending->generate(provider: $provider, model: $model);

        return $response->embeddings;
    }
}
