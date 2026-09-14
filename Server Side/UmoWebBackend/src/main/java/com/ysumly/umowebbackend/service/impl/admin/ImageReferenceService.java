package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ContentMapper;
import com.ysumly.umowebbackend.mapper.SiteOptionMapper;
import com.ysumly.umowebbackend.model.entity.SiteOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImageReferenceService {

    private static final Logger log = LoggerFactory.getLogger(ImageReferenceService.class);
    private static final List<String> FIXED_PAGE_KEYS = List.of("about_page", "project_page");
    private static final Pattern IMAGE_URL =
            Pattern.compile("(?<![A-Za-z0-9_./:-])(/images/[A-Za-z0-9][A-Za-z0-9/_.-]*)");

    private final ContentMapper contentMapper;
    private final SiteOptionMapper siteOptionMapper;
    private final FileUtil fileUtil;

    public ImageReferenceService(ContentMapper contentMapper,
                                 SiteOptionMapper siteOptionMapper,
                                 FileUtil fileUtil) {
        this.contentMapper = contentMapper;
        this.siteOptionMapper = siteOptionMapper;
        this.fileUtil = fileUtil;
    }

    public Set<String> findReferencedImageUrls() {
        Set<String> references = new LinkedHashSet<>();
        for (String bodyPath : contentMapper.findAllBodyPaths()) {
            try {
                collectUrls(fileUtil.readMarkdown(bodyPath), references);
            } catch (IOException | RuntimeException e) {
                log.warn("Failed to scan Markdown image references: {}", bodyPath, e);
            }
        }
        for (String optionKey : FIXED_PAGE_KEYS) {
            SiteOption option = siteOptionMapper.findByKey(optionKey);
            if (option != null && option.getOptionValue() != null) {
                collectUrls(option.getOptionValue(), references);
            }
        }
        return references;
    }

    private void collectUrls(String markdown, Set<String> references) {
        Matcher matcher = IMAGE_URL.matcher(markdown);
        while (matcher.find()) {
            references.add(matcher.group(1));
        }
    }
}
