<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Cv extends Model
{
    /**
     * The attributes that are mass assignable.
     *
     * @var list<string>
     */
    protected $fillable = [
        'basic_info',
        'experiences',
        'educations',
        'certifications',
        'projects',
        'awards',
        'skills',
        'metatdata_blocks',
    ];

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'metatdata_blocks' => 'array',
        ];
    }
}
