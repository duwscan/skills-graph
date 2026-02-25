package com.sk.skillsgraph.repository;

import com.sk.skillsgraph.domain.Skill;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface SkillRepository extends Neo4jRepository<Skill, String> {

    Optional<Skill> findByExternalId(String externalId);

    Optional<Skill> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, String id);

    boolean existsByCanonicalNameIgnoreCase(String canonicalName);

    boolean existsByCanonicalNameIgnoreCaseAndIdNot(String canonicalName, String id);
}
