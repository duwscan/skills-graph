<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Skill extends Model
{
    use HasFactory;

    /**
     * The attributes that are mass assignable.
     *
     * @var list<string>
     */
    protected $fillable = [
        'external_id',
        'canonical_name',
        'slug',
        'description',
        'status',
        'category',
        'path',
        'embedding',
        'version',
        'source',
        'metadata',
    ];

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'metadata' => 'array',
        ];
    }

    /**
     * Get the aliases for the skill.
     */
    public function aliases(): HasMany
    {
        return $this->hasMany(SkillAlias::class);
    }

    /**
     * Get the outgoing relationships for the skill.
     */
    public function outgoingRelationships(): HasMany
    {
        return $this->hasMany(SkillRelationship::class, 'source_skill_id');
    }

    /**
     * Get the incoming relationships for the skill.
     */
    public function incomingRelationships(): HasMany
    {
        return $this->hasMany(SkillRelationship::class, 'target_skill_id');
    }
}
