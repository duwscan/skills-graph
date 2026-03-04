<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class WorkHistoryResource extends JsonResource
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
            'candidate_id' => $this->candidate_id,
            'cv_id' => $this->cv_id,
            'company_name' => $this->company_name,
            'position' => $this->position,
            'start_date' => $this->start_date?->toDateString(),
            'end_date' => $this->end_date?->toDateString(),
            'responsibilities' => $this->responsibilities,
            'created_at' => $this->created_at,
            'updated_at' => $this->updated_at,
        ];
    }
}
