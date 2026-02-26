package com.sk.skillsgraph.script;

import com.sk.skillsgraph.domain.LocaleConfigEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.sk.skillsgraph.repository.jpa.LocaleConfigRepository;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SeedRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeedRunner.class);

    private final LocaleConfigRepository localeConfigRepository;
    private final Neo4jClient neo4jClient;

    public SeedRunner(LocaleConfigRepository localeConfigRepository, Neo4jClient neo4jClient) {
        this.localeConfigRepository = localeConfigRepository;
        this.neo4jClient = neo4jClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("seed")) {
            return;
        }

        seedLocales();
        seedRootSkills();
        LOGGER.info("Seed completed");
    }

    private void seedLocales() {
        List<LocaleConfigEntity> locales = List.of(
                new LocaleConfigEntity("en", "English", true, 100),
                new LocaleConfigEntity("vi", "Vietnamese", true, 70),
                new LocaleConfigEntity("fr", "French", true, 60),
                new LocaleConfigEntity("ja", "Japanese", true, 50),
                new LocaleConfigEntity("zh", "Chinese", true, 50)
        );

        for (LocaleConfigEntity locale : locales) {
            if (!localeConfigRepository.existsById(locale.getLocale())) {
                localeConfigRepository.save(locale);
            }
        }
        LOGGER.info("Seeded locale_config rows");
    }

    private void seedRootSkills() {
        List<Map<String, String>> roots = List.of(
                Map.of("id", "skill-root-technology", "name", "Technology", "slug", "technology", "category", "root"),
                Map.of("id", "skill-root-business", "name", "Business", "slug", "business", "category", "root"),
                Map.of("id", "skill-root-design", "name", "Design", "slug", "design", "category", "root"),
                Map.of("id", "skill-root-science", "name", "Science", "slug", "science", "category", "root"),
                Map.of("id", "skill-root-languages", "name", "Languages", "slug", "languages", "category", "root"),
                Map.of("id", "skill-root-soft-skills", "name", "Soft Skills", "slug", "soft-skills", "category", "root")
        );

        for (Map<String, String> root : roots) {
            String rootId = root.get("id");
            Instant now = Instant.now();
            String externalId = UUID.nameUUIDFromBytes(rootId.getBytes()).toString();
            neo4jClient.query("""
                            MERGE (s:Skill {id: $id})
                            ON CREATE SET s.externalId = $externalId,
                                          s.createdAt = datetime($now)
                            SET s.canonicalName = $canonicalName,
                                s.slug = $slug,
                                s.status = 'active',
                                s.category = $category,
                                s.version = 1,
                                s.source = 'seed',
                                s.updatedAt = datetime($now)
                            """)
                    .bind(rootId).to("id")
                    .bind(externalId).to("externalId")
                    .bind(root.get("name")).to("canonicalName")
                    .bind(root.get("slug")).to("slug")
                    .bind(root.get("category")).to("category")
                    .bind(now.toString()).to("now")
                    .run();
        }

        LOGGER.info("Seeded root Skill nodes in Neo4j");
    }
}
