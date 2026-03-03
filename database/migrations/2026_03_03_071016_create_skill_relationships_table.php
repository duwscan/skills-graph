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
        Schema::create('skill_relationships', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->uuid('source_skill_id');
            $table->uuid('target_skill_id');
            $table->text('relationship_type');
            $table->float('confidence')->default(1.0);
            $table->float('weight')->default(1.0);
            $table->text('provenance')->default('human_curated');
            $table->text('status')->default('active');
            $table->timestampsTz();

            $table->foreign('source_skill_id')
                ->references('id')
                ->on('skills')
                ->onDelete('cascade');

            $table->foreign('target_skill_id')
                ->references('id')
                ->on('skills')
                ->onDelete('cascade');

            $table->unique(['source_skill_id', 'target_skill_id', 'relationship_type']);
        });

        DB::statement('ALTER TABLE skill_relationships ALTER COLUMN id SET DEFAULT gen_random_uuid()');

        DB::statement("ALTER TABLE skill_relationships ADD CONSTRAINT skill_relationships_relationship_type_check CHECK (relationship_type IN ('parent_of', 'child_of', 'related_to', 'requires', 'superseded_by'))");
        DB::statement('ALTER TABLE skill_relationships ADD CONSTRAINT skill_relationships_confidence_check CHECK (confidence >= 0 AND confidence <= 1)');
        DB::statement('ALTER TABLE skill_relationships ADD CONSTRAINT skill_relationships_weight_check CHECK (weight >= 0 AND weight <= 1)');
        DB::statement("ALTER TABLE skill_relationships ADD CONSTRAINT skill_relationships_provenance_check CHECK (provenance IN ('human_curated', 'llm_predicted', 'embedding_similarity', 'empirical'))");
        DB::statement("ALTER TABLE skill_relationships ADD CONSTRAINT skill_relationships_status_check CHECK (status IN ('active', 'pending_review', 'rejected', 'deprecated'))");
        DB::statement('ALTER TABLE skill_relationships ADD CONSTRAINT skill_relationships_source_target_check CHECK (source_skill_id != target_skill_id)');

        DB::statement('CREATE INDEX idx_relationships_source ON skill_relationships (source_skill_id)');
        DB::statement('CREATE INDEX idx_relationships_target ON skill_relationships (target_skill_id)');
        DB::statement('CREATE INDEX idx_relationships_type ON skill_relationships (relationship_type)');
        DB::statement('CREATE INDEX idx_relationships_provenance ON skill_relationships (provenance)');
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('skill_relationships');
    }
};
