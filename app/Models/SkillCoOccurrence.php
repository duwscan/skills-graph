<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class SkillCoOccurrence extends Model
{
    use HasFactory;

    /**
     * The attributes that are mass assignable.
     *
     * @var list<string>
     */
    protected $fillable = [
        'skill_a_id',
        'skill_b_id',
        'co_occurrence_count',
        'source_type_counts',
        'last_seen_at',
    ];

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'source_type_counts' => 'array',
            'last_seen_at' => 'datetime',
        ];
    }

    /**
     * Get the first skill in the co-occurrence pair.
     */
    public function skillA(): BelongsTo
    {
        return $this->belongsTo(Skill::class, 'skill_a_id');
    }

    /**
     * Get the second skill in the co-occurrence pair.
     */
    public function skillB(): BelongsTo
    {
        return $this->belongsTo(Skill::class, 'skill_b_id');
    }
}
