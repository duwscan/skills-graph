<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class SkillRelationship extends Model
{
    use HasFactory;

    /**
     * The attributes that are mass assignable.
     *
     * @var list<string>
     */
    protected $fillable = [
        'source_skill_id',
        'target_skill_id',
        'relationship_type',
        'confidence',
        'weight',
        'provenance',
        'status',
    ];

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'confidence' => 'float',
            'weight' => 'float',
        ];
    }

    /**
     * Get the source skill for the relationship.
     */
    public function sourceSkill(): BelongsTo
    {
        return $this->belongsTo(Skill::class, 'source_skill_id');
    }

    /**
     * Get the target skill for the relationship.
     */
    public function targetSkill(): BelongsTo
    {
        return $this->belongsTo(Skill::class, 'target_skill_id');
    }
}
