package cl.duoc.backendskatesapp.messaging;

import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

@Component
@Profile("worker")
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class QueueMessageConsumer {

    private static final Logger logger = LoggerFactory.getLogger(QueueMessageConsumer.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MessageProcessingService processingService;
    private final ConsumerControlService controlService;
    private final int maxReceiveCount;
    private final List<QueueBinding> queues;

    public QueueMessageConsumer(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            MessageProcessingService processingService,
            ConsumerControlService controlService,
            @Value("${app.messaging.max-receive-count}") int maxReceiveCount,
            @Value("${app.messaging.orders-queue-url}") String ordersQueueUrl,
            @Value("${app.messaging.orders-dlq-url}") String ordersDlqUrl,
            @Value("${app.messaging.inventory-queue-url}") String inventoryQueueUrl,
            @Value("${app.messaging.inventory-dlq-url}") String inventoryDlqUrl,
            @Value("${app.messaging.notifications-queue-url}") String notificationsQueueUrl,
            @Value("${app.messaging.notifications-dlq-url}") String notificationsDlqUrl) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.processingService = processingService;
        this.controlService = controlService;
        this.maxReceiveCount = maxReceiveCount;
        this.queues = List.of(
                new QueueBinding(MessagingNames.ORDERS_CONSUMER, ordersQueueUrl, ordersDlqUrl),
                new QueueBinding(MessagingNames.INVENTORY_CONSUMER, inventoryQueueUrl, inventoryDlqUrl),
                new QueueBinding(MessagingNames.NOTIFICATIONS_CONSUMER, notificationsQueueUrl, notificationsDlqUrl));
    }

    @PostConstruct
    void validateQueueConfiguration() {
        if (maxReceiveCount < 1) {
            throw new IllegalStateException("SQS_MAX_RECEIVE_COUNT must be greater than zero");
        }
        queues.forEach(queue -> {
            if (queue.queueUrl().isBlank() || queue.dlqUrl().isBlank()) {
                throw new IllegalStateException(
                        "Both SQS queue and DLQ URLs must be configured for the " + queue.name() + " consumer");
            }
        });
    }

    @Scheduled(fixedDelayString = "${app.messaging.poll-interval-ms}")
    public void pollQueues() {
        for (QueueBinding queue : queues) {
            if (!controlService.isPaused(queue.name())) {
                poll(queue);
            }
        }
    }

    private void poll(QueueBinding queue) {
        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queue.queueUrl())
                .maxNumberOfMessages(10)
                .waitTimeSeconds(2)
                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                .build()).messages();

        for (Message message : messages) {
            process(queue, message);
        }
    }

    private void process(QueueBinding queue, Message message) {
        int attempt = receiveCount(message);
        String eventId = message.messageId();
        try {
            MessagingDomainEvent event = objectMapper.readValue(message.body(), MessagingDomainEvent.class);
            eventId = event.eventId() == null || event.eventId().isBlank() ? message.messageId() : event.eventId();
            boolean newlyProcessed = processingService.process(queue.name(), event);
            logger.info("{} consumer {} event id={} type={}",
                    newlyProcessed ? "Processed" : "Acknowledged duplicate",
                    queue.name(),
                    eventId,
                    event.eventType());
            if (newlyProcessed && MessagingNames.NOTIFICATIONS_CONSUMER.equals(queue.name())) {
                logger.info("Notification task recorded event id={} type={}", eventId, event.eventType());
            }
            acknowledge(queue.queueUrl(), message);
        } catch (JacksonException | NonRecoverableMessageException exception) {
            moveToDeadLetterQueue(queue, message, eventId, attempt, exception.getMessage());
        } catch (RuntimeException exception) {
            if (attempt >= maxReceiveCount) {
                logger.error(
                        "Message will be moved to DLQ queue={} id={} attempt={} cause={}",
                        queue.name(),
                        eventId,
                        attempt,
                        exception.getMessage(),
                        exception);
            } else {
                logger.warn(
                        "Retrying message queue={} id={} attempt={} cause={}",
                        queue.name(),
                        eventId,
                        attempt,
                        exception.getMessage(),
                        exception);
            }
        }
    }

    private void moveToDeadLetterQueue(
            QueueBinding queue, Message message, String eventId, int attempt, String cause) {
        logger.error(
                "Sending non-recoverable message to DLQ queue={} id={} attempt={} cause={}",
                queue.name(),
                eventId,
                attempt,
                cause);
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queue.dlqUrl())
                .messageBody(message.body())
                .messageAttributes(Map.of(
                        "originalMessageId", stringAttribute(message.messageId()),
                        "receiveCount", stringAttribute(Integer.toString(attempt)),
                        "failureCause", stringAttribute(truncate(cause, 240))))
                .build());
        acknowledge(queue.queueUrl(), message);
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

    private record QueueBinding(String name, String queueUrl, String dlqUrl) {
    }
}
