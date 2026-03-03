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
        Schema::create('skill_co_occurrences', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->uuid('skill_a_id');
            $table->uuid('skill_b_id');
            $table->integer('co_occurrence_count')->default(1);
            $table->timestampTz('last_seen_at')->useCurrent();
            $table->timestampTz('created_at')->useCurrent();

            $table->foreign('skill_a_id')
                ->references('id')
                ->on('skills')
                ->onDelete('cascade');

            $table->foreign('skill_b_id')
                ->references('id')
                ->on('skills')
                ->onDelete('cascade');

            $table->unique(['skill_a_id', 'skill_b_id']);
        });

        DB::statement('ALTER TABLE skill_co_occurrences ALTER COLUMN id SET DEFAULT gen_random_uuid()');

        DB::statement("ALTER TABLE skill_co_occurrences ADD COLUMN source_type_counts jsonb NOT NULL DEFAULT '{}'::jsonb");
        DB::statement('ALTER TABLE skill_co_occurrences ADD CONSTRAINT skill_co_occurrences_skill_order_check CHECK (skill_a_id < skill_b_id)');

        DB::statement('CREATE INDEX idx_co_occurrences_skill_a ON skill_co_occurrences (skill_a_id)');
        DB::statement('CREATE INDEX idx_co_occurrences_skill_b ON skill_co_occurrences (skill_b_id)');
        DB::statement('CREATE INDEX idx_co_occurrences_count ON skill_co_occurrences (co_occurrence_count DESC)');
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('skill_co_occurrences');
    }
};
