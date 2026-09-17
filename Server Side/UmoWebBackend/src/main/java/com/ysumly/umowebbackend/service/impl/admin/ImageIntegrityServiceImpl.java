package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.util.FileUtil;
import com.ysumly.umowebbackend.mapper.ImageMapper;
import com.ysumly.umowebbackend.model.entity.Image;
import com.ysumly.umowebbackend.model.vo.BrokenImageReferenceVO;
import com.ysumly.umowebbackend.model.vo.ImageIntegrityCountsVO;
import com.ysumly.umowebbackend.model.vo.ImageIntegrityReportVO;
import com.ysumly.umowebbackend.model.vo.MissingImageFileVO;
import com.ysumly.umowebbackend.model.vo.UntrackedImageFileVO;
import com.ysumly.umowebbackend.service.admin.ImageIntegrityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ImageIntegrityServiceImpl implements ImageIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(ImageIntegrityServiceImpl.class);

    private final ImageMapper imageMapper;
    private final ImageReferenceService referenceService;
    private final FileUtil fileUtil;

    public ImageIntegrityServiceImpl(ImageMapper imageMapper,
                                     ImageReferenceService referenceService,
                                     FileUtil fileUtil) {
        this.imageMapper = imageMapper;
        this.referenceService = referenceService;
        this.fileUtil = fileUtil;
    }

    @Override
    public ImageIntegrityReportVO inspect() {
        try {
            List<Image> images = imageMapper.findAll();
            Map<String, Image> recordsByUrl = images.stream()
                    .collect(Collectors.toMap(
                            image -> toUrl(image.getPath()),
                            Function.identity(),
                            (first, ignored) -> first));
            Set<String> storedUrls = fileUtil.listStoredFiles("images").stream()
                    .map(this::toUrl)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<ImageReferenceOccurrence> references = referenceService.scanReferencesStrict();
            Set<String> referencedUrls = references.stream()
                    .map(ImageReferenceOccurrence::url)
                    .collect(Collectors.toSet());

            List<BrokenImageReferenceVO> brokenReferences = references.stream()
                    .filter(reference -> !recordsByUrl.containsKey(reference.url()))
                    .distinct()
                    .sorted(Comparator
                            .comparing(ImageReferenceOccurrence::url)
                            .thenComparing(ImageReferenceOccurrence::sourceType)
                            .thenComparing(
                                    ImageReferenceOccurrence::sourceId,
                                    Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparing(
                                    ImageReferenceOccurrence::sourceLabel,
                                    Comparator.nullsFirst(Comparator.naturalOrder())))
                    .map(reference -> new BrokenImageReferenceVO(
                            reference.url(),
                            reference.sourceType(),
                            reference.sourceId(),
                            reference.sourceLabel()))
                    .toList();

            List<MissingImageFileVO> missingFiles = images.stream()
                    .filter(image -> !storedUrls.contains(toUrl(image.getPath())))
                    .sorted(Comparator.comparing(Image::getId))
                    .map(image -> new MissingImageFileVO(
                            image.getId(),
                            toUrl(image.getPath()),
                            image.getOriginalName()))
                    .toList();

            List<UntrackedImageFileVO> untrackedFiles = storedUrls.stream()
                    .filter(url -> !recordsByUrl.containsKey(url))
                    .filter(url -> !referencedUrls.contains(url))
                    .sorted()
                    .map(UntrackedImageFileVO::new)
                    .toList();

            int total = brokenReferences.size() + missingFiles.size() + untrackedFiles.size();
            return new ImageIntegrityReportVO(
                    LocalDateTime.now(),
                    new ImageIntegrityCountsVO(
                            brokenReferences.size(),
                            missingFiles.size(),
                            untrackedFiles.size(),
                            total),
                    List.copyOf(brokenReferences),
                    List.copyOf(missingFiles),
                    List.copyOf(untrackedFiles));
        } catch (IOException | BusinessException e) {
            log.error("Failed to inspect image integrity", e);
            throw new BusinessException(500, "图片一致性检查失败", e);
        }
    }

    private String toUrl(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}
