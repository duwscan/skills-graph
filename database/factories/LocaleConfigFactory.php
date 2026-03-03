<?php

namespace Database\Factories;

use App\Models\LocaleConfig;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends \Illuminate\Database\Eloquent\Factories\Factory<\App\Models\LocaleConfig>
 */
class LocaleConfigFactory extends Factory
{
    /**
     * The name of the factory's corresponding model.
     *
     * @var class-string<LocaleConfig>
     */
    protected $model = LocaleConfig::class;

    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'locale' => $this->faker->unique()->locale(),
            'display_name' => $this->faker->languageCode(),
            'is_active' => $this->faker->boolean(80),
            'coverage_pct' => $this->faker->randomFloat(2, 0, 100),
        ];
    }
}
