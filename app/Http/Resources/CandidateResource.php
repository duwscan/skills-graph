<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class CandidateResource extends JsonResource
{
    /**
     * Transform the resource into an array.
     *
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'user_id' => $this->user_id,
            'first_name' => $this->first_name,
            'last_name' => $this->last_name,
            'email' => $this->email,
            'phone' => $this->phone,
            'summary' => $this->summary,
            'current_or_last_company' => $this->current_or_last_company,
            'first_work_start_date' => $this->first_work_start_date,
            'last_work_end_date' => $this->last_work_end_date,
            'created_at' => $this->created_at,
            'updated_at' => $this->updated_at,
            'work_histories' => WorkHistoryResource::collection($this->whenLoaded('workHistories')),
            'job_expectation' => $this->whenLoaded('jobExpectation', fn () => new JobExpectationResource($this->jobExpectation)),
        ];
    }
}
