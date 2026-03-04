<?php

namespace Database\Factories;

use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends \Illuminate\Database\Eloquent\Factories\Factory<\App\Models\JobExpectation>
 */
class JobExpectationFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $salaryMin = fake()->numberBetween(20, 80) * 1_000_000;
        $salaryMax = $salaryMin + fake()->numberBetween(10, 50) * 1_000_000;

        return [
            'candidate_id' => null,
            'salary_min' => $salaryMin,
            'salary_max' => $salaryMax,
            'salary_currency' => 'VND',
            'work_type' => fake()->randomElement(['remote', 'hybrid', 'on_site', 'flexible']),
            'preferred_location' => fake()->optional(0.7)->city(),
            'notice_period' => fake()->optional(0.6)->randomElement(['2 weeks', '1 month', '2 months', 'Immediate']),
            'earliest_start_date' => fake()->optional(0.5)->dateTimeBetween('now', '+3 months')->format('Y-m-d'),
            'benefits_priorities' => fake()->optional(0.5)->randomElements(
                ['health_insurance', 'remote_equipment', 'learning_budget', 'flexible_hours', 'annual_leave'],
                fake()->numberBetween(1, 3)
            ),
            'notes' => fake()->optional(0.3)->sentence(),
        ];
    }
}
