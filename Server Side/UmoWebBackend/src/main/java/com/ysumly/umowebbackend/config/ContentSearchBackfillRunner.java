package com.ysumly.umowebbackend.config;

import com.ysumly.umowebbackend.service.ContentSearchIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
@ConditionalOnProperty(name = "app.search.backfill-only", havingValue = "true")
public class ContentSearchBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentSearchBackfillRunner.class);

    private final ContentSearchIndexService searchIndexService;

    public ContentSearchBackfillRunner(ContentSearchIndexService searchIndexService) {
        this.searchIndexService = searchIndexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int indexed = searchIndexService.rebuildPublished();
        log.info("Content search backfill completed: {} published contents indexed", indexed);
    }
}
