<?php

namespace Database\Factories;

use App\Models\Skill;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends \Illuminate\Database\Eloquent\Factories\Factory<\App\Models\Skill>
 */
class SkillFactory extends Factory
{
    /**
     * The name of the factory's corresponding model.
     *
     * @var class-string<Skill>
     */
    protected $model = Skill::class;

    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'external_id' => $this->faker->unique()->bothify('SK-####'),
            'canonical_name' => $this->faker->unique()->sentence(2),
            'slug' => $this->faker->unique()->slug(),
            'description' => $this->faker->sentence(8),
            'status' => $this->faker->randomElement(['candidate', 'active', 'deprecated', 'merged']),
            'category' => $this->faker->randomElement(['domain', 'tool', 'certification', 'soft_skill', 'methodology', 'language']),
            'path' => 'tech.'.$this->faker->slug(2),
            'version' => 1,
            'source' => $this->faker->randomElement(['curator', 'llm_discovered', 'import']),
            'metadata' => [],
        ];
    }
}
