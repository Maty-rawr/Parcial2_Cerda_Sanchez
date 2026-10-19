package cl.duoc.backendskatesapp.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@Component
@Profile({"all", "catalog", "commerce"})
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class SnsEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(SnsEventPublisher.class);

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String topicArn;

    public SnsEventPublisher(
            SnsClient snsClient,
            ObjectMapper objectMapper,
            @Value("${app.messaging.topic-arn}") String topicArn) {
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.topicArn = topicArn;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(MessagingDomainEvent event) {
        publish(event);
    }

    @EventListener
    public void publishOutsideTransaction(MessagingDomainEvent event) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive()) {
            return;
        }
        publish(event);
    }

    private void publish(MessagingDomainEvent event) {
        if (topicArn.isBlank()) {
            throw new IllegalStateException("SNS_TOPIC_ARN must be configured when messaging is enabled");
        }

        try {
            snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(objectMapper.writeValueAsString(event))
                    .messageAttributes(java.util.Map.of(
                            "eventType",
                            MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(event.eventType())
                                    .build()))
                    .build());
            logger.info("Published SNS event id={} type={}", event.eventId(), event.eventType());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize SNS event " + event.eventId(), exception);
        }
    }
}
