<?php

namespace App\Enums;

enum PermissionAlias: string
{
    case ParseCv = 'parse-cv';
    case ManageUsers = 'manage-users';
    case ManageCandidates = 'manage-candidates';

    /**
     * @return list<string>
     */
    public static function values(): array
    {
        return array_column(self::cases(), 'value');
    }
}
