<?php

namespace Database\Factories;

use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends \Illuminate\Database\Eloquent\Factories\Factory<\App\Models\WorkHistory>
 */
class WorkHistoryFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'candidate_id' => null,
            'cv_id' => null,
            'company_name' => fake()->company(),
            'position' => fake()->jobTitle(),
            'start_date' => fake()->dateTimeBetween('-10 years', '-1 years')->format('Y-m-d'),
            'end_date' => fake()->optional(0.7)->dateTimeBetween('-1 years', 'now')->format('Y-m-d'),
            'responsibilities' => fake()->optional()->paragraph(),
        ];
    }
}
