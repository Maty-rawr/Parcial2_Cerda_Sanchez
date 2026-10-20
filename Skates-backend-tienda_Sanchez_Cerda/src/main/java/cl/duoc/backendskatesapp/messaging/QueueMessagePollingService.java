package cl.duoc.backendskatesapp.messaging;

import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import cl.duoc.backendskatesapp.config.MessagingProperties;
import cl.duoc.backendskatesapp.service.ConsumerControlService;
import cl.duoc.backendskatesapp.service.MessageProcessingService;
import cl.duoc.backendskatesapp.service.NonRecoverableMessageException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
@Profile("worker")
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class QueueMessagePollingService {

    private static final Logger logger = LoggerFactory.getLogger(QueueMessagePollingService.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MessageProcessingService processingService;
    private final ConsumerControlService controlService;
    private final MessagingProperties properties;

    public QueueMessagePollingService(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            MessageProcessingService processingService,
            ConsumerControlService controlService,
            MessagingProperties properties) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.processingService = processingService;
        this.controlService = controlService;
        this.properties = properties;
    }

    @PostConstruct
    void validateConfiguration() {
        if (properties.getMaxReceiveCount() < 1) {
            throw new IllegalStateException("SQS_MAX_RECEIVE_COUNT must be greater than zero");
        }
        requireQueue(properties.getQueues().getOrders(), MessagingNames.ORDERS_CONSUMER);
        requireQueue(properties.getQueues().getInventory(), MessagingNames.INVENTORY_CONSUMER);
        requireQueue(properties.getQueues().getNotifications(), MessagingNames.NOTIFICATIONS_CONSUMER);
    }

    public void poll(String consumerName, String queueUrl, String dlqUrl) {
        if (controlService.isPaused(consumerName)) {
            return;
        }
        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                .build()).messages();
        for (Message message : messages) {
            process(consumerName, queueUrl, dlqUrl, message);
        }
    }

    private void process(String consumerName, String queueUrl, String dlqUrl, Message message) {
        int attempt = receiveCount(message);
        String eventId = message.messageId();
        try {
            MessagingDomainEvent event = objectMapper.readValue(message.body(), MessagingDomainEvent.class);
            if (event != null && event.eventId() != null && !event.eventId().isBlank()) {
                eventId = event.eventId();
            }
            boolean newlyProcessed = processingService.process(consumerName, event);
            acknowledge(queueUrl, message);
            logger.info("{} consumer {} event id={} type={}",
                    newlyProcessed ? "Processed" : "Acknowledged duplicate",
                    consumerName,
                    eventId,
                    event.eventType());
            if (newlyProcessed && MessagingNames.NOTIFICATIONS_CONSUMER.equals(consumerName)) {
                logger.info("Notification task recorded event id={} type={}", eventId, event.eventType());
            }
        } catch (JacksonException | NonRecoverableMessageException exception) {
            moveToDeadLetterQueue(consumerName, queueUrl, dlqUrl, message, eventId, attempt, exception.getMessage());
        } catch (RuntimeException exception) {
            if (attempt >= properties.getMaxReceiveCount()) {
                logger.error(
                        "Message exceeded max receives and will be moved to DLQ queue={} id={} attempt={} cause={}",
                        consumerName,
                        eventId,
                        attempt,
                        exception.getMessage(),
                        exception);
            } else {
                logger.warn(
                        "Retrying message queue={} id={} attempt={} cause={}",
                        consumerName,
                        eventId,
                        attempt,
                        exception.getMessage(),
                        exception);
            }
        }
    }

    private void moveToDeadLetterQueue(
            String consumerName,
            String queueUrl,
            String dlqUrl,
            Message message,
            String eventId,
            int attempt,
            String cause) {
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(dlqUrl)
                .messageBody(message.body())
                .messageAttributes(Map.of(
                        "originalMessageId", stringAttribute(message.messageId()),
                        "receiveCount", stringAttribute(Integer.toString(attempt)),
                        "failureCause", stringAttribute(truncate(cause, 240))))
                .build());
        logger.error(
                "Sent non-recoverable message to DLQ queue={} id={} attempt={} cause={}",
                consumerName,
                eventId,
                attempt,
                cause);
        acknowledge(queueUrl, message);
    }

    private void acknowledge(String queueUrl, Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }

    private int receiveCount(Message message) {
        String value = message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            logger.warn("SQS did not return a valid ApproximateReceiveCount for message {}", message.messageId());
            return 1;
        }
    }

    private MessageAttributeValue stringAttribute(String value) {
        return MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(value)
                .build();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "No failure details";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void requireQueue(MessagingProperties.Queue queue, String consumerName) {
        if (queue == null || queue.getUrl() == null || queue.getUrl().isBlank()
                || queue.getDlqUrl() == null || queue.getDlqUrl().isBlank()) {
            throw new IllegalStateException(
                    "Both SQS queue and DLQ URLs must be configured for the " + consumerName + " consumer");
        }
    }
}
