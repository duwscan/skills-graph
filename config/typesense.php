<?php

return [

    /*
    |--------------------------------------------------------------------------
    | Typesense Connection
    |--------------------------------------------------------------------------
    |
    | Core connection settings for the Typesense cluster.
    |
    */

    'host' => env('TYPESENSE_HOST', 'localhost'),

    'port' => (int) env('TYPESENSE_PORT', 8108),

    'protocol' => env('TYPESENSE_PROTOCOL', 'http'),

    'api_key' => env('TYPESENSE_API_KEY'),

    /*
    |--------------------------------------------------------------------------
    | Collections
    |--------------------------------------------------------------------------
    |
    | Collection names used by the application.
    |
    */

    'collections' => [
        'skills' => env('TYPESENSE_SKILLS_COLLECTION', 'skills'),
    ],

];
