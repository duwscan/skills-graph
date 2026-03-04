<?php

use App\Enums\PermissionAlias;
use App\Enums\RoleAlias;

/**
 * Build one or many permission middleware strings from PermissionAlias enums.
 *
 * @return array<int, string>
 */
function permission_middleware(PermissionAlias ...$permissions): array
{
    return array_map(
        static fn (PermissionAlias $permission): string => 'permission:'.$permission->value,
        $permissions,
    );
}

/**
 * Build one or many role middleware strings from RoleAlias enums.
 *
 * @return array<int, string>
 */
function role_middleware(RoleAlias ...$roles): array
{
    return array_map(
        static fn (RoleAlias $role): string => 'role:'.$role->value,
        $roles,
    );
}
