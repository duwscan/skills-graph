<?php

namespace App\Enums;

enum RoleAlias: string
{
    case User = 'user';
    case Admin = 'admin';
    case Agent = 'agent';
    case SuperAdmin = 'superadmin';

    /**
     * @return list<string>
     */
    public static function values(): array
    {
        return array_column(self::cases(), 'value');
    }
}
