import {
  pgTable,
  uuid,
  text,
  boolean,
  integer,
  real,
  timestamp,
  jsonb,
  bigserial,
  bigint,
  uniqueIndex,
  index,
  check,
} from "drizzle-orm/pg-core";
import { sql } from "drizzle-orm";

// -- Custom column type helpers for pgvector and ltree --
// Drizzle doesn't have built-in support for these types, so we use `text`
// with explicit SQL casts in queries. The raw SQL migration creates the
// actual column types; Drizzle uses these definitions for type-safe selects/inserts.

export const skills = pgTable(
  "skills",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    externalId: text("external_id").unique().notNull(),
    canonicalName: text("canonical_name").notNull(),
    slug: text("slug").unique().notNull(),
    description: text("description"),
    status: text("status").notNull().default("candidate"),
    category: text("category"),
    path: text("path").notNull(), // ltree — cast in raw SQL queries
    embedding: text("embedding"), // vector(1024) — use pgvector helpers
    version: integer("version").notNull().default(1),
    source: text("source"),
    createdAt: timestamp("created_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
    metadata: jsonb("metadata").notNull().default({}),
  },
  (table) => [
    index("idx_skills_status").on(table.status),
    index("idx_skills_slug").on(table.slug),
    check(
      "skills_status_check",
      sql`${table.status} IN ('candidate', 'active', 'deprecated', 'merged')`,
    ),
    check(
      "skills_category_check",
      sql`${table.category} IN ('domain', 'tool', 'certification', 'soft_skill', 'methodology', 'language')`,
    ),
  ],
);

export const skillAliases = pgTable(
  "skill_aliases",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    skillId: uuid("skill_id")
      .notNull()
      .references(() => skills.id, { onDelete: "cascade" }),
    surfaceForm: text("surface_form").notNull(),
    locale: text("locale").notNull().default("en"),
    source: text("source").notNull().default("curated"),
    isPrimary: boolean("is_primary").notNull().default(false),
    aliasEmbedding: text("alias_embedding"), // vector(1024)
    createdAt: timestamp("created_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
  },
  (table) => [
    index("idx_aliases_skill_id").on(table.skillId),
    index("idx_aliases_locale").on(table.locale),
    check(
      "aliases_source_check",
      sql`${table.source} IN ('curated', 'llm_discovered', 'user_submitted')`,
    ),
  ],
);

export const skillRelationships = pgTable(
  "skill_relationships",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    sourceSkillId: uuid("source_skill_id")
      .notNull()
      .references(() => skills.id),
    targetSkillId: uuid("target_skill_id")
      .notNull()
      .references(() => skills.id),
    relationshipType: text("relationship_type").notNull(),
    confidence: real("confidence").notNull().default(1.0),
    weight: real("weight").notNull().default(1.0),
    provenance: text("provenance").notNull().default("human_curated"),
    status: text("status").notNull().default("active"),
    createdAt: timestamp("created_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
  },
  (table) => [
    uniqueIndex("idx_relationships_unique").on(
      table.sourceSkillId,
      table.targetSkillId,
      table.relationshipType,
    ),
    index("idx_relationships_source").on(table.sourceSkillId),
    index("idx_relationships_target").on(table.targetSkillId),
    index("idx_relationships_type").on(table.relationshipType),
    index("idx_relationships_provenance").on(table.provenance),
    check(
      "relationships_type_check",
      sql`${table.relationshipType} IN ('parent_of', 'child_of', 'related_to', 'requires', 'superseded_by')`,
    ),
    check(
      "relationships_provenance_check",
      sql`${table.provenance} IN ('human_curated', 'llm_predicted', 'embedding_similarity', 'empirical')`,
    ),
    check(
      "relationships_status_check",
      sql`${table.status} IN ('active', 'pending_review', 'rejected', 'deprecated')`,
    ),
    check(
      "relationships_confidence_check",
      sql`${table.confidence} >= 0 AND ${table.confidence} <= 1`,
    ),
    check(
      "relationships_weight_check",
      sql`${table.weight} >= 0 AND ${table.weight} <= 1`,
    ),
    check(
      "relationships_no_self_ref",
      sql`${table.sourceSkillId} != ${table.targetSkillId}`,
    ),
  ],
);

export const skillCoOccurrences = pgTable(
  "skill_co_occurrences",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    skillAId: uuid("skill_a_id")
      .notNull()
      .references(() => skills.id),
    skillBId: uuid("skill_b_id")
      .notNull()
      .references(() => skills.id),
    coOccurrenceCount: integer("co_occurrence_count").notNull().default(1),
    sourceTypeCounts: jsonb("source_type_counts").notNull().default({}),
    lastSeenAt: timestamp("last_seen_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
    createdAt: timestamp("created_at", { withTimezone: true })
      .notNull()
      .defaultNow(),
  },
  (table) => [
    uniqueIndex("idx_co_occurrences_pair").on(table.skillAId, table.skillBId),
    index("idx_co_occurrences_skill_a").on(table.skillAId),
    index("idx_co_occurrences_skill_b").on(table.skillBId),
    index("idx_co_occurrences_count").on(table.coOccurrenceCount),
    check(
      "co_occurrences_ordering",
      sql`${table.skillAId} < ${table.skillBId}`,
    ),
  ],
);

export const localeConfig = pgTable("locale_config", {
  locale: text("locale").primaryKey(),
  displayName: text("display_name").notNull(),
  isActive: boolean("is_active").notNull().default(true),
  coveragePct: real("coverage_pct").notNull().default(0),
});

export const graphChangelog = pgTable(
  "graph_changelog",
  {
    id: bigserial("id", { mode: "number" }).primaryKey(),
    graphVersion: bigint("graph_version", { mode: "number" }).notNull(),
    timestamp: timestamp("timestamp", { withTimezone: true })
      .notNull()
      .defaultNow(),
    actor: text("actor").notNull(),
    mutationType: text("mutation_type").notNull(),
    entityType: text("entity_type").notNull(),
    entityId: uuid("entity_id").notNull(),
    diffPayload: jsonb("diff_payload").notNull().default({}),
  },
  (table) => [
    index("idx_changelog_version").on(table.graphVersion),
    index("idx_changelog_entity").on(table.entityType, table.entityId),
    check(
      "changelog_mutation_type_check",
      sql`${table.mutationType} IN ('skill_created', 'skill_updated', 'skill_deprecated', 'skill_merged', 'alias_added', 'alias_removed', 'edge_created', 'edge_updated', 'edge_deprecated')`,
    ),
  ],
);
