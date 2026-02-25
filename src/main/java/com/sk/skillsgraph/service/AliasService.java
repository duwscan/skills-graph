package com.sk.skillsgraph.service;

import com.sk.skillsgraph.domain.Alias;
import com.sk.skillsgraph.domain.Skill;
import com.sk.skillsgraph.dto.AliasDto.AliasResponse;
import com.sk.skillsgraph.dto.AliasDto.CreateAliasRequest;
import com.sk.skillsgraph.dto.AliasDto.UpdateAliasRequest;
import com.sk.skillsgraph.dto.Enums.AliasSource;
import com.sk.skillsgraph.repository.AliasRepository;
import com.sk.skillsgraph.repository.SkillRepository;
import com.sk.skillsgraph.service.ChangelogService.MutationType;
import com.sk.skillsgraph.util.AppExceptions.ValidationException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AliasService {

    private static final Comparator<Alias> ALIAS_ORDER = Comparator
            .comparing((Alias alias) -> Boolean.TRUE.equals(alias.getIsPrimary()) ? 0 : 1)
            .thenComparing(alias -> alias.getSurfaceForm() == null ? "" : alias.getSurfaceForm(), String.CASE_INSENSITIVE_ORDER);

    private final AliasRepository aliasRepository;
    private final SkillRepository skillRepository;
    private final SkillService skillService;
    private final ChangelogService changelogService;

    public AliasService(
            AliasRepository aliasRepository,
            SkillRepository skillRepository,
            SkillService skillService,
            ChangelogService changelogService
    ) {
        this.aliasRepository = aliasRepository;
        this.skillRepository = skillRepository;
        this.skillService = skillService;
        this.changelogService = changelogService;
    }

    @Transactional
    public AliasResponse create(String idOrExternalIdOrSlug, CreateAliasRequest input) {
        String skillId = skillService.resolveSkillId(idOrExternalIdOrSlug);
        Skill skill = requireSkill(skillId);

        String locale = normalizeLocale(input.locale());
        String surfaceForm = normalizeSurfaceForm(input.surfaceForm());
        AliasSource source = input.source() == null ? AliasSource.curated : input.source();
        boolean isPrimary = Boolean.TRUE.equals(input.isPrimary());

        if (isPrimary) {
            skill.getAliases().stream()
                    .filter(alias -> locale.equalsIgnoreCase(aliasLocale(alias)))
                    .forEach(alias -> alias.setIsPrimary(false));
        }

        Alias alias = new Alias();
        alias.setId(UUID.randomUUID().toString());
        alias.setSurfaceForm(surfaceForm);
        alias.setLocale(locale);
        alias.setSource(source.name());
        alias.setIsPrimary(isPrimary);
        alias.setCreatedAt(Instant.now());
        skill.getAliases().add(alias);
        skillRepository.save(skill);

        changelogService.record(
                "system",
                MutationType.alias_added,
                "alias",
                alias.getId(),
                Map.of("skill_id", skillId, "surface_form", surfaceForm, "locale", locale)
        );

        return toAliasResponse(alias);
    }

    public List<AliasResponse> listBySkill(String idOrExternalIdOrSlug, String locale) {
        String skillId = skillService.resolveSkillId(idOrExternalIdOrSlug);
        Skill skill = requireSkill(skillId);
        String localeFilter = locale == null || locale.isBlank() ? null : normalizeLocale(locale);

        return skill.getAliases().stream()
                .filter(alias -> localeFilter == null || localeFilter.equalsIgnoreCase(aliasLocale(alias)))
                .sorted(ALIAS_ORDER)
                .map(this::toAliasResponse)
                .toList();
    }

    @Transactional
    public AliasResponse update(String idOrExternalIdOrSlug, String aliasId, UpdateAliasRequest input) {
        String skillId = skillService.resolveSkillId(idOrExternalIdOrSlug);
        Skill skill = requireSkill(skillId);
        Alias alias = findAliasInSkill(skill, aliasId);

        String locale = input.locale() == null ? aliasLocale(alias) : normalizeLocale(input.locale());
        String surfaceForm = input.surfaceForm() == null ? alias.getSurfaceForm() : normalizeSurfaceForm(input.surfaceForm());
        AliasSource source = input.source() == null ? parseAliasSource(alias.getSource()) : input.source();
        boolean isPrimary = input.isPrimary() == null ? Boolean.TRUE.equals(alias.getIsPrimary()) : input.isPrimary();

        if (isPrimary) {
            skill.getAliases().stream()
                    .filter(existing -> locale.equalsIgnoreCase(aliasLocale(existing)))
                    .forEach(existing -> existing.setIsPrimary(false));
        }

        alias.setSurfaceForm(surfaceForm);
        alias.setLocale(locale);
        alias.setSource(source.name());
        alias.setIsPrimary(isPrimary);
        skillRepository.save(skill);

        changelogService.record(
                "system",
                MutationType.alias_updated,
                "alias",
                aliasId,
                Map.of("surface_form", surfaceForm, "locale", locale, "is_primary", isPrimary)
        );

        return toAliasResponse(alias);
    }

    @Transactional
    public void delete(String idOrExternalIdOrSlug, String aliasId) {
        String skillId = skillService.resolveSkillId(idOrExternalIdOrSlug);
        Skill skill = requireSkill(skillId);
        Alias alias = findAliasInSkill(skill, aliasId);
        String locale = aliasLocale(alias);

        if (Boolean.TRUE.equals(alias.getIsPrimary())) {
            long primaryCount = skill.getAliases().stream()
                    .filter(existing -> locale.equalsIgnoreCase(aliasLocale(existing)))
                    .filter(existing -> Boolean.TRUE.equals(existing.getIsPrimary()))
                    .count();
            if (primaryCount <= 1L) {
                throw new ValidationException("Cannot delete the last primary alias for locale " + locale, 422);
            }
        }

        skill.getAliases().removeIf(existing -> aliasId.equals(existing.getId()));
        skillRepository.save(skill);
        aliasRepository.deleteById(aliasId);

        changelogService.record(
                "system",
                MutationType.alias_removed,
                "alias",
                aliasId,
                Map.of("skill_id", skillId, "locale", locale)
        );
    }

    private Skill requireSkill(String skillId) {
        return skillRepository.findById(skillId)
                .orElseThrow(() -> new ValidationException("Skill not found: " + skillId, 404));
    }

    private Alias findAliasInSkill(Skill skill, String aliasId) {
        return skill.getAliases().stream()
                .filter(alias -> aliasId.equals(alias.getId()))
                .findFirst()
                .orElseThrow(() -> new ValidationException("Alias not found: " + aliasId, 404));
    }

    private AliasResponse toAliasResponse(Alias alias) {
        return new AliasResponse(
                alias.getId(),
                alias.getSurfaceForm(),
                aliasLocale(alias),
                Boolean.TRUE.equals(alias.getIsPrimary()),
                parseAliasSource(alias.getSource()),
                alias.getCreatedAt()
        );
    }

    private String aliasLocale(Alias alias) {
        String locale = alias.getLocale();
        return locale == null || locale.isBlank() ? "en" : locale;
    }

    private AliasSource parseAliasSource(String source) {
        if (source == null || source.isBlank()) {
            return AliasSource.curated;
        }
        try {
            return AliasSource.valueOf(source);
        } catch (IllegalArgumentException ignored) {
            return AliasSource.curated;
        }
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "en";
        }
        return locale.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSurfaceForm(String surfaceForm) {
        if (surfaceForm == null || surfaceForm.isBlank()) {
            throw new ValidationException("surface_form must not be blank", 400);
        }
        return surfaceForm.trim();
    }
}
