<?php

namespace Database\Seeders;

use App\Enums\PermissionAlias;
use App\Enums\RoleAlias;
use Illuminate\Database\Seeder;
use Spatie\Permission\Models\Permission;
use Spatie\Permission\Models\Role;

class RolePermissionSeeder extends Seeder
{
    /**
     * Run the database seeds.
     */
    public function run(): void
    {
        $guardName = 'api';

        $parseCvPermission = Permission::firstOrCreate(
            ['name' => PermissionAlias::ParseCv->value, 'guard_name' => $guardName]
        );
        $manageUsersPermission = Permission::firstOrCreate(
            ['name' => PermissionAlias::ManageUsers->value, 'guard_name' => $guardName]
        );
        $manageCandidatesPermission = Permission::firstOrCreate(
            ['name' => PermissionAlias::ManageCandidates->value, 'guard_name' => $guardName]
        );

        $userRole = Role::firstOrCreate(['name' => RoleAlias::User->value, 'guard_name' => $guardName]);
        $userRole->syncPermissions([$parseCvPermission]);

        $adminRole = Role::firstOrCreate(['name' => RoleAlias::Admin->value, 'guard_name' => $guardName]);
        $adminRole->syncPermissions([$parseCvPermission, $manageUsersPermission]);

        $agentRole = Role::firstOrCreate(['name' => RoleAlias::Agent->value, 'guard_name' => $guardName]);
        $agentRole->syncPermissions([$manageCandidatesPermission]);

        $superAdminRole = Role::firstOrCreate(['name' => RoleAlias::SuperAdmin->value, 'guard_name' => $guardName]);
        $superAdminRole->syncPermissions(Permission::where('guard_name', $guardName)->get());
    }
}
