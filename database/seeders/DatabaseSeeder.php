<?php

namespace Database\Seeders;

use App\Enums\RoleAlias;
use App\Models\User;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;

class DatabaseSeeder extends Seeder
{
    use WithoutModelEvents;

    /**
     * Seed the application's database.
     */
    public function run(): void
    {
        $this->call([
            RolePermissionSeeder::class,
        ]);

        $user = User::firstOrCreate(
            ['email' => 'test@example.com'],
            ['name' => 'Test User', 'password' => 'password']
        );
        $user->assignRole(RoleAlias::User->value);

        $superadminEmail = config('permission.superadmin_email', 'superadmin@example.com');
        $superadmin = User::firstOrCreate(
            ['email' => $superadminEmail],
            ['name' => 'Super Admin', 'password' => 'password']
        );
        $superadmin->assignRole(RoleAlias::SuperAdmin->value);
    }
}
