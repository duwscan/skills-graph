<?php

namespace Database\Factories;

use App\Models\Skill;
use App\Models\SkillCoOccurrence;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends \Illuminate\Database\Eloquent\Factories\Factory<\App\Models\SkillCoOccurrence>
 */
class SkillCoOccurrenceFactory extends Factory
{
    /**
     * The name of the factory's corresponding model.
     *
     * @var class-string<SkillCoOccurrence>
     */
    protected $model = SkillCoOccurrence::class;

    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $skills = Skill::factory()->count(2)->create()->sortBy('id')->values();

        return [
            'skill_a_id' => $skills[0]->id,
            'skill_b_id' => $skills[1]->id,
            'co_occurrence_count' => $this->faker->numberBetween(1, 100),
            'source_type_counts' => [
                'cv' => $this->faker->numberBetween(0, 50),
                'jd' => $this->faker->numberBetween(0, 50),
            ],
            'last_seen_at' => now(),
        ];
    }
}
