package com.ysumly.umowebbackend.controller;

import com.ysumly.umowebbackend.common.exception.*;
import com.ysumly.umowebbackend.controller.admin.*;
import com.ysumly.umowebbackend.controller.open.*;
import com.ysumly.umowebbackend.service.admin.*;
import com.ysumly.umowebbackend.service.open.*;
import com.ysumly.umowebbackend.model.vo.CategoryTreeVO;
import com.ysumly.umowebbackend.model.vo.ContentDetailVO;
import com.ysumly.umowebbackend.model.vo.ImageIntegrityCountsVO;
import com.ysumly.umowebbackend.model.vo.ImageIntegrityReportVO;
import com.ysumly.umowebbackend.model.vo.TagVO;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 边界测试 — 覆盖所有 4xx/5xx 异常路径，防止 500 泄漏。
 * 使用 standalone setup，不启动 Spring 容器，无需数据库。
 */
class BoundaryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // Mock Services
    private final SiteOptionService siteOptionService = mock(SiteOptionService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final CategoryService categoryService = mock(CategoryService.class);
    private final TagService tagService = mock(TagService.class);
    private final AuthService authService = mock(AuthService.class);
    private final ContentManageService contentManageService = mock(ContentManageService.class);
    private final CategoryManageService categoryManageService = mock(CategoryManageService.class);
    private final TagManageService tagManageService = mock(TagManageService.class);
    private final ImageService imageService = mock(ImageService.class);
    private final ImageIntegrityService imageIntegrityService =
            mock(ImageIntegrityService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(
                    new SiteController(siteOptionService),
                    new ContentController(contentService),
                    new CategoryController(categoryService),
                    new TagController(tagService),
                    new AuthController(authService, new com.ysumly.umowebbackend.config.ClientIpResolver("")),
                    new ContentManageController(contentManageService),
                    new CategoryManageController(categoryManageService),
                    new TagManageController(tagManageService),
                    new ImageController(imageService, imageIntegrityService),
                    new OptionController(siteOptionService)
            )
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    // ======================== 公开端 ========================

    @Test
    @DisplayName("1. 文章列表: 无参数 → 200")
    void contentListDefaults() throws Exception {
        mvc.perform(get("/api/public/contents"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("2. 文章详情: slug 不存在 → 404")
    void contentDetailNotFound() throws Exception {
        when(contentService.getBySlug("no-such"))
                .thenThrow(new NotFoundException("Content not found: no-such"));

        mvc.perform(get("/api/public/contents/no-such"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Content not found: no-such"));
    }

    @Test
    @DisplayName("3. 搜索: 无参数 → 200")
    void searchDefaults() throws Exception {
        mvc.perform(get("/api/public/contents/search"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("4. 站点信息: → 200")
    void siteInfoOk() throws Exception {
        mvc.perform(get("/api/public/site-info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("5. 分类树: 空 → 200 []")
    void categoriesEmpty() throws Exception {
        when(categoryService.getTree(null)).thenReturn(java.util.List.of());
        mvc.perform(get("/api/public/categories"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("6. 标签列表: 空 → 200 []")
    void tagsEmpty() throws Exception {
        when(tagService.getAll()).thenReturn(java.util.List.of());
        mvc.perform(get("/api/public/tags"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("7. About 页面: 空 → 200")
    void aboutPage() throws Exception {
        when(siteOptionService.getPage("about_page")).thenReturn("");
        mvc.perform(get("/api/public/pages/about"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("8. Project 页面: 空 → 200")
    void projectPage() throws Exception {
        when(siteOptionService.getPage("project_page")).thenReturn("");
        mvc.perform(get("/api/public/pages/project"))
                .andExpect(status().isOk());
    }

    // ======================== 管理端 — 认证 ========================

    @Test
    @DisplayName("9. 登录: 缺少 Body → 400")
    void loginMissingBody() throws Exception {
        mvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求体不能为空"));
    }

    @Test
    @DisplayName("10. 登录: 空字段 → 400")
    void loginEmptyFields() throws Exception {
        mvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("11. 登录: 密码错误 → 401")
    void loginWrongPassword() throws Exception {
        when(authService.login(anyString(), any()))
                .thenThrow(new UnauthorizedException("Invalid username or password"));

        mvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ======================== 管理端 CRUD 边界 ========================

    @Test
    @DisplayName("12. 新建分类: 空字段 → 400")
    void categoryCreateMissingFields() throws Exception {
        mvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"slug\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("13. 文章: ID 不存在 → 404")
    void contentManageNotFound() throws Exception {
        when(contentManageService.getById(999L))
                .thenThrow(new NotFoundException("Content not found: id=999"));

        mvc.perform(get("/api/admin/contents/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("14. 更新配置: 空值 → 400")
    void optionUpdateEmptyValue() throws Exception {
        mvc.perform(put("/api/admin/options/site_title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("15. 新建文章: 空必填字段 → 400")
    void contentCreateMissingFields() throws Exception {
        mvc.perform(post("/api/admin/contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"slug\":\"\",\"type\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("15.1 批量文章: 空 contentIds → 400")
    void bulkContentRequiresIds() throws Exception {
        mvc.perform(post("/api/admin/contents/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"ARCHIVE","contentIds":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("15.2 批量文章: 预检失败返回明细 → 409")
    void bulkContentReturnsFailureDetails() throws Exception {
        when(contentManageService.bulk(any()))
                .thenThrow(new BulkOperationException(
                        409,
                        "只有已归档内容可以恢复为草稿",
                        java.util.List.of(new com.ysumly.umowebbackend.model.vo.BulkContentFailureVO(
                                7L, null, "NOT_ARCHIVED"))));

        mvc.perform(post("/api/admin/contents/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"RESTORE_DRAFT","contentIds":[7]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.failures[0].contentId").value(7))
                .andExpect(jsonPath("$.failures[0].reason").value("NOT_ARCHIVED"));
    }

    @Test
    @DisplayName("16. 修改密码: 新密码不足6位 → 400")
    void changePasswordTooShort() throws Exception {
        mvc.perform(put("/api/admin/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"old\",\"newPassword\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("17. 新建标签: 空字段 → 400")
    void tagCreateMissingFields() throws Exception {
        mvc.perform(post("/api/admin/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"slug\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("18. 删除有关联的分类 → 409")
    void categoryDeleteWithContent() throws Exception {
        doThrow(new BusinessException(409,
                "Cannot delete category: it is associated with 3 content(s)"))
                .when(categoryManageService).delete(1L);

        mvc.perform(delete("/api/admin/categories/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("19. 文章列表: 无效排序不崩溃 → 200")
    void contentListInvalidSort() throws Exception {
        mvc.perform(get("/api/public/contents?sort=invalid"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("20. 文章列表: page=0 → 400")
    void contentListInvalidPage() throws Exception {
        mvc.perform(get("/api/public/contents?page=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("21. 文章列表: 超大 size → 400")
    void contentListOversizedPage() throws Exception {
        mvc.perform(get("/api/public/contents?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("22. 文章列表: 非法 type → 400")
    void contentListInvalidType() throws Exception {
        mvc.perform(get("/api/public/contents?type=INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("26. 文章列表: includeDescendants 缺少 categoryId → 400")
    void contentListDescendantsWithoutCategory() throws Exception {
        mvc.perform(get("/api/public/contents?includeDescendants=true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message")
                        .value("descendantFilterValid: includeDescendants 需要同时提供 categoryId"));
    }

    @Test
    @DisplayName("23. 新建文章: 非法 metadata → 400")
    void contentCreateInvalidMetadata() throws Exception {
        mvc.perform(post("/api/admin/contents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Title","slug":"valid-slug","type":"NOTE","metadata":"{invalid"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("24. 公开详情返回 categories/tags")
    void contentDetailIncludesCategoriesAndTags() throws Exception {
        ContentDetailVO detail = new ContentDetailVO();
        detail.setSlug("article");
        CategoryTreeVO category = new CategoryTreeVO();
        category.setId(1L);
        category.setName("Java");
        category.setSlug("java");
        detail.setCategories(java.util.List.of(category));
        TagVO tag = new TagVO();
        tag.setId(2L);
        tag.setName("Backend");
        tag.setSlug("backend");
        detail.setTags(java.util.List.of(tag));
        when(contentService.getBySlug("article")).thenReturn(detail);

        mvc.perform(get("/api/public/contents/article"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].slug").value("java"))
                .andExpect(jsonPath("$.tags[0].slug").value("backend"));
    }

    @Test
    @DisplayName("25. 分类树: 按 type 过滤 → 200")
    void categoriesByType() throws Exception {
        when(categoryService.getTree("NOTE")).thenReturn(java.util.List.of());
        mvc.perform(get("/api/public/categories?type=NOTE"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("27. 图片列表: 分页和未引用筛选 → 200")
    void imageListWithUsageFilter() throws Exception {
        mvc.perform(get("/api/admin/images?page=1&size=24&usage=ORPHANED"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("28. 图片列表: 非法 usage → 400")
    void imageListInvalidUsage() throws Exception {
        mvc.perform(get("/api/admin/images?usage=INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("29. 删除图片: 未引用 → 204")
    void imageDeleteNoContent() throws Exception {
        mvc.perform(delete("/api/admin/images/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("30. 删除图片: ID 不存在 → 404")
    void imageDeleteNotFound() throws Exception {
        doThrow(new NotFoundException("Image not found: id=999"))
                .when(imageService).delete(999L);

        mvc.perform(delete("/api/admin/images/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("31. 删除图片: 仍被引用 → 409")
    void imageDeleteReferenced() throws Exception {
        doThrow(new BusinessException(409, "图片仍被内容引用，无法删除"))
                .when(imageService).delete(1L);

        mvc.perform(delete("/api/admin/images/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("图片仍被内容引用，无法删除"));
    }

    @Test
    @DisplayName("32. 图片一致性检查: 无异常 → 200 完整报告")
    void imageIntegrityReport() throws Exception {
        ImageIntegrityReportVO report = new ImageIntegrityReportVO(
                java.time.LocalDateTime.of(2026, 9, 15, 21, 0),
                new ImageIntegrityCountsVO(0, 0, 0, 0),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of());
        when(imageIntegrityService.inspect()).thenReturn(report);

        mvc.perform(get("/api/admin/images/integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedAt").value("2026-09-15T21:00:00"))
                .andExpect(jsonPath("$.counts.total").value(0))
                .andExpect(jsonPath("$.brokenReferences").isArray())
                .andExpect(jsonPath("$.missingFiles").isArray())
                .andExpect(jsonPath("$.untrackedFiles").isArray());
    }

    @Test
    @DisplayName("33. 图片一致性检查: 扫描失败 → 500")
    void imageIntegrityScanFailure() throws Exception {
        when(imageIntegrityService.inspect())
                .thenThrow(new BusinessException(500, "图片一致性检查失败"));

        mvc.perform(get("/api/admin/images/integrity"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("图片一致性检查失败"));
    }
}
