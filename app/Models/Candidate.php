<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Database\Eloquent\Relations\HasOne;

class Candidate extends Model
{
    use HasFactory;

    /**
     * The relationships that should always be loaded.
     *
     * @var list<string>
     */
    protected $with = [
        'workHistories',
        'jobExpectation',
    ];

    /**
     * The accessors to append to the model's array form.
     *
     * @var list<string>
     */
    protected $appends = [
        'current_or_last_company',
        'first_work_start_date',
        'last_work_end_date',
    ];

    /**
     * The attributes that are mass assignable.
     *
     * @var list<string>
     */
    protected $fillable = [
        'user_id',
        'first_name',
        'last_name',
        'email',
        'phone',
        'summary',
    ];

    /**
     * Get the user that owns the candidate.
     */
    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    /**
     * Get the CVs for the candidate.
     */
    public function cvs(): HasMany
    {
        return $this->hasMany(Cv::class);
    }

    /**
     * Get the work history entries for the candidate.
     */
    public function workHistories(): HasMany
    {
        return $this->hasMany(WorkHistory::class);
    }

    /**
     * Get the job expectation for the candidate.
     */
    public function jobExpectation(): HasOne
    {
        return $this->hasOne(JobExpectation::class);
    }

    /**
     * Get the current or most recent company name for the candidate.
     */
    public function getCurrentOrLastCompanyAttribute(): ?string
    {
        $histories = $this->relationLoaded('workHistories')
            ? $this->workHistories
            : $this->workHistories()->get();

        if ($histories->isEmpty()) {
            return null;
        }

        $latest = $histories
            ->filter(fn (WorkHistory $history): bool => (string) $history->company_name !== '')
            ->sortByDesc('start_date')
            ->first();

        return $latest?->company_name ?: null;
    }

    /**
     * Get the first date the candidate started working.
     */
    public function getFirstWorkStartDateAttribute(): ?string
    {
        $histories = $this->relationLoaded('workHistories')
            ? $this->workHistories
            : $this->workHistories()->get();

        $first = $histories
            ->filter(fn (WorkHistory $history): bool => $history->start_date !== null)
            ->sortBy('start_date')
            ->first();

        if ($first === null || $first->start_date === null) {
            return null;
        }

        return method_exists($first->start_date, 'toDateString')
            ? $first->start_date->toDateString()
            : (string) $first->start_date;
    }

    /**
     * Get the most recent end date across all work history entries.
     */
    public function getLastWorkEndDateAttribute(): ?string
    {
        $histories = $this->relationLoaded('workHistories')
            ? $this->workHistories
            : $this->workHistories()->get();

        $last = $histories
            ->filter(fn (WorkHistory $history): bool => $history->end_date !== null)
            ->sortByDesc(callback: 'end_date')
            ->first();

        if ($last === null || $last->end_date === null) {
            return null;
        }

        return method_exists($last->end_date, 'toDateString')
            ? $last->end_date->toDateString()
            : (string) $last->end_date;
    }
}
