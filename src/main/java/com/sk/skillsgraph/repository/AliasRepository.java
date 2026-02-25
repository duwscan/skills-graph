package com.sk.skillsgraph.repository;

import com.sk.skillsgraph.domain.Alias;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface AliasRepository extends Neo4jRepository<Alias, String> {
}
