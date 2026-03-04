<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class UpdateJobExpectationRequest extends FormRequest
{
    /**
     * Determine if the user is authorized to make this request.
     */
    public function authorize(): bool
    {
        return true;
    }

    /**
     * Get the validation rules that apply to the request.
     *
     * @return array<string, \Illuminate\Contracts\Validation\ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        return [
            'salary_min' => ['nullable', 'numeric', 'min:0'],
            'salary_max' => ['nullable', 'numeric', 'min:0', 'gte:salary_min'],
            'salary_currency' => ['nullable', 'string', 'max:3'],
            'work_type' => ['nullable', 'string', 'max:255'],
            'preferred_location' => ['nullable', 'string', 'max:255'],
            'notice_period' => ['nullable', 'string', 'max:255'],
            'earliest_start_date' => ['nullable', 'date'],
            'benefits_priorities' => ['nullable', 'array'],
            'benefits_priorities.*' => ['string'],
            'notes' => ['nullable', 'string'],
        ];
    }
}
