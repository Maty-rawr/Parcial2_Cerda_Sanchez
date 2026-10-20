package cl.duoc.backend_skates_app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cl.duoc.backendskatesapp.config.MessagingProperties;
import cl.duoc.backendskatesapp.messaging.MessagingDomainEvent;
import cl.duoc.backendskatesapp.messaging.SnsEventPublisher;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@ExtendWith(MockitoExtension.class)
class SnsEventPublisherTest {

    private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:123:events";
    private static final MessagingDomainEvent EVENT = new MessagingDomainEvent(
            "event-123",
            "PAYMENT_COMPLETED",
            Instant.parse("2026-01-01T00:00:00Z"),
            Map.of("buyOrder", "order-1"));

    @Mock
    private SnsClient snsClient;

    @Mock
    private ObjectMapper objectMapper;

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void publishesJsonAndEventTypeAttribute() throws Exception {
        when(objectMapper.writeValueAsString(EVENT)).thenReturn("{\"eventId\":\"event-123\"}");
        SnsEventPublisher publisher = new SnsEventPublisher(snsClient, objectMapper, properties(TOPIC_ARN));

        publisher.publishOutsideTransaction(EVENT);

        ArgumentCaptor<PublishRequest> request = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(request.capture());
        assertEquals(TOPIC_ARN, request.getValue().topicArn());
        assertEquals("{\"eventId\":\"event-123\"}", request.getValue().message());
        assertEquals(
                "PAYMENT_COMPLETED",
                request.getValue().messageAttributes().get("eventType").stringValue());
    }

    @Test
    void doesNotPublishFromTheNonTransactionalListenerDuringATransaction() {
        SnsEventPublisher publisher = new SnsEventPublisher(snsClient, objectMapper, properties(TOPIC_ARN));
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.publishOutsideTransaction(EVENT);

        verifyNoInteractions(snsClient, objectMapper);
    }

    @Test
    void rejectsMissingTopicArnInsteadOfSilentlyDroppingEvent() {
        SnsEventPublisher publisher = new SnsEventPublisher(snsClient, objectMapper, properties(" "));

        assertThrows(IllegalStateException.class, () -> publisher.publishAfterCommit(EVENT));

        verifyNoInteractions(snsClient, objectMapper);
    }

    private MessagingProperties properties(String topicArn) {
        MessagingProperties properties = new MessagingProperties();
        properties.setTopicArn(topicArn);
        return properties;
    }
}
