<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Laravel\Scout\Searchable;

class Skill extends Model
{
    use HasFactory;
    use Searchable;

    /**
     * The primary key type is a UUID string.
     *
     * @var string
     */
    protected $keyType = 'string';

    /**
     * The primary key is not an auto-incrementing integer.
     *
     * @var bool
     */
    public $incrementing = false;

    /**
     * The attributes that should be hidden for serialization.
     *
     * @var list<string>
     */
    protected $hidden = [
        'embedding',
        'metadata',
    ];

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
            'embedding' => 'array',
            'metadata' => 'array',
        ];
    }

    /**
     * Get the data that should be indexed by Laravel Scout.
     *
     * @return array<string, mixed>
     */
    public function toSearchableArray(): array
    {
        return [
            'id' => $this->id,
            'external_id' => $this->external_id,
            'canonical_name' => $this->canonical_name,
            'slug' => $this->slug,
            'description' => $this->description,
            'category' => $this->category,
            'status' => $this->status,
            'aliases' => $this->aliases()->pluck('surface_form')->all(),
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
