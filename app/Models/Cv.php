<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Cv extends Model
{
    /**
     * The attributes that are mass assignable.
     *
     * @var list<string>
     */
    protected $fillable = [
        'basic_info',
        'experiences',
        'educations',
        'certifications',
        'projects',
        'awards',
        'skills',
    ];
}
