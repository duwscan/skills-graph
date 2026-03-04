<?php

namespace App\Console\Commands;

use App\Enums\RoleAlias;
use App\Models\User;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;

class GenerateSuperadminJwt extends Command
{
    /**
     * The name and signature of the console command.
     *
     * @var string
     */
    protected $signature = 'jwt:superadmin
                            {--email= : Email of the superadmin user (default: first superadmin)}
                            {--create : Create superadmin user if not exists}';

    /**
     * The console command description.
     *
     * @var string
     */
    protected $description = 'Generate a never-expiring JWT token for the superadmin user';

    /**
     * TTL in minutes for "never expiring" token (100 years).
     */
    private const NEVER_EXPIRING_TTL = 52560000;

    /**
     * Execute the console command.
     */
    public function handle(): int
    {
        $user = $this->resolveSuperadminUser();

        if (! $user) {
            $this->error('No superadmin user found. Run with --create to create one, or seed the database first.');

            return self::FAILURE;
        }

        $guard = auth('api');
        $guard->setTTL(self::NEVER_EXPIRING_TTL);

        $token = $guard->login($user);

        $this->newLine();
        $this->info('Superadmin JWT token (never expiring):');
        $this->line($token);
        $this->newLine();
        $this->comment('Usage: Authorization: Bearer '.substr($token, 0, 50).'...');

        return self::SUCCESS;
    }

    private function resolveSuperadminUser(): ?User
    {
        $email = $this->option('email');

        if ($email) {
            $user = User::query()->where('email', $email)->first();
            if ($user && $user->hasRole(RoleAlias::SuperAdmin->value)) {
                return $user;
            }
            if ($user && ! $user->hasRole(RoleAlias::SuperAdmin->value)) {
                $this->warn("User {$email} exists but does not have superadmin role. Assigning role.");
                $user->assignRole(RoleAlias::SuperAdmin->value);

                return $user;
            }
            if ($this->option('create')) {
                return $this->createSuperadminUser($email);
            }

            return null;
        }

        $superAdmin = User::query()
            ->whereHas('roles', fn ($q) => $q->where('name', RoleAlias::SuperAdmin->value))
            ->first();

        if ($superAdmin) {
            return $superAdmin;
        }

        if ($this->option('create')) {
            $email = $this->ask('Enter superadmin email', config('permission.superadmin_email', 'superadmin@example.com'));

            return $this->createSuperadminUser($email);
        }

        return null;
    }

    private function createSuperadminUser(string $email): User
    {
        $user = User::query()->where('email', $email)->first();

        if ($user) {
            $user->assignRole(RoleAlias::SuperAdmin->value);

            return $user;
        }

        return DB::transaction(function () use ($email): User {
            $user = User::factory()->create([
                'email' => $email,
                'name' => 'Super Admin',
            ]);
            $user->assignRole(RoleAlias::SuperAdmin->value);

            return $user;
        });
    }
}
