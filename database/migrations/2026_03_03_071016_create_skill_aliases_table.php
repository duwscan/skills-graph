<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        Schema::create('skill_aliases', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->uuid('skill_id');
            $table->text('surface_form');
            $table->text('locale')->default('en');
            $table->text('source')->default('curated');
            $table->boolean('is_primary')->default(false);
            $table->timestampsTz(0);

            $table->foreign('skill_id')
                ->references('id')
                ->on('skills')
                ->onDelete('cascade');

            $table->unique(['skill_id', 'locale', 'surface_form']);
        });

        DB::statement('ALTER TABLE skill_aliases ALTER COLUMN id SET DEFAULT gen_random_uuid()');

        DB::statement('ALTER TABLE skill_aliases ADD COLUMN alias_embedding vector(1024)');

        DB::statement("ALTER TABLE skill_aliases ADD CONSTRAINT skill_aliases_source_check CHECK (source IN ('curated', 'llm_discovered', 'user_submitted'))");

        DB::statement('CREATE INDEX idx_aliases_embedding ON skill_aliases USING hnsw (alias_embedding vector_cosine_ops)');
        DB::statement('CREATE INDEX idx_aliases_skill_id ON skill_aliases (skill_id)');
        DB::statement('CREATE INDEX idx_aliases_surface_trgm ON skill_aliases USING gin (surface_form gin_trgm_ops)');
        DB::statement('CREATE INDEX idx_aliases_locale ON skill_aliases (locale)');
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('skill_aliases');
    }
};
