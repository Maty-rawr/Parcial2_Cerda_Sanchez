package cl.duoc.backend_skates_app;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import cl.duoc.backendskatesapp.config.MessagingProperties;
import cl.duoc.backendskatesapp.messaging.MessagingDomainEvent;
import cl.duoc.backendskatesapp.messaging.QueueMessagePollingService;
import cl.duoc.backendskatesapp.service.ConsumerControlService;
import cl.duoc.backendskatesapp.service.MessageProcessingService;
import cl.duoc.backendskatesapp.service.NonRecoverableMessageException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@ExtendWith(MockitoExtension.class)
class QueueMessagePollingServiceTest {

    private static final String ORDERS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123/orders";
    private static final String ORDERS_DLQ_URL = "https://sqs.us-east-1.amazonaws.com/123/orders-dlq";
    private static final MessagingDomainEvent EVENT = new MessagingDomainEvent(
            "event-123",
            "PAYMENT_COMPLETED",
            Instant.parse("2026-01-01T00:00:00Z"),
            Map.of("buyOrder", "order-1"));

    @Mock
    private SqsClient sqsClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private MessageProcessingService processingService;

    @Mock
    private ConsumerControlService controlService;

    private QueueMessagePollingService pollingService;

    @BeforeEach
    void setUp() {
        MessagingProperties properties = new MessagingProperties();
        properties.setMaxReceiveCount(5);
        properties.getQueues().getOrders().setUrl(ORDERS_QUEUE_URL);
        properties.getQueues().getOrders().setDlqUrl(ORDERS_DLQ_URL);
        properties.getQueues().getInventory().setUrl("https://sqs.us-east-1.amazonaws.com/123/inventory");
        properties.getQueues().getInventory().setDlqUrl("https://sqs.us-east-1.amazonaws.com/123/inventory-dlq");
        properties.getQueues().getNotifications().setUrl("https://sqs.us-east-1.amazonaws.com/123/notifications");
        properties.getQueues().getNotifications().setDlqUrl("https://sqs.us-east-1.amazonaws.com/123/notifications-dlq");
        pollingService = new QueueMessagePollingService(
                sqsClient,
                objectMapper,
                processingService,
                controlService,
                properties);
        when(controlService.isPaused(anyString())).thenReturn(false);
    }

    @Test
    void acknowledgesOnlyAfterSuccessfulProcessing() throws Exception {
        Message message = orderMessage(1);
        mockOrderMessage(message);
        when(objectMapper.readValue("{}", MessagingDomainEvent.class)).thenReturn(EVENT);
        when(processingService.process("orders", EVENT)).thenReturn(true);

        pollingService.poll("orders", ORDERS_QUEUE_URL, ORDERS_DLQ_URL);

        InOrder order = inOrder(processingService, sqsClient);
        order.verify(processingService).process("orders", EVENT);
        order.verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void leavesRecoverableFailureForSqsRedrive() throws Exception {
        mockOrderMessage(orderMessage(2));
        when(objectMapper.readValue("{}", MessagingDomainEvent.class)).thenReturn(EVENT);
        when(processingService.process("orders", EVENT)).thenThrow(new IllegalStateException("database unavailable"));

        pollingService.poll("orders", ORDERS_QUEUE_URL, ORDERS_DLQ_URL);

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void sendsNonRecoverableFailureToDlqBeforeAcknowledgingOriginal() throws Exception {
        Message message = orderMessage(1);
        mockOrderMessage(message);
        when(objectMapper.readValue("{}", MessagingDomainEvent.class)).thenReturn(EVENT);
        when(processingService.process("orders", EVENT))
                .thenThrow(new NonRecoverableMessageException("unsupported event"));
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("dlq-123").build());

        pollingService.poll("orders", ORDERS_QUEUE_URL, ORDERS_DLQ_URL);

        ArgumentCaptor<SendMessageRequest> request = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(request.capture());
        org.junit.jupiter.api.Assertions.assertEquals(ORDERS_DLQ_URL, request.getValue().queueUrl());
        org.junit.jupiter.api.Assertions.assertEquals("{}", request.getValue().messageBody());
        org.junit.jupiter.api.Assertions.assertEquals(
                "unsupported event",
                request.getValue().messageAttributes().get("failureCause").stringValue());
        org.junit.jupiter.api.Assertions.assertEquals(
                "1",
                request.getValue().messageAttributes().get("receiveCount").stringValue());

        InOrder order = inOrder(sqsClient);
        order.verify(sqsClient).sendMessage(any(SendMessageRequest.class));
        order.verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void doesNotAcknowledgeOriginalWhenDlqSendFails() throws Exception {
        mockOrderMessage(orderMessage(1));
        when(objectMapper.readValue("{}", MessagingDomainEvent.class)).thenReturn(EVENT);
        when(processingService.process("orders", EVENT))
                .thenThrow(new NonRecoverableMessageException("invalid event"));
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(new IllegalStateException("SQS unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> pollingService.poll("orders", ORDERS_QUEUE_URL, ORDERS_DLQ_URL));

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void logsSuccessfulDlqCopyEvenIfAcknowledgingOriginalFails() throws Exception {
        mockOrderMessage(orderMessage(1));
        when(objectMapper.readValue("{}", MessagingDomainEvent.class)).thenReturn(EVENT);
        when(processingService.process("orders", EVENT))
                .thenThrow(new NonRecoverableMessageException("invalid event"));
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("dlq-123").build());
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenThrow(new IllegalStateException("ack failed"));
        Logger logger = (Logger) LoggerFactory.getLogger(QueueMessagePollingService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> pollingService.poll("orders", ORDERS_QUEUE_URL, ORDERS_DLQ_URL));

            org.junit.jupiter.api.Assertions.assertTrue(appender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains("Sent non-recoverable message to DLQ")));
            verify(sqsClient).sendMessage(any(SendMessageRequest.class));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void doesNotLogSuccessWhenAcknowledgementFails() throws Exception {
        mockOrderMessage(orderMessage(1));
        when(objectMapper.readValue("{}", MessagingDomainEvent.class)).thenReturn(EVENT);
        when(processingService.process("orders", EVENT)).thenReturn(true);
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenThrow(new IllegalStateException("ack failed"));
        Logger logger = (Logger) LoggerFactory.getLogger(QueueMessagePollingService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            pollingService.poll("orders", ORDERS_QUEUE_URL, ORDERS_DLQ_URL);

            org.junit.jupiter.api.Assertions.assertTrue(appender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains("Retrying message queue=orders")));
            org.junit.jupiter.api.Assertions.assertFalse(appender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains("Processed consumer orders")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    private void mockOrderMessage(Message message) {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            ReceiveMessageRequest request = invocation.getArgument(0);
            List<Message> messages = ORDERS_QUEUE_URL.equals(request.queueUrl()) ? List.of(message) : List.of();
            return ReceiveMessageResponse.builder().messages(messages).build();
        });
    }

    private Message orderMessage(int receiveCount) {
        return Message.builder()
                .messageId("sqs-message-123")
                .receiptHandle("receipt-123")
                .body("{}")
                .attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, Integer.toString(receiveCount)))
                .build();
    }
}
