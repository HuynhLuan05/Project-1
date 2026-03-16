package com.yas.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yas.commonlibrary.exception.NotFoundException;
import com.yas.webhook.integration.api.WebhookApi;
import com.yas.webhook.model.Webhook;
import com.yas.webhook.model.WebhookEvent;
import com.yas.webhook.model.WebhookEventNotification;
import com.yas.webhook.model.dto.WebhookEventNotificationDto;
import com.yas.webhook.model.enums.NotificationStatus;
import com.yas.webhook.model.mapper.WebhookMapper;
import com.yas.webhook.model.viewmodel.webhook.EventVm;
import com.yas.webhook.model.viewmodel.webhook.WebhookDetailVm;
import com.yas.webhook.model.viewmodel.webhook.WebhookListGetVm;
import com.yas.webhook.model.viewmodel.webhook.WebhookPostVm;
import com.yas.webhook.model.viewmodel.webhook.WebhookVm;
import com.yas.webhook.repository.EventRepository;
import com.yas.webhook.repository.WebhookEventNotificationRepository;
import com.yas.webhook.repository.WebhookEventRepository;
import com.yas.webhook.repository.WebhookRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    WebhookRepository webhookRepository;
    @Mock
    EventRepository eventRepository;
    @Mock
    WebhookEventRepository webhookEventRepository;
    @Mock
    WebhookEventNotificationRepository webhookEventNotificationRepository;
    @Mock
    WebhookMapper webhookMapper;
    @Mock
    WebhookApi webHookApi;

    @InjectMocks
    WebhookService webhookService;

    // ── getPageableWebhooks ───────────────────────────────────────────────────

    @Test
    void getPageableWebhooks_shouldReturnMappedVm() {
        Webhook webhook = new Webhook();
        Page<Webhook> page = new PageImpl<>(List.of(webhook));
        WebhookListGetVm expected = WebhookListGetVm.builder()
            .webhooks(List.of())
            .pageNo(0)
            .pageSize(10)
            .totalElements(1)
            .totalPages(1)
            .isLast(true)
            .build();

        when(webhookRepository.findAll(any(PageRequest.class))).thenReturn(page);
        when(webhookMapper.toWebhookListGetVm(page, 0, 10)).thenReturn(expected);

        WebhookListGetVm result = webhookService.getPageableWebhooks(0, 10);

        assertThat(result).isEqualTo(expected);
        verify(webhookRepository).findAll(any(PageRequest.class));
        verify(webhookMapper).toWebhookListGetVm(page, 0, 10);
    }

    // ── findAllWebhooks ───────────────────────────────────────────────────────

    @Test
    void findAllWebhooks_shouldReturnMappedVmList() {
        Webhook webhook = new Webhook();
        WebhookVm webhookVm = new WebhookVm();

        when(webhookRepository.findAll(Sort.by(Sort.Direction.DESC, "id")))
            .thenReturn(List.of(webhook));
        when(webhookMapper.toWebhookVm(webhook)).thenReturn(webhookVm);

        List<WebhookVm> result = webhookService.findAllWebhooks();

        assertThat(result).containsExactly(webhookVm);
    }

    @Test
    void findAllWebhooks_shouldReturnEmptyList_whenNoWebhooks() {
        when(webhookRepository.findAll(Sort.by(Sort.Direction.DESC, "id")))
            .thenReturn(Collections.emptyList());

        List<WebhookVm> result = webhookService.findAllWebhooks();

        assertThat(result).isEmpty();
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_shouldReturnDetailVm_whenWebhookExists() {
        Webhook webhook = new Webhook();
        WebhookDetailVm detailVm = new WebhookDetailVm();

        when(webhookRepository.findById(1L)).thenReturn(Optional.of(webhook));
        when(webhookMapper.toWebhookDetailVm(webhook)).thenReturn(detailVm);

        WebhookDetailVm result = webhookService.findById(1L);

        assertThat(result).isEqualTo(detailVm);
    }

    @Test
    void findById_shouldThrowNotFoundException_whenWebhookDoesNotExist() {
        when(webhookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.findById(99L))
            .isInstanceOf(NotFoundException.class);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_shouldSaveWebhookAndReturnDetailVm_withEvents() {
        EventVm eventVm = EventVm.builder().id(1L).build();
        WebhookPostVm postVm = new WebhookPostVm(
            "https://example.com", "secret", "application/json", true, List.of(eventVm));

        Webhook mappedWebhook = new Webhook();
        Webhook savedWebhook = new Webhook();
        savedWebhook.setId(1L);

        WebhookEvent webhookEvent = new WebhookEvent();
        WebhookDetailVm detailVm = new WebhookDetailVm();

        com.yas.webhook.model.Event event = new com.yas.webhook.model.Event();

        when(webhookMapper.toCreatedWebhook(postVm)).thenReturn(mappedWebhook);
        when(webhookRepository.save(mappedWebhook)).thenReturn(savedWebhook);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(webhookEventRepository.saveAll(anyList())).thenReturn(List.of(webhookEvent));
        when(webhookMapper.toWebhookDetailVm(savedWebhook)).thenReturn(detailVm);

        WebhookDetailVm result = webhookService.create(postVm);

        assertThat(result).isEqualTo(detailVm);
        verify(webhookRepository).save(mappedWebhook);
        verify(webhookEventRepository).saveAll(anyList());
    }

    @Test
    void create_shouldSaveWebhookWithoutEvents_whenEventsListIsEmpty() {
        WebhookPostVm postVm = new WebhookPostVm(
            "https://example.com", "secret", "application/json", true, Collections.emptyList());

        Webhook mappedWebhook = new Webhook();
        Webhook savedWebhook = new Webhook();
        savedWebhook.setId(1L);
        WebhookDetailVm detailVm = new WebhookDetailVm();

        when(webhookMapper.toCreatedWebhook(postVm)).thenReturn(mappedWebhook);
        when(webhookRepository.save(mappedWebhook)).thenReturn(savedWebhook);
        when(webhookMapper.toWebhookDetailVm(savedWebhook)).thenReturn(detailVm);

        WebhookDetailVm result = webhookService.create(postVm);

        assertThat(result).isEqualTo(detailVm);
        verify(webhookEventRepository, never()).saveAll(anyList());
    }

    @Test
    void create_shouldThrowNotFoundException_whenEventIdIsInvalid() {
        EventVm eventVm = EventVm.builder().id(999L).build();
        WebhookPostVm postVm = new WebhookPostVm(
            "https://example.com", "secret", "application/json", true, List.of(eventVm));

        Webhook mappedWebhook = new Webhook();
        Webhook savedWebhook = new Webhook();
        savedWebhook.setId(1L);

        when(webhookMapper.toCreatedWebhook(postVm)).thenReturn(mappedWebhook);
        when(webhookRepository.save(mappedWebhook)).thenReturn(savedWebhook);
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.create(postVm))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_shouldSaveWebhookWithoutEvents_whenEventsListIsNull() {
        WebhookPostVm postVm = new WebhookPostVm(
            "https://example.com", "secret", "application/json", true, null);

        Webhook mappedWebhook = new Webhook();
        Webhook savedWebhook = new Webhook();
        savedWebhook.setId(2L);
        WebhookDetailVm detailVm = new WebhookDetailVm();

        when(webhookMapper.toCreatedWebhook(postVm)).thenReturn(mappedWebhook);
        when(webhookRepository.save(mappedWebhook)).thenReturn(savedWebhook);
        when(webhookMapper.toWebhookDetailVm(savedWebhook)).thenReturn(detailVm);

        WebhookDetailVm result = webhookService.create(postVm);

        assertThat(result).isEqualTo(detailVm);
        verify(webhookEventRepository, never()).saveAll(anyList());
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_shouldUpdateWebhookAndReplaceEvents() {
        EventVm eventVm = EventVm.builder().id(1L).build();
        WebhookPostVm postVm = new WebhookPostVm(
            "https://updated.com", "newSecret", "application/json", false, List.of(eventVm));

        WebhookEvent oldEvent = new WebhookEvent();
        Webhook existingWebhook = new Webhook();
        existingWebhook.setId(1L);
        existingWebhook.setWebhookEvents(List.of(oldEvent));

        com.yas.webhook.model.Event event = new com.yas.webhook.model.Event();

        when(webhookRepository.findById(1L)).thenReturn(Optional.of(existingWebhook));
        when(webhookMapper.toUpdatedWebhook(existingWebhook, postVm)).thenReturn(existingWebhook);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        webhookService.update(postVm, 1L);

        verify(webhookRepository).save(existingWebhook);
        verify(webhookEventRepository).deleteAll(existingWebhook.getWebhookEvents().stream().toList());
        verify(webhookEventRepository).saveAll(anyList());
    }

    @Test
    void update_shouldThrowNotFoundException_whenWebhookDoesNotExist() {
        WebhookPostVm postVm = new WebhookPostVm(
            "https://example.com", "secret", "application/json", true, null);

        when(webhookRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.update(postVm, 99L))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_shouldNotSaveEvents_whenEventsListIsEmpty() {
        WebhookPostVm postVm = new WebhookPostVm(
            "https://updated.com", "newSecret", "application/json", false, Collections.emptyList());

        WebhookEvent oldEvent = new WebhookEvent();
        Webhook existingWebhook = new Webhook();
        existingWebhook.setId(1L);
        existingWebhook.setWebhookEvents(List.of(oldEvent));

        when(webhookRepository.findById(1L)).thenReturn(Optional.of(existingWebhook));
        when(webhookMapper.toUpdatedWebhook(existingWebhook, postVm)).thenReturn(existingWebhook);

        webhookService.update(postVm, 1L);

        verify(webhookRepository).save(existingWebhook);
        verify(webhookEventRepository).deleteAll(anyList());
        verify(webhookEventRepository, never()).saveAll(anyList());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_shouldDeleteWebhookAndItsEvents_whenExists() {
        when(webhookRepository.existsById(1L)).thenReturn(true);

        webhookService.delete(1L);

        verify(webhookEventRepository).deleteByWebhookId(1L);
        verify(webhookRepository).deleteById(1L);
    }

    @Test
    void delete_shouldThrowNotFoundException_whenWebhookDoesNotExist() {
        when(webhookRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> webhookService.delete(99L))
            .isInstanceOf(NotFoundException.class);

        verify(webhookEventRepository, never()).deleteByWebhookId(any());
        verify(webhookRepository, never()).deleteById(any());
    }

    // ── notifyToWebhook ───────────────────────────────────────────────────────

    @Test
    void notifyToWebhook_shouldCallApiAndUpdateStatus() {
        WebhookEventNotificationDto notificationDto = WebhookEventNotificationDto.builder()
            .notificationId(1L)
            .url("https://example.com")
            .secret("secret")
            .build();

        WebhookEventNotification notification = new WebhookEventNotification();
        when(webhookEventNotificationRepository.findById(notificationDto.getNotificationId()))
            .thenReturn(Optional.of(notification));

        webhookService.notifyToWebhook(notificationDto);

        verify(webHookApi).notify(
            notificationDto.getUrl(),
            notificationDto.getSecret(),
            notificationDto.getPayload());
        verify(webhookEventNotificationRepository).save(notification);
        assertThat(notification.getNotificationStatus()).isEqualTo(NotificationStatus.NOTIFIED);
    }
}
