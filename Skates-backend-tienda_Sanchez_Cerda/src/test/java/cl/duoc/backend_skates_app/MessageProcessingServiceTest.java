package cl.duoc.backend_skates_app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.duoc.backendskatesapp.messaging.MessagingDomainEvent;
import cl.duoc.backendskatesapp.model.ProcessedMessage;
import cl.duoc.backendskatesapp.repository.ProcessedMessageRepository;
import cl.duoc.backendskatesapp.service.MessageProcessingService;
import cl.duoc.backendskatesapp.service.NonRecoverableMessageException;

@ExtendWith(MockitoExtension.class)
class MessageProcessingServiceTest {

    @Mock
    private ProcessedMessageRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    private MessageProcessingService service;

    @BeforeEach
    void setUp() {
        service = new MessageProcessingService(repository, objectMapper);
    }

    @Test
    void persistsFirstSupportedEventForConsumer() throws Exception {
        MessagingDomainEvent event = event("PAYMENT_COMPLETED");
        when(repository.existsByConsumerNameAndEventId("orders", event.eventId())).thenReturn(false);
        when(objectMapper.writeValueAsString(event.payload())).thenReturn("{\"buyOrder\":\"order-1\"}");

        assertTrue(service.process("orders", event));

        verify(repository).saveAndFlush(any(ProcessedMessage.class));
    }

    @Test
    void acknowledgesAlreadyProcessedEventWithoutSavingAgain() {
        MessagingDomainEvent event = event("PAYMENT_COMPLETED");
        when(repository.existsByConsumerNameAndEventId("orders", event.eventId())).thenReturn(true);

        assertFalse(service.process("orders", event));

        verify(repository, never()).saveAndFlush(any(ProcessedMessage.class));
    }

    @Test
    void rejectsUnsupportedEventForDomainQueue() {
        MessagingDomainEvent event = event("INVENTORY_UPDATED");

        assertThrows(NonRecoverableMessageException.class, () -> service.process("orders", event));

        verify(repository, never()).saveAndFlush(any(ProcessedMessage.class));
    }

    private MessagingDomainEvent event(String eventType) {
        return new MessagingDomainEvent("event-123", eventType, Instant.now(), Map.of("buyOrder", "order-1"));
    }
}
