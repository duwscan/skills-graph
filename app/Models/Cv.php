<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Cv extends Model
{
    /**
     * The attributes that are mass assignable.
     *
     * @var list<string>
     */
    protected $fillable = [
        'candidate_id',
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
     * Get the candidate that owns the CV.
     */
    public function candidate(): BelongsTo
    {
        return $this->belongsTo(Candidate::class);
    }

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
