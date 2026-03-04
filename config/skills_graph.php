<?php

return [

    /*
    |--------------------------------------------------------------------------
    | Embedding & Vector Search
    |--------------------------------------------------------------------------
    |
    | Centralized configuration for similarity thresholds, embedding dimensions,
    | and RAG retrieval limits used across the skills graph.
    |
    */

    'similarity_duplicate_threshold' => (float) env('SIMILARITY_DUPLICATE_THRESHOLD', 0.9),

    'similarity_review_threshold' => (float) env('SIMILARITY_REVIEW_THRESHOLD', 0.7),

    'similarity_related_high_threshold' => 0.85,

    'similarity_related_low_threshold' => 0.65,

    'rag_candidate_limit' => (int) env('RAG_CANDIDATE_LIMIT', 100),

    /*
    |--------------------------------------------------------------------------
    | API & Pagination
    |--------------------------------------------------------------------------
    |
    | Defaults and maximums for search-related pagination.
    |
    */

    'pagination_default_limit' => 20,

    'pagination_max_limit' => 100,

];
