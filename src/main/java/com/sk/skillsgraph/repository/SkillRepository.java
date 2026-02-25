package com.sk.skillsgraph.repository;

import com.sk.skillsgraph.domain.Skill;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface SkillRepository extends Neo4jRepository<Skill, String> {
}
