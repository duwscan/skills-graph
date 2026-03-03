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
        Schema::create('skills', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->text('external_id')->unique();
            $table->text('canonical_name');
            $table->text('slug')->unique();
            $table->text('description')->nullable();
            $table->text('status')->default('candidate');
            $table->text('category')->nullable();
            $table->integer('version')->default(1);
            $table->text('source')->nullable();
            $table->timestampsTz();
        });

        DB::statement('CREATE EXTENSION IF NOT EXISTS ltree');
        DB::statement('CREATE EXTENSION IF NOT EXISTS vector');

        DB::statement('ALTER TABLE skills ALTER COLUMN id SET DEFAULT gen_random_uuid()');

        DB::statement('ALTER TABLE skills ADD COLUMN path ltree NOT NULL');
        DB::statement('ALTER TABLE skills ADD COLUMN embedding vector(1024)');
        DB::statement("ALTER TABLE skills ADD COLUMN metadata jsonb NOT NULL DEFAULT '{}'::jsonb");

        DB::statement("ALTER TABLE skills ADD CONSTRAINT skills_status_check CHECK (status IN ('candidate', 'active', 'deprecated', 'merged'))");
        DB::statement("ALTER TABLE skills ADD CONSTRAINT skills_category_check CHECK (category IN ('domain', 'tool', 'certification', 'soft_skill', 'methodology', 'language'))");

        DB::statement('CREATE INDEX idx_skills_embedding ON skills USING hnsw (embedding vector_cosine_ops)');
        DB::statement('CREATE INDEX idx_skills_path ON skills USING gist (path)');
        DB::statement('CREATE INDEX idx_skills_name_trgm ON skills USING gin (canonical_name gin_trgm_ops)');
        DB::statement("CREATE INDEX idx_skills_status ON skills (status) WHERE status = 'active'");
        DB::statement('CREATE INDEX idx_skills_slug ON skills (slug)');
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('skills');
    }
};
