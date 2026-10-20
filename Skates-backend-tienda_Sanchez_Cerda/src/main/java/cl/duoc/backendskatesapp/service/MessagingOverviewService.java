package cl.duoc.backendskatesapp.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import cl.duoc.backendskatesapp.config.MessagingProperties;
import cl.duoc.backendskatesapp.messaging.MessagingNames;
import cl.duoc.backendskatesapp.model.ProcessedMessage;
import cl.duoc.backendskatesapp.repository.ProcessedMessageRepository;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

@Service
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class MessagingOverviewService {

    private static final List<QueueAttributeName> OVERVIEW_ATTRIBUTES = List.of(
            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE);

    private final SqsClient sqsClient;
    private final ConsumerControlService consumerControlService;
    private final MessagingProperties properties;
    private final ProcessedMessageRepository processedMessageRepository;

    public MessagingOverviewService(
            SqsClient sqsClient,
            ConsumerControlService consumerControlService,
            MessagingProperties properties,
            ProcessedMessageRepository processedMessageRepository) {
        this.sqsClient = sqsClient;
        this.consumerControlService = consumerControlService;
        this.properties = properties;
        this.processedMessageRepository = processedMessageRepository;
    }

    public Overview getOverview() {
        return new Overview(
                properties.getTopicArn(),
                List.of(
                        queueStatus(MessagingNames.ORDERS_CONSUMER, properties.getQueues().getOrders()),
                        queueStatus(MessagingNames.INVENTORY_CONSUMER, properties.getQueues().getInventory()),
                        queueStatus(MessagingNames.NOTIFICATIONS_CONSUMER, properties.getQueues().getNotifications())),
                processedMessageRepository.findTop50ByOrderByProcessedAtDesc().stream()
                        .map(message -> new RecentEvent(
                                message.getConsumerName(),
                                message.getEventId(),
                                message.getEventType(),
                                message.getProcessedAt()))
                        .toList());
    }

    private QueueStatus queueStatus(String name, MessagingProperties.Queue queue) {
        Map<QueueAttributeName, String> mainQueue = attributes(queue.getUrl(), name);
        Map<QueueAttributeName, String> deadLetterQueue = attributes(queue.getDlqUrl(), name + " DLQ");
        return new QueueStatus(
                name,
                requiredCount(mainQueue, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, name),
                requiredCount(mainQueue, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, name),
                requiredCount(
                        deadLetterQueue,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        name + " DLQ"),
                consumerControlService.isPaused(name));
    }

    private Map<QueueAttributeName, String> attributes(String queueUrl, String name) {
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException("SQS URL is not configured for " + name);
        }
        return sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(OVERVIEW_ATTRIBUTES)
                .build()).attributes();
    }

    private long requiredCount(
            Map<QueueAttributeName, String> attributes,
            QueueAttributeName attribute,
            String queueName) {
        String value = attributes.get(attribute);
        if (value == null) {
            throw new IllegalStateException("SQS did not return " + attribute + " for " + queueName);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("SQS returned an invalid " + attribute + " for " + queueName, exception);
        }
    }

    public record Overview(String topicArn, List<QueueStatus> queues, List<RecentEvent> recentEvents) {
    }

    public record QueueStatus(
            String name,
            long visibleMessages,
            long inFlightMessages,
            long deadLetterMessages,
            boolean paused) {
    }

    public record RecentEvent(String consumerName, String eventId, String eventType, Instant processedAt) {
    }
}
