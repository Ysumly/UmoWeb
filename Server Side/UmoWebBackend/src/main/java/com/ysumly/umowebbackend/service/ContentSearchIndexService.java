package com.ysumly.umowebbackend.service;

import com.ysumly.umowebbackend.model.entity.Content;

public interface ContentSearchIndexService {
    void sync(Content content, String body);

    int rebuildPublished();
}
