<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        Schema::table('work_histories', function (Blueprint $table) {
            $table->dropForeign(['cv_id']);
            $table->foreignId('cv_id')->nullable()->change();
            $table->foreign('cv_id')->references('id')->on('cvs')->nullOnDelete();
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('work_histories', function (Blueprint $table) {
            $table->dropForeign(['cv_id']);
            $table->foreignId('cv_id')->nullable(false)->change();
            $table->foreign('cv_id')->references('id')->on('cvs')->cascadeOnDelete();
        });
    }
};
