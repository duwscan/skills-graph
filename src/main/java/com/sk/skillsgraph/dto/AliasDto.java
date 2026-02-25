package com.sk.skillsgraph.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sk.skillsgraph.dto.Enums.AliasSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AliasDto {

    private AliasDto() {
    }

    public record CreateAliasRequest(
            @JsonProperty("surface_form")
            @NotBlank
            @Size(max = 200)
            String surfaceForm,
            @Size(max = 16)
            String locale,
            AliasSource source,
            @JsonProperty("is_primary")
            Boolean isPrimary
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateAliasRequest(
            @JsonProperty("surface_form")
            @Size(max = 200)
            String surfaceForm,
            @Size(max = 16)
            String locale,
            AliasSource source,
            @JsonProperty("is_primary")
            Boolean isPrimary
    ) {
    }

    public record AliasResponse(
            String id,
            @JsonProperty("surface_form") String surfaceForm,
            String locale,
            @JsonProperty("is_primary") boolean isPrimary,
            AliasSource source,
            @JsonProperty("created_at") Instant createdAt
    ) {
    }
}
