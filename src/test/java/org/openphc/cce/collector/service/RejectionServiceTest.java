package org.openphc.cce.collector.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.collector.domain.model.InboundEvent;
import org.openphc.cce.collector.domain.model.enums.InboundStatus;
import org.openphc.cce.collector.domain.model.enums.RejectionReason;
import org.openphc.cce.collector.domain.repository.InboundEventRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RejectionService}.
 */
@ExtendWith(MockitoExtension.class)
class RejectionServiceTest {

    @Mock
    private InboundEventRepository repository;

    @InjectMocks
    private RejectionService rejectionService;

    @Test
    @DisplayName("Sets status to REJECTED on the event")
    void setsStatusToRejected() {
        InboundEvent event = buildEvent();
        when(repository.save(any())).thenReturn(event);

        rejectionService.recordRejection(event, RejectionReason.INVALID_FHIR, "Bad resource");

        assertThat(event.getStatus()).isEqualTo(InboundStatus.REJECTED);
    }

    @Test
    @DisplayName("Sets rejection reason as string name of enum")
    void setsRejectionReason() {
        InboundEvent event = buildEvent();
        when(repository.save(any())).thenReturn(event);

        rejectionService.recordRejection(event, RejectionReason.KAFKA_PUBLISH_FAILURE, "timeout");

        assertThat(event.getRejectionReason()).isEqualTo("KAFKA_PUBLISH_FAILURE");
    }

    @Test
    @DisplayName("Sets error details on the event")
    void setsErrorDetails() {
        InboundEvent event = buildEvent();
        when(repository.save(any())).thenReturn(event);

        rejectionService.recordRejection(event, RejectionReason.INVALID_JSON, "Empty payload");

        assertThat(event.getErrorDetails()).isEqualTo("Empty payload");
    }

    @Test
    @DisplayName("Persists the updated event via repository")
    void persistsViaRepository() {
        InboundEvent event = buildEvent();
        when(repository.save(any())).thenReturn(event);

        rejectionService.recordRejection(event, RejectionReason.INVALID_ENVELOPE, "missing id");

        ArgumentCaptor<InboundEvent> captor = ArgumentCaptor.forClass(InboundEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(event);
    }

    @Test
    @DisplayName("Handles all rejection reasons correctly")
    void handlesAllRejectionReasons() {
        for (RejectionReason reason : RejectionReason.values()) {
            InboundEvent event = buildEvent();
            when(repository.save(any())).thenReturn(event);

            rejectionService.recordRejection(event, reason, "test");

            assertThat(event.getRejectionReason()).isEqualTo(reason.name());
            assertThat(event.getStatus()).isEqualTo(InboundStatus.REJECTED);
        }
    }

    private InboundEvent buildEvent() {
        InboundEvent event = InboundEvent.builder()
                .cloudeventsId("evt-001")
                .source("test-source")
                .type("test.type")
                .subject("patient-1")
                .rawPayload("{}")
                .build();
        event.setId(UUID.randomUUID());
        return event;
    }
}
