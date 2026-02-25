CREATE CONSTRAINT skill_id_unique IF NOT EXISTS
FOR (s:Skill)
REQUIRE s.id IS UNIQUE;

CREATE CONSTRAINT skill_external_id_unique IF NOT EXISTS
FOR (s:Skill)
REQUIRE s.externalId IS UNIQUE;

CREATE CONSTRAINT skill_slug_unique IF NOT EXISTS
FOR (s:Skill)
REQUIRE s.slug IS UNIQUE;

CREATE CONSTRAINT alias_id_unique IF NOT EXISTS
FOR (a:Alias)
REQUIRE a.id IS UNIQUE;

CREATE INDEX skill_status_idx IF NOT EXISTS
FOR (s:Skill)
ON (s.status);

CREATE INDEX skill_category_idx IF NOT EXISTS
FOR (s:Skill)
ON (s.category);

CREATE INDEX skill_canonical_name_idx IF NOT EXISTS
FOR (s:Skill)
ON (s.canonicalName);

CREATE FULLTEXT INDEX skill_fulltext IF NOT EXISTS
FOR (s:Skill)
ON EACH [s.canonicalName, s.slug];

CREATE FULLTEXT INDEX alias_fulltext IF NOT EXISTS
FOR (a:Alias)
ON EACH [a.surfaceForm];
