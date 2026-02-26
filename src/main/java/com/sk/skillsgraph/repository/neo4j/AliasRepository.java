package com.sk.skillsgraph.repository.neo4j;

import com.sk.skillsgraph.domain.Alias;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

public interface AliasRepository extends Neo4jRepository<Alias, String> {

    @Query("""
            MATCH (:Skill {id: $skillId})-[:HAS_ALIAS]->(a:Alias)
            WHERE coalesce(a.locale, 'en') = $locale AND coalesce(a.isPrimary, false) = true
            RETURN a
            LIMIT 1
            """)
    Optional<Alias> findPrimaryBySkillIdAndLocale(@Param("skillId") String skillId, @Param("locale") String locale);
}
