<?php

namespace Database\Factories;

use App\Models\Skill;
use App\Models\SkillAlias;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends \Illuminate\Database\Eloquent\Factories\Factory<\App\Models\SkillAlias>
 */
class SkillAliasFactory extends Factory
{
    /**
     * The name of the factory's corresponding model.
     *
     * @var class-string<SkillAlias>
     */
    protected $model = SkillAlias::class;

    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'skill_id' => Skill::factory(),
            'surface_form' => $this->faker->unique()->word(),
            'locale' => $this->faker->randomElement(['en', 'en-US', 'fr-FR', 'vi-VN']),
            'source' => $this->faker->randomElement(['curated', 'llm_discovered', 'user_submitted']),
            'is_primary' => $this->faker->boolean(70),
        ];
    }
}
