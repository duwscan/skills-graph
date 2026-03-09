<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        DB::statement('ALTER TABLE skills DROP CONSTRAINT IF EXISTS skills_category_check');

        DB::statement("ALTER TABLE skills ADD CONSTRAINT skills_category_check CHECK (category IN (
            'domain', 'tool', 'certification', 'soft_skill', 'methodology', 'language',
            'hard_skill', 'tool_skill', 'process_skill', 'analytical_skill',
            'compliance_skill', 'communication_skill', 'operational_skill'
        ))");
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        DB::statement('ALTER TABLE skills DROP CONSTRAINT IF EXISTS skills_category_check');

        DB::statement("ALTER TABLE skills ADD CONSTRAINT skills_category_check CHECK (category IN ('domain', 'tool', 'certification', 'soft_skill', 'methodology', 'language'))");
    }
};
