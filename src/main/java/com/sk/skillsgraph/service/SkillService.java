package com.sk.skillsgraph.service;

import com.sk.skillsgraph.config.AppConstants;
import com.sk.skillsgraph.domain.Alias;
import com.sk.skillsgraph.domain.Skill;
import com.sk.skillsgraph.dto.AliasDto;
import com.sk.skillsgraph.dto.EdgeDto;
import com.sk.skillsgraph.dto.Enums.AliasSource;
import com.sk.skillsgraph.dto.Enums.EdgeStatus;
import com.sk.skillsgraph.dto.Enums.Provenance;
import com.sk.skillsgraph.dto.Enums.RelationshipType;
import com.sk.skillsgraph.dto.Enums.SkillCategory;
import com.sk.skillsgraph.dto.Enums.SkillStatus;
import com.sk.skillsgraph.dto.QueryParams.DepthQuery;
import com.sk.skillsgraph.dto.QueryParams.ListSkillsQuery;
import com.sk.skillsgraph.dto.SkillDto.CreateSkillRequest;
import com.sk.skillsgraph.dto.SkillDto.ListSkillsResponse;
import com.sk.skillsgraph.dto.SkillDto.SkillPathNode;
import com.sk.skillsgraph.dto.SkillDto.SkillResponse;
import com.sk.skillsgraph.dto.SkillDto.SkillSummary;
import com.sk.skillsgraph.dto.SkillDto.UpdateSkillRequest;
import com.sk.skillsgraph.repository.neo4j.SkillRepository;
import com.sk.skillsgraph.service.ChangelogService.MutationType;
import com.sk.skillsgraph.util.AppExceptions.DuplicateSkillException;
import com.sk.skillsgraph.util.AppExceptions.SkillNotFoundException;
import com.sk.skillsgraph.util.AppExceptions.ValidationException;
import com.sk.skillsgraph.util.SlugUtils;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {

    private final Neo4jClient neo4jClient;
    private final SkillRepository skillRepository;
    private final GuardrailService guardrailService;
    private final ChangelogService changelogService;
    private final EmbeddingService embeddingService;
    private final SearchService searchService;

    public SkillService(
            Neo4jClient neo4jClient,
            SkillRepository skillRepository,
            GuardrailService guardrailService,
            ChangelogService changelogService,
            EmbeddingService embeddingService,
            SearchService searchService
    ) {
        this.neo4jClient = neo4jClient;
        this.skillRepository = skillRepository;
        this.guardrailService = guardrailService;
        this.changelogService = changelogService;
        this.embeddingService = embeddingService;
        this.searchService = searchService;
    }

    @Transactional
    public SkillResponse create(CreateSkillRequest input) {
        String sanitizedName = guardrailService.sanitizeName(input.canonicalName());
        if (sanitizedName.isBlank()) {
            throw new ValidationException("canonical_name must not be blank", 400);
        }
        if (canonicalNameExists(sanitizedName, null)) {
            throw new DuplicateSkillException(sanitizedName);
        }

        Instant now = Instant.now();

        String skillId = UUID.randomUUID().toString();
        String externalId = "SK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String slug = nextAvailableSlug(SlugUtils.generateSlug(sanitizedName), null);
        SkillCategory category = input.category() == null ? SkillCategory.domain : input.category();
        SkillStatus status = input.status() == null ? SkillStatus.candidate : input.status();
        if (status == SkillStatus.active) {
            guardrailService.validateActivation(skillId, category);
        }

        String aliasId = UUID.randomUUID().toString();
        neo4jClient.query("""
                        CREATE (s:Skill {
                            id: $skillId,
                            externalId: $externalId,
                            canonicalName: $canonicalName,
                            slug: $slug,
                            category: $category,
                            status: $status,
                            source: 'human_curated',
                            version: 1,
                            createdAt: datetime($now),
                            updatedAt: datetime($now)
                        })
                        SET s.description = $description
                        CREATE (a:Alias {
                            id: $aliasId,
                            surfaceForm: $canonicalName,
                            locale: 'en',
                            isPrimary: true,
                            source: $aliasSource,
                            createdAt: datetime($now)
                        })
                        CREATE (s)-[:HAS_ALIAS]->(a)
                        """)
                .bind(skillId).to("skillId")
                .bind(externalId).to("externalId")
                .bind(sanitizedName).to("canonicalName")
                .bind(slug).to("slug")
                .bind(input.description()).to("description")
                .bind(category.name()).to("category")
                .bind(status.name()).to("status")
                .bind(aliasId).to("aliasId")
                .bind(AliasSource.curated.name()).to("aliasSource")
                .bind(now.toString()).to("now")
                .run();
        embeddingService.embedSkillAsync(skillId, sanitizedName, input.description(), status.name());
        searchService.indexSkillAsync(skillId);

        changelogService.record(
                "system",
                MutationType.skill_created,
                "skill",
                skillId,
                Map.of(
                        "canonical_name", sanitizedName,
                        "slug", slug,
                        "category", category.name(),
                        "status", status.name()
                )
        );

        return getById(skillId);
    }

    public SkillResponse getById(String idOrExternalIdOrSlug) {
        Skill skillEntity = findSkillEntity(idOrExternalIdOrSlug)
                .orElseThrow(() -> new SkillNotFoundException(idOrExternalIdOrSlug));
        SkillCore skill = toSkillCore(skillEntity);

        List<AliasDto.AliasResponse> aliases = toAliasResponses(skillEntity.getAliases());
        List<EdgeDto.EdgeResponse> relationships = fetchDirectEdges(skill.id());

        return new SkillResponse(
                skill.id(),
                skill.externalId(),
                skill.canonicalName(),
                skill.slug(),
                skill.description(),
                parseSkillCategory(skill.category()),
                parseSkillStatus(skill.status()),
                skill.version(),
                skill.source(),
                skill.createdAt(),
                skill.updatedAt(),
                aliases,
                relationships
        );
    }

    @Transactional
    public SkillResponse update(String idOrExternalIdOrSlug, UpdateSkillRequest input) {
        Skill existingSkill = findSkillEntity(idOrExternalIdOrSlug)
                .orElseThrow(() -> new SkillNotFoundException(idOrExternalIdOrSlug));
        SkillCore existing = toSkillCore(existingSkill);

        String canonicalName = input.canonicalName() == null
                ? existing.canonicalName()
                : guardrailService.sanitizeName(input.canonicalName());
        if (canonicalName.isBlank()) {
            throw new ValidationException("canonical_name must not be blank", 400);
        }
        if (!canonicalName.equals(existing.canonicalName()) && canonicalNameExists(canonicalName, existing.id())) {
            throw new DuplicateSkillException(canonicalName);
        }

        String slug = canonicalName.equals(existing.canonicalName())
                ? existing.slug()
                : nextAvailableSlug(SlugUtils.generateSlug(canonicalName), existing.id());
        String description = input.description() == null ? existing.description() : input.description();
        SkillCategory category = input.category() == null ? parseSkillCategory(existing.category()) : input.category();
        SkillStatus status = input.status() == null ? parseSkillStatus(existing.status()) : input.status();
        SkillStatus existingStatus = parseSkillStatus(existing.status());
        SkillCategory existingCategory = parseSkillCategory(existing.category());
        boolean canonicalNameChanged = !canonicalName.equals(existing.canonicalName());
        boolean descriptionChanged = !Objects.equals(description, existing.description());
        boolean statusChanged = status != existingStatus;
        if (status == SkillStatus.active && (existingStatus != SkillStatus.active || category != existingCategory)) {
            guardrailService.validateActivation(existing.id(), category);
        }

        int nextVersion = (existingSkill.getVersion() == null ? 1 : existingSkill.getVersion()) + 1;
        Instant updatedAt = Instant.now();
        neo4jClient.query("""
                        MATCH (s:Skill {id: $skillId})
                        SET s.canonicalName = $canonicalName,
                            s.slug = $slug,
                            s.description = $description,
                            s.category = $category,
                            s.status = $status,
                            s.version = $version,
                            s.updatedAt = datetime($updatedAt)
                        """)
                .bind(existing.id()).to("skillId")
                .bind(canonicalName).to("canonicalName")
                .bind(slug).to("slug")
                .bind(description).to("description")
                .bind(category.name()).to("category")
                .bind(status.name()).to("status")
                .bind(nextVersion).to("version")
                .bind(updatedAt.toString()).to("updatedAt")
                .run();

        if (canonicalNameChanged) {
            long updatedAliasCount = neo4jClient.query("""
                            MATCH (s:Skill {id: $skillId})-[:HAS_ALIAS]->(a:Alias)
                            WHERE toLower(coalesce(a.locale, 'en')) = 'en'
                              AND coalesce(a.isPrimary, false) = true
                            SET a.surfaceForm = $surfaceForm
                            RETURN count(a) AS updated
                            """)
                    .bind(existing.id()).to("skillId")
                    .bind(canonicalName).to("surfaceForm")
                    .fetchAs(Long.class)
                    .one()
                    .orElse(0L);
            if (updatedAliasCount == 0L) {
                neo4jClient.query("""
                                MATCH (s:Skill {id: $skillId})
                                CREATE (a:Alias {
                                    id: $aliasId,
                                    surfaceForm: $surfaceForm,
                                    locale: 'en',
                                    isPrimary: true,
                                    source: $source,
                                    createdAt: datetime($createdAt)
                                })
                                CREATE (s)-[:HAS_ALIAS]->(a)
                                """)
                        .bind(existing.id()).to("skillId")
                        .bind(UUID.randomUUID().toString()).to("aliasId")
                        .bind(canonicalName).to("surfaceForm")
                        .bind(AliasSource.curated.name()).to("source")
                        .bind(updatedAt.toString()).to("createdAt")
                        .run();
            }
        }

        if (canonicalNameChanged || descriptionChanged) {
            embeddingService.embedSkillAsync(existing.id(), canonicalName, description, status.name());
        } else if (statusChanged) {
            embeddingService.updateSkillStatus(existing.id(), status.name());
        }
        searchService.indexSkillAsync(existing.id());

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("before", Map.of(
                "canonical_name", existing.canonicalName(),
                "slug", existing.slug(),
                "description", existing.description(),
                "category", existing.category(),
                "status", existing.status()
        ));
        diff.put("after", Map.of(
                "canonical_name", canonicalName,
                "slug", slug,
                "description", description,
                "category", category.name(),
                "status", status.name()
        ));
        changelogService.record("system", MutationType.skill_updated, "skill", existing.id(), diff);

        return getById(existing.id());
    }

    public ListSkillsResponse list(ListSkillsQuery query) {
        int limit = query.normalizedLimit();
        int offset = query.normalizedOffset();

        if (query.q() != null && !query.q().isBlank()) {
            return search(query, limit, offset);
        }

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();
        params.put("limit", limit);
        params.put("offset", offset);

        if (query.status() != null) {
            where.append(" AND s.status = $status ");
            params.put("status", query.status().name());
        }
        if (query.category() != null) {
            where.append(" AND s.category = $category ");
            params.put("category", query.category().name());
        }
        String countQuery = "MATCH (s:Skill) " + where + " RETURN count(s) AS total";
        long total = neo4jClient.query(countQuery)
                .bindAll(params)
                .fetchAs(Long.class)
                .one()
                .orElse(0L);

        String listQuery = """
                MATCH (s:Skill)
                %s
                RETURN s.id AS id,
                       s.externalId AS externalId,
                       s.canonicalName AS canonicalName,
                       s.slug AS slug,
                       s.category AS category,
                       s.status AS status
                ORDER BY s.canonicalName ASC
                SKIP $offset
                LIMIT $limit
                """.formatted(where);

        List<SkillSummary> items = neo4jClient.query(listQuery)
                .bindAll(params)
                .fetch()
                .all()
                .stream()
                .map(this::toSkillSummary)
                .toList();

        return new ListSkillsResponse(items, total, limit, offset);
    }

    private ListSkillsResponse search(ListSkillsQuery query, int limit, int offset) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();
        params.put("limit", limit);
        params.put("offset", offset);
        params.put("searchQuery", toFullTextQuery(query.q()));

        if (query.status() != null) {
            where.append(" AND s.status = $status ");
            params.put("status", query.status().name());
        }
        if (query.category() != null) {
            where.append(" AND s.category = $category ");
            params.put("category", query.category().name());
        }

        String countQuery = """
                CALL {
                    CALL db.index.fulltext.queryNodes('skill_fulltext', $searchQuery) YIELD node, score
                    RETURN node AS s, score
                    UNION
                    CALL db.index.fulltext.queryNodes('alias_fulltext', $searchQuery) YIELD node, score
                    MATCH (s:Skill)-[:HAS_ALIAS]->(node)
                    RETURN s, score
                }
                WITH s, max(score) AS bestScore
                %s
                RETURN count(DISTINCT s) AS total
                """.formatted(where);

        long total = neo4jClient.query(countQuery)
                .bindAll(params)
                .fetchAs(Long.class)
                .one()
                .orElse(0L);

        String listQuery = """
                CALL {
                    CALL db.index.fulltext.queryNodes('skill_fulltext', $searchQuery) YIELD node, score
                    RETURN node AS s, score
                    UNION
                    CALL db.index.fulltext.queryNodes('alias_fulltext', $searchQuery) YIELD node, score
                    MATCH (s:Skill)-[:HAS_ALIAS]->(node)
                    RETURN s, score
                }
                WITH s, max(score) AS bestScore
                %s
                RETURN DISTINCT s.id AS id,
                                s.externalId AS externalId,
                                s.canonicalName AS canonicalName,
                                s.slug AS slug,
                                s.category AS category,
                                s.status AS status,
                                bestScore
                ORDER BY bestScore DESC, canonicalName ASC
                SKIP $offset
                LIMIT $limit
                """.formatted(where);

        List<SkillSummary> items = neo4jClient.query(listQuery)
                .bindAll(params)
                .fetch()
                .all()
                .stream()
                .map(this::toSkillSummary)
                .toList();

        return new ListSkillsResponse(items, total, limit, offset);
    }

    public List<SkillPathNode> getAncestors(String idOrExternalIdOrSlug, DepthQuery depthQuery) {
        String skillId = resolveSkillId(idOrExternalIdOrSlug);
        int depth = depthQuery.normalizedDepth();

        return neo4jClient.query("""
                        MATCH path = (s:Skill {id: $skillId})<-[:PARENT_OF*1..%d]-(ancestor:Skill)
                        RETURN ancestor.id AS id,
                               ancestor.externalId AS externalId,
                               ancestor.canonicalName AS canonicalName,
                               ancestor.slug AS slug,
                               ancestor.category AS category,
                               ancestor.status AS status,
                               min(length(path)) AS depth
                        ORDER BY depth ASC, canonicalName ASC
                        """.formatted(depth))
                .bind(skillId).to("skillId")
                .fetch()
                .all()
                .stream()
                .map(row -> new SkillPathNode(toSkillSummary(row), toInt(row.get("depth"), 1)))
                .toList();
    }

    public List<SkillPathNode> getDescendants(String idOrExternalIdOrSlug, DepthQuery depthQuery) {
        String skillId = resolveSkillId(idOrExternalIdOrSlug);
        int depth = depthQuery.normalizedDepth();

        return neo4jClient.query("""
                        MATCH path = (parent:Skill {id: $skillId})-[:PARENT_OF*1..%d]->(descendant:Skill)
                        RETURN descendant.id AS id,
                               descendant.externalId AS externalId,
                               descendant.canonicalName AS canonicalName,
                               descendant.slug AS slug,
                               descendant.category AS category,
                               descendant.status AS status,
                               min(length(path)) AS depth
                        ORDER BY depth ASC, canonicalName ASC
                        """.formatted(depth))
                .bind(skillId).to("skillId")
                .fetch()
                .all()
                .stream()
                .map(row -> new SkillPathNode(toSkillSummary(row), toInt(row.get("depth"), 1)))
                .toList();
    }

    public List<SkillSummary> getRoots() {
        return neo4jClient.query("""
                        MATCH (s:Skill {status: 'active'})
                        WHERE NOT ()-[:PARENT_OF]->(s)
                        RETURN s.id AS id,
                               s.externalId AS externalId,
                               s.canonicalName AS canonicalName,
                               s.slug AS slug,
                               s.category AS category,
                               s.status AS status
                        ORDER BY canonicalName ASC
                        """)
                .fetch()
                .all()
                .stream()
                .map(this::toSkillSummary)
                .toList();
    }

    public String resolveSkillId(String idOrExternalIdOrSlug) {
        return findSkillEntity(idOrExternalIdOrSlug)
                .map(Skill::getId)
                .orElseThrow(() -> new SkillNotFoundException(idOrExternalIdOrSlug));
    }

    private List<AliasDto.AliasResponse> toAliasResponses(java.util.Set<Alias> aliases) {
        return aliases.stream()
                .sorted(java.util.Comparator
                        .comparing((Alias alias) -> Boolean.TRUE.equals(alias.getIsPrimary()) ? 0 : 1)
                        .thenComparing(alias -> alias.getSurfaceForm() == null ? "" : alias.getSurfaceForm(),
                                String.CASE_INSENSITIVE_ORDER))
                .map(alias -> new AliasDto.AliasResponse(
                        alias.getId(),
                        alias.getSurfaceForm(),
                        alias.getLocale() == null || alias.getLocale().isBlank() ? "en" : alias.getLocale(),
                        Boolean.TRUE.equals(alias.getIsPrimary()),
                        parseAliasSource(alias.getSource()),
                        alias.getCreatedAt()
                ))
                .toList();
    }

    private List<EdgeDto.EdgeResponse> fetchDirectEdges(String key) {
        return neo4jClient.query("""
                        MATCH (s:Skill)
                        WHERE s.id = $key OR s.externalId = $key OR s.slug = $key
                        MATCH (s)-[r]-(other:Skill)
                        WHERE type(r) IN ['PARENT_OF', 'RELATED_TO', 'REQUIRES', 'SUPERSEDED_BY']
                        RETURN r.id AS id,
                               startNode(r).id AS sourceSkillId,
                               endNode(r).id AS targetSkillId,
                               type(r) AS relationshipType,
                               coalesce(r.confidence, 1.0) AS confidence,
                               coalesce(r.provenance, 'human_curated') AS provenance,
                               coalesce(r.status, 'active') AS status,
                               r.createdAt AS createdAt,
                               r.updatedAt AS updatedAt,
                               other.canonicalName AS targetSkillName
                        ORDER BY relationshipType ASC, targetSkillName ASC
                        """)
                .bind(key).to("key")
                .fetch()
                .all()
                .stream()
                .map(this::toEdgeResponse)
                .toList();
    }

    private Optional<Skill> findSkillEntity(String key) {
        return findSkillEntityViaCypher(key);
    }

    private Optional<Skill> findSkillEntityViaCypher(String key) {
        return neo4jClient.query("""
                        MATCH (s:Skill)
                        WHERE s.id = $key OR s.externalId = $key OR s.slug = $key
                        WITH s,
                             CASE
                                 WHEN s.id = $key THEN 0
                                 WHEN s.externalId = $key THEN 1
                                 ELSE 2
                             END AS priority
                        ORDER BY priority ASC
                        LIMIT 1
                        OPTIONAL MATCH (s)-[:HAS_ALIAS]->(a:Alias)
                        WITH s, collect(a) AS aliases
                        RETURN s.id AS id,
                               s.externalId AS externalId,
                               s.canonicalName AS canonicalName,
                               s.slug AS slug,
                               s.description AS description,
                               s.status AS status,
                               s.category AS category,
                               s.version AS version,
                               s.source AS source,
                               s.createdAt AS createdAt,
                               s.updatedAt AS updatedAt,
                               [alias IN aliases WHERE alias IS NOT NULL | {
                                   id: alias.id,
                                   surfaceForm: alias.surfaceForm,
                                   locale: alias.locale,
                                   isPrimary: alias.isPrimary,
                                   source: alias.source,
                                   createdAt: alias.createdAt
                               }] AS aliases
                        """)
                .bind(key).to("key")
                .fetch()
                .one()
                .map(this::mapSkillWithAliases);
    }

    private Skill mapSkillWithAliases(Map<String, Object> row) {
        Skill skill = new Skill();
        skill.setId(asString(row.get("id")));
        skill.setExternalId(asString(row.get("externalId")));
        skill.setCanonicalName(asString(row.get("canonicalName")));
        skill.setSlug(asString(row.get("slug")));
        skill.setDescription(asString(row.get("description")));
        skill.setStatus(asString(row.get("status")));
        skill.setCategory(asString(row.get("category")));
        skill.setVersion(toInt(row.get("version"), 1));
        skill.setSource(asString(row.get("source")));
        skill.setCreatedAt(toInstant(row.get("createdAt")));
        skill.setUpdatedAt(toInstant(row.get("updatedAt")));

        Object aliasRaw = row.get("aliases");
        if (aliasRaw instanceof List<?> aliasRows) {
            for (Object aliasItem : aliasRows) {
                if (!(aliasItem instanceof Map<?, ?> aliasMap)) {
                    continue;
                }
                Alias alias = new Alias();
                alias.setId(asString(aliasMap.get("id")));
                alias.setSurfaceForm(asString(aliasMap.get("surfaceForm")));
                alias.setLocale(asString(aliasMap.get("locale")));
                alias.setIsPrimary(toBoolean(aliasMap.get("isPrimary"), false));
                alias.setSource(asString(aliasMap.get("source")));
                alias.setCreatedAt(toInstant(aliasMap.get("createdAt")));
                if (alias.getId() != null) {
                    skill.getAliases().add(alias);
                }
            }
        }

        return skill;
    }

    private SkillCore toSkillCore(Skill skill) {
        return new SkillCore(
                skill.getId(),
                skill.getExternalId(),
                skill.getCanonicalName(),
                skill.getSlug(),
                skill.getDescription(),
                skill.getCategory(),
                skill.getStatus(),
                skill.getVersion() == null ? 1 : skill.getVersion(),
                skill.getSource() == null ? "human_curated" : skill.getSource(),
                skill.getCreatedAt(),
                skill.getUpdatedAt()
        );
    }

    private SkillSummary toSkillSummary(Map<String, Object> row) {
        return new SkillSummary(
                asString(row.get("id")),
                asString(row.get("externalId")),
                asString(row.get("canonicalName")),
                asString(row.get("slug")),
                parseSkillCategory(asString(row.get("category"))),
                parseSkillStatus(asString(row.get("status")))
        );
    }

    private EdgeDto.EdgeResponse toEdgeResponse(Map<String, Object> row) {
        return new EdgeDto.EdgeResponse(
                asString(row.get("id")),
                asString(row.get("sourceSkillId")),
                asString(row.get("targetSkillId")),
                parseRelationshipType(asString(row.get("relationshipType"))),
                toDouble(row.get("confidence"), 1.0D),
                parseProvenance(asString(row.get("provenance"))),
                parseEdgeStatus(asString(row.get("status"))),
                toInstant(row.get("createdAt")),
                toInstant(row.get("updatedAt")),
                asString(row.get("targetSkillName"))
        );
    }

    private String nextAvailableSlug(String baseSlug, String currentSkillId) {
        String slug = baseSlug;
        int suffix = 2;
        while (slugExists(slug, currentSkillId)) {
            String candidate = baseSlug + "-" + suffix++;
            slug = candidate.length() > AppConstants.SLUG_MAX_LENGTH
                    ? candidate.substring(0, AppConstants.SLUG_MAX_LENGTH)
                    : candidate;
        }
        return slug;
    }

    private boolean slugExists(String slug, String currentSkillId) {
        String query = """
                MATCH (s:Skill {slug: $slug})
                WHERE $currentSkillId IS NULL OR s.id <> $currentSkillId
                RETURN count(s) > 0 AS exists
                """;
        return neo4jClient.query(query)
                .bind(slug).to("slug")
                .bind(currentSkillId).to("currentSkillId")
                .fetchAs(Boolean.class)
                .one()
                .orElse(false);
    }

    private boolean canonicalNameExists(String canonicalName, String currentSkillId) {
        String query = """
                MATCH (s:Skill)
                WHERE toLower(coalesce(s.canonicalName, '')) = toLower($canonicalName)
                  AND ($currentSkillId IS NULL OR s.id <> $currentSkillId)
                RETURN count(s) > 0 AS exists
                """;
        return neo4jClient.query(query)
                .bind(canonicalName).to("canonicalName")
                .bind(currentSkillId).to("currentSkillId")
                .fetchAs(Boolean.class)
                .one()
                .orElse(false);
    }

    private String toFullTextQuery(String rawQuery) {
        String sanitized = rawQuery == null ? "" : rawQuery.trim();
        if (sanitized.isBlank()) {
            return "*";
        }

        String fuzzy = Arrays.stream(sanitized.split("\\s+"))
                .map(term -> term.replaceAll("[^\\p{L}\\p{N}_-]", ""))
                .filter(term -> !term.isBlank())
                .map(term -> term.length() <= 3 ? term + "*" : term + "~2")
                .reduce((left, right) -> left + " AND " + right)
                .orElse("");
        return fuzzy.isBlank() ? "*" : fuzzy;
    }

    private SkillCategory parseSkillCategory(String value) {
        if (value == null || value.isBlank()) {
            return SkillCategory.domain;
        }
        try {
            return SkillCategory.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return SkillCategory.domain;
        }
    }

    private SkillStatus parseSkillStatus(String value) {
        if (value == null || value.isBlank()) {
            return SkillStatus.candidate;
        }
        try {
            return SkillStatus.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return SkillStatus.candidate;
        }
    }

    private AliasSource parseAliasSource(String value) {
        if (value == null || value.isBlank()) {
            return AliasSource.curated;
        }
        try {
            return AliasSource.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return AliasSource.curated;
        }
    }

    private RelationshipType parseRelationshipType(String label) {
        if (label == null || label.isBlank()) {
            return RelationshipType.related_to;
        }
        try {
            return RelationshipType.valueOf(label.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return RelationshipType.related_to;
        }
    }

    private Provenance parseProvenance(String value) {
        if (value == null || value.isBlank()) {
            return Provenance.human_curated;
        }
        try {
            return Provenance.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return Provenance.human_curated;
        }
    }

    private EdgeStatus parseEdgeStatus(String value) {
        if (value == null || value.isBlank()) {
            return EdgeStatus.active;
        }
        try {
            return EdgeStatus.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return EdgeStatus.active;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean toBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        String normalized = value.toString().trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        return fallback;
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double toDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        try {
            return Instant.parse(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private record SkillCore(
            String id,
            String externalId,
            String canonicalName,
            String slug,
            String description,
            String category,
            String status,
            Integer version,
            String source,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
