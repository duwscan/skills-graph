package com.sk.skillsgraph.script;

import com.sk.skillsgraph.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SearchReindexRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchReindexRunner.class);

    private final SearchService searchService;

    public SearchReindexRunner(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("search-reindex")) {
            return;
        }

        int indexed = searchService.reindexAllActiveSkills();
        LOGGER.info("Search reindex completed. Indexed {} active skills", indexed);
    }
}
