package com.ysumly.umowebbackend.controller;

import com.ysumly.umowebbackend.common.constant.AiValidationProfile;
import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.exception.GlobalExceptionHandler;
import com.ysumly.umowebbackend.common.exception.NotFoundException;
import com.ysumly.umowebbackend.common.util.JwtUtil;
import com.ysumly.umowebbackend.config.AdminInterceptor;
import com.ysumly.umowebbackend.controller.admin.AiModeCatalogController;
import com.ysumly.umowebbackend.mapper.UserMapper;
import com.ysumly.umowebbackend.model.dto.AiModeCopyRequest;
import com.ysumly.umowebbackend.model.dto.AiModeCreateRequest;
import com.ysumly.umowebbackend.model.dto.AiModeUpdateRequest;
import com.ysumly.umowebbackend.model.vo.AiModeSettingsVO;
import com.ysumly.umowebbackend.model.vo.AiModeVersionVO;
import com.ysumly.umowebbackend.service.admin.AiModeCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiModeCatalogControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AiModeCatalogService service = mock(AiModeCatalogService.class);
    private final AdminInterceptor adminInterceptor = new AdminInterceptor(
            new JwtUtil("a-test-secret-that-is-not-production", 24),
            mock(UserMapper.class));
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AiModeCatalogController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    private final MockMvc securedMvc = MockMvcBuilders
            .standaloneSetup(new AiModeCatalogController(service))
            .addInterceptors(adminInterceptor)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        securedMvc.perform(get("/api/admin/ai/modes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void listReturnsModeSettings() throws Exception {
        when(service.list()).thenReturn(List.of(settings()));

        mvc.perform(get("/api/admin/ai/modes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].modeKey").value("CUSTOM_MODE"))
                .andExpect(jsonPath("$[0].currentVersion").value(1));
    }

    @Test
    void createReturnsModeSettings() throws Exception {
        when(service.create(any(AiModeCreateRequest.class))).thenReturn(settings());

        mvc.perform(post("/api/admin/ai/modes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AiModeCreateRequest(
                                "CUSTOM_MODE",
                                "自定义模式",
                                "",
                                "prompt-v1",
                                AiValidationProfile.NONE,
                                false,
                                1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modeKey").value("CUSTOM_MODE"));
    }

    @Test
    void createRejectsInvalidModeKey() throws Exception {
        mvc.perform(post("/api/admin/ai/modes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modeKey": "invalid-key",
                                  "name": "模式",
                                  "description": "",
                                  "systemPrompt": "prompt",
                                  "validationProfile": "NONE",
                                  "enabled": false,
                                  "sortOrder": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createReturnsConflictForDuplicateKey() throws Exception {
        when(service.create(any(AiModeCreateRequest.class)))
                .thenThrow(new BusinessException(409, "AI mode key already exists"));

        mvc.perform(post("/api/admin/ai/modes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AiModeCreateRequest(
                                "CUSTOM_MODE",
                                "自定义模式",
                                "",
                                "prompt-v1",
                                AiValidationProfile.NONE,
                                false,
                                1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void copyReturnsModeSettings() throws Exception {
        when(service.copy(eq(7L), any(AiModeCopyRequest.class))).thenReturn(settings());

        mvc.perform(post("/api/admin/ai/modes/7/copy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modeKey":"COPIED_MODE","name":"复制模式"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modeKey").value("CUSTOM_MODE"));
    }

    @Test
    void updateReturnsConflictForStaleVersion() throws Exception {
        when(service.update(eq(7L), any(AiModeUpdateRequest.class)))
                .thenThrow(new BusinessException(409, "stale"));

        mvc.perform(put("/api/admin/ai/modes/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AiModeUpdateRequest(
                                "自定义模式",
                                "",
                                "prompt-v2",
                                AiValidationProfile.NONE,
                                true,
                                1,
                                1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void versionsReturnNotFoundForUnknownMode() throws Exception {
        when(service.listVersions(99L))
                .thenThrow(new NotFoundException("AI mode not found: id=99"));

        mvc.perform(get("/api/admin/ai/modes/99/versions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void versionsReturnHistory() throws Exception {
        when(service.listVersions(7L)).thenReturn(List.of(
                new AiModeVersionVO(
                        2,
                        "prompt-v2",
                        AiValidationProfile.NONE,
                        LocalDateTime.of(2026, 9, 18, 12, 0))));

        mvc.perform(get("/api/admin/ai/modes/7/versions"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{
                          "versionNo": 2,
                          "systemPrompt": "prompt-v2",
                          "validationProfile": "NONE",
                          "createdAt": "2026-09-18T12:00:00"
                        }]
                        """));
    }

    @Test
    void rollbackReturnsNotFoundForMissingVersion() throws Exception {
        when(service.rollback(7L, 99, 3))
                .thenThrow(new NotFoundException("AI mode version not found"));

        mvc.perform(post("/api/admin/ai/modes/7/rollback/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void rollbackReturnsNewCurrentVersion() throws Exception {
        when(service.rollback(7L, 1, 3)).thenReturn(settings());

        mvc.perform(post("/api/admin/ai/modes/7/rollback/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modeKey").value("CUSTOM_MODE"));
    }

    private AiModeSettingsVO settings() {
        return new AiModeSettingsVO(
                7L,
                "CUSTOM_MODE",
                "自定义模式",
                "",
                false,
                1,
                1,
                "prompt-v1",
                AiValidationProfile.NONE,
                LocalDateTime.of(2026, 9, 18, 12, 0),
                LocalDateTime.of(2026, 9, 18, 12, 0));
    }
}
