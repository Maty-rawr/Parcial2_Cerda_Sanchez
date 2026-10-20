package cl.duoc.backend_skates_app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import cl.duoc.backendskatesapp.config.MessagingProperties;
import cl.duoc.backendskatesapp.model.ProcessedMessage;
import cl.duoc.backendskatesapp.repository.ProcessedMessageRepository;
import cl.duoc.backendskatesapp.service.ConsumerControlService;
import cl.duoc.backendskatesapp.service.MessagingOverviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

@ExtendWith(MockitoExtension.class)
class MessagingOverviewServiceTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private ConsumerControlService controlService;

    @Mock
    private ProcessedMessageRepository processedMessageRepository;

    @Test
    void returnsQueueAndDeadLetterMetricsWithoutReceivingMessages() {
        MessagingProperties properties = new MessagingProperties();
        properties.setTopicArn("arn:aws:sns:us-east-1:123:skates-events");
        setQueue(properties.getQueues().getOrders(), "orders");
        setQueue(properties.getQueues().getInventory(), "inventory");
        setQueue(properties.getQueues().getNotifications(), "notifications");
        when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
                .thenReturn(GetQueueAttributesResponse.builder()
                        .attributes(Map.of(
                                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "4",
                                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "2"))
                        .build());
        when(controlService.isPaused("orders")).thenReturn(true);
        when(controlService.isPaused("inventory")).thenReturn(false);
        when(controlService.isPaused("notifications")).thenReturn(false);
        ProcessedMessage event = new ProcessedMessage(
                "orders", "evt-123", "PAYMENT_COMPLETED", "{\"orderId\":\"order-1\"}");
        when(processedMessageRepository.findTop50ByOrderByProcessedAtDesc()).thenReturn(List.of(event));

        MessagingOverviewService.Overview overview =
                new MessagingOverviewService(sqsClient, controlService, properties, processedMessageRepository)
                        .getOverview();

        assertEquals("arn:aws:sns:us-east-1:123:skates-events", overview.topicArn());
        assertEquals(3, overview.queues().size());
        assertEquals("orders", overview.queues().get(0).name());
        assertEquals(4, overview.queues().get(0).visibleMessages());
        assertEquals(2, overview.queues().get(0).inFlightMessages());
        assertEquals(4, overview.queues().get(0).deadLetterMessages());
        assertEquals(true, overview.queues().get(0).paused());
        assertEquals(1, overview.recentEvents().size());
        assertEquals("evt-123", overview.recentEvents().get(0).eventId());
        assertEquals("PAYMENT_COMPLETED", overview.recentEvents().get(0).eventType());
        verify(sqsClient, org.mockito.Mockito.times(6)).getQueueAttributes(any(GetQueueAttributesRequest.class));
    }

    private void setQueue(MessagingProperties.Queue queue, String name) {
        queue.setUrl("https://sqs.us-east-1.amazonaws.com/123/" + name);
        queue.setDlqUrl("https://sqs.us-east-1.amazonaws.com/123/" + name + "-dlq");
    }
}
