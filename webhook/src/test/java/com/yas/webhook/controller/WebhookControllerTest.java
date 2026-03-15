package com.yas.webhook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.webhook.config.constants.ApiConstant;
import com.yas.webhook.model.viewmodel.webhook.EventVm;
import com.yas.webhook.model.viewmodel.webhook.WebhookDetailVm;
import com.yas.webhook.model.viewmodel.webhook.WebhookListGetVm;
import com.yas.webhook.model.viewmodel.webhook.WebhookPostVm;
import com.yas.webhook.model.viewmodel.webhook.WebhookVm;
import com.yas.webhook.service.WebhookService;
import java.util.List;
import com.yas.commonlibrary.exception.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    private static final String BASE_URL = ApiConstant.WEBHOOK_URL;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @Mock
    WebhookService webhookService;

    @InjectMocks
    WebhookController webhookController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(webhookController)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
        objectMapper = new ObjectMapper();
    }

    // ── GET /backoffice/webhooks/paging ───────────────────────────────────────

    @Test
    void getPageableWebhooks_shouldReturn200WithPagingResult() throws Exception {
        WebhookListGetVm listGetVm = WebhookListGetVm.builder()
            .webhooks(List.of())
            .pageNo(0)
            .pageSize(10)
            .totalElements(5)
            .totalPages(1)
            .isLast(true)
            .build();

        when(webhookService.getPageableWebhooks(0, 10)).thenReturn(listGetVm);

        mockMvc.perform(get(BASE_URL + "/paging")
                .param("pageNo", "0")
                .param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNo").value(0))
            .andExpect(jsonPath("$.pageSize").value(10))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getPageableWebhooks_shouldReturn200WithDefaults_whenNoParamsProvided() throws Exception {
        WebhookListGetVm listGetVm = WebhookListGetVm.builder()
            .webhooks(List.of())
            .pageNo(0)
            .pageSize(10)
            .totalElements(0)
            .totalPages(0)
            .isLast(true)
            .build();

        when(webhookService.getPageableWebhooks(0, 10)).thenReturn(listGetVm);

        mockMvc.perform(get(BASE_URL + "/paging"))
            .andExpect(status().isOk());

        verify(webhookService).getPageableWebhooks(0, 10);
    }

    // ── GET /backoffice/webhooks ──────────────────────────────────────────────

    @Test
    void listWebhooks_shouldReturn200WithWebhookList() throws Exception {
        WebhookVm webhookVm = new WebhookVm();
        webhookVm.setId(1L);
        webhookVm.setPayloadUrl("https://example.com");

        when(webhookService.findAllWebhooks()).thenReturn(List.of(webhookVm));

        mockMvc.perform(get(BASE_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].payloadUrl").value("https://example.com"));
    }

    // ── GET /backoffice/webhooks/{id} ─────────────────────────────────────────

    @Test
    void getWebhook_shouldReturn200_whenFound() throws Exception {
        WebhookDetailVm detailVm = new WebhookDetailVm();
        detailVm.setId(1L);
        detailVm.setPayloadUrl("https://example.com");

        when(webhookService.findById(1L)).thenReturn(detailVm);

        mockMvc.perform(get(BASE_URL + "/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.payloadUrl").value("https://example.com"));
    }

    @Test
    void getWebhook_shouldReturn404_whenNotFound() throws Exception {
        when(webhookService.findById(99L))
            .thenThrow(new NotFoundException("WEBHOOK_NOT_FOUND", 99L));

        mockMvc.perform(get(BASE_URL + "/99"))
            .andExpect(status().isNotFound());
    }

    // ── POST /backoffice/webhooks ─────────────────────────────────────────────

    @Test
    void createWebhook_shouldReturn201WithCreatedWebhook() throws Exception {
        EventVm eventVm = EventVm.builder().id(1L).build();
        WebhookPostVm postVm = new WebhookPostVm(
            "https://example.com", "secret", "application/json", true, List.of(eventVm));

        WebhookDetailVm detailVm = new WebhookDetailVm();
        detailVm.setId(1L);
        detailVm.setPayloadUrl("https://example.com");

        when(webhookService.create(any(WebhookPostVm.class))).thenReturn(detailVm);

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(postVm)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.payloadUrl").value("https://example.com"));
    }

    // ── PUT /backoffice/webhooks/{id} ─────────────────────────────────────────

    @Test
    void updateWebhook_shouldReturn204_whenSuccessful() throws Exception {
        WebhookPostVm postVm = new WebhookPostVm(
            "https://updated.com", "newSecret", "application/json", false, null);

        doNothing().when(webhookService).update(any(WebhookPostVm.class), eq(1L));

        mockMvc.perform(put(BASE_URL + "/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(postVm)))
            .andExpect(status().isNoContent());

        verify(webhookService).update(any(WebhookPostVm.class), eq(1L));
    }

    @Test
    void updateWebhook_shouldReturn404_whenWebhookNotFound() throws Exception {
        WebhookPostVm postVm = new WebhookPostVm(
            "https://updated.com", "newSecret", "application/json", false, null);

        doThrow(new NotFoundException("WEBHOOK_NOT_FOUND", 99L))
            .when(webhookService).update(any(WebhookPostVm.class), eq(99L));

        mockMvc.perform(put(BASE_URL + "/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(postVm)))
            .andExpect(status().isNotFound());
    }

    // ── DELETE /backoffice/webhooks/{id} ──────────────────────────────────────

    @Test
    void deleteWebhook_shouldReturn204_whenSuccessful() throws Exception {
        doNothing().when(webhookService).delete(1L);

        mockMvc.perform(delete(BASE_URL + "/1"))
            .andExpect(status().isNoContent());

        verify(webhookService).delete(1L);
    }

    @Test
    void deleteWebhook_shouldReturn404_whenWebhookNotFound() throws Exception {
        doThrow(new NotFoundException("WEBHOOK_NOT_FOUND", 99L))
            .when(webhookService).delete(99L);

        mockMvc.perform(delete(BASE_URL + "/99"))
            .andExpect(status().isNotFound());
    }
}
