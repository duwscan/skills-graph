<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/** @use HasFactory<\Database\Factories\JobExpectationFactory> */
class JobExpectation extends Model
{
    use HasFactory;

    /**
     * The attributes that are mass assignable.
     *
     * @var list<string>
     */
    protected $fillable = [
        'candidate_id',
        'salary_min',
        'salary_max',
        'salary_currency',
        'work_type',
        'preferred_location',
        'notice_period',
        'earliest_start_date',
        'benefits_priorities',
        'notes',
    ];

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'earliest_start_date' => 'date',
            'benefits_priorities' => 'array',
        ];
    }

    /**
     * Get the candidate that owns the job expectation.
     */
    public function candidate(): BelongsTo
    {
        return $this->belongsTo(Candidate::class);
    }
}
