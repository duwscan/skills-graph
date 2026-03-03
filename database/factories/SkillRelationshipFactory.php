<?php

namespace Database\Factories;

use App\Models\Skill;
use App\Models\SkillRelationship;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends \Illuminate\Database\Eloquent\Factories\Factory<\App\Models\SkillRelationship>
 */
class SkillRelationshipFactory extends Factory
{
    /**
     * The name of the factory's corresponding model.
     *
     * @var class-string<SkillRelationship>
     */
    protected $model = SkillRelationship::class;

    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $skills = Skill::factory()->count(2)->create();

        return [
            'source_skill_id' => $skills[0]->id,
            'target_skill_id' => $skills[1]->id,
            'relationship_type' => $this->faker->randomElement(['parent_of', 'child_of', 'related_to', 'requires', 'superseded_by']),
            'confidence' => $this->faker->randomFloat(2, 0, 1),
            'weight' => $this->faker->randomFloat(2, 0, 1),
            'provenance' => $this->faker->randomElement(['human_curated', 'llm_predicted', 'embedding_similarity', 'empirical']),
            'status' => $this->faker->randomElement(['active', 'pending_review', 'rejected', 'deprecated']),
        ];
    }
}
