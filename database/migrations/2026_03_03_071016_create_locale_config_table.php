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
        Schema::create('locale_config', function (Blueprint $table) {
            $table->text('locale')->primary();
            $table->text('display_name');
            $table->boolean('is_active')->default(true);
            $table->float('coverage_pct')->default(0);
        });

        DB::statement('CREATE INDEX idx_locale_config_locale ON locale_config (locale)');
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('locale_config');
    }
};
