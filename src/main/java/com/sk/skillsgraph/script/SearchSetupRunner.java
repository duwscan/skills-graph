package com.sk.skillsgraph.script;

import com.sk.skillsgraph.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SearchSetupRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchSetupRunner.class);

    private final SearchService searchService;

    public SearchSetupRunner(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("search-setup")) {
            return;
        }

        searchService.setupSearchInfrastructure();
        int indexed = searchService.reindexAllActiveSkills();
        LOGGER.info("Search setup completed. Indexed {} active skills", indexed);
    }
}
