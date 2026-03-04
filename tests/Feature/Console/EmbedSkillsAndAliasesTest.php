<?php

namespace Tests\Feature\Console;

use App\Ai\EmbeddingService;
use App\Models\Skill;
use App\Models\SkillAlias;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Mockery\MockInterface;
use Tests\TestCase;

class EmbedSkillsAndAliasesTest extends TestCase
{
    use RefreshDatabase;

    public function test_embeds_skills_that_are_missing_embeddings(): void
    {
        $skill = Skill::factory()->create(['embedding' => null]);

        $fakeVector = array_fill(0, 1024, 0.1);

        $this->mock(EmbeddingService::class, function (MockInterface $mock) use ($fakeVector): void {
            $mock->shouldReceive('embedMany')
                ->once()
                ->andReturn([$fakeVector]);
        });

        $this->artisan('skills:embed')
            ->assertSuccessful();

        $skill->refresh();
        $this->assertNotNull($skill->embedding);
        $this->assertCount(1024, $skill->embedding);
    }

    public function test_embeds_skill_aliases_that_are_missing_embeddings(): void
    {
        $skill = Skill::factory()->create();
        $alias = SkillAlias::factory()->create([
            'skill_id' => $skill->id,
            'alias_embedding' => null,
        ]);

        $fakeVector = array_fill(0, 1024, 0.2);

        $this->mock(EmbeddingService::class, function (MockInterface $mock) use ($fakeVector): void {
            $mock->shouldReceive('embedMany')
                ->andReturn([$fakeVector]);
        });

        $this->artisan('skills:embed')
            ->assertSuccessful();

        $alias->refresh();
        $this->assertNotNull($alias->alias_embedding);
        $this->assertCount(1024, $alias->alias_embedding);
    }

    public function test_skips_records_that_already_have_embeddings(): void
    {
        $existingVector = array_fill(0, 1024, 0.5);

        Skill::factory()->create(['embedding' => $existingVector]);
        SkillAlias::factory()->create(['alias_embedding' => $existingVector]);

        $this->mock(EmbeddingService::class, function (MockInterface $mock): void {
            $mock->shouldNotReceive('embedMany');
        });

        $this->artisan('skills:embed')
            ->assertSuccessful();
    }

    public function test_force_option_re_embeds_all_records(): void
    {
        $oldVector = array_fill(0, 1024, 0.3);
        $newVector = array_fill(0, 1024, 0.9);

        $skill = Skill::factory()->create(['embedding' => $oldVector]);
        SkillAlias::factory()->create([
            'skill_id' => $skill->id,
            'alias_embedding' => $oldVector,
        ]);

        $this->mock(EmbeddingService::class, function (MockInterface $mock) use ($newVector): void {
            $mock->shouldReceive('embedMany')
                ->twice()
                ->andReturn([$newVector]);
        });

        $this->artisan('skills:embed --force')
            ->assertSuccessful();

        $skill->refresh();
        $this->assertEquals($newVector, $skill->embedding);
    }

    public function test_outputs_nothing_to_embed_when_tables_are_empty(): void
    {
        $this->artisan('skills:embed')
            ->assertSuccessful();
    }
}
