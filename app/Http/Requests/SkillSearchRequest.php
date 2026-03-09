<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class SkillSearchRequest extends FormRequest
{
    public function authorize(): bool
    {
        return true;
    }

    /**
     * @return array<string, mixed>
     */
    public function rules(): array
    {
        return [
            'q' => ['required', 'string', 'min:1'],
            'category' => ['nullable', 'string', 'in:domain,tool,certification,soft_skill,methodology,language'],
            'status' => ['nullable', 'string', 'in:candidate,active,deprecated,merged'],
            'limit' => [
                'nullable',
                'integer',
                'min:1',
                'max:'.(int) config('skills_graph.pagination_max_limit', 100),
            ],
        ];
    }

    /**
     * @return array{
     *     q: string,
     *     category: ?string,
     *     status: ?string,
     *     limit: ?int
     * }
     */
    public function searchParameters(): array
    {
        return [
            'q' => $this->string('q')->toString(),
            'category' => $this->filled('category')
                ? $this->string('category')->toString()
                : null,
            'status' => $this->filled('status')
                ? $this->string('status')->toString()
                : null,
            'limit' => $this->integer('limit') ?: null,
        ];
    }
}
