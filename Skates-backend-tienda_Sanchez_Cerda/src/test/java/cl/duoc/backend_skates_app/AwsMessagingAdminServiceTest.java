package cl.duoc.backend_skates_app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.duoc.backendskatesapp.config.MessagingProperties;
import cl.duoc.backendskatesapp.service.AwsMessagingAdminService;
import cl.duoc.backendskatesapp.service.ConsumerControlService;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.DeleteTopicRequest;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicResponse;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicRequest;
import software.amazon.awssdk.services.sns.model.ListTopicsResponse;
import software.amazon.awssdk.services.sns.model.ListTopicsRequest;
import software.amazon.awssdk.services.sns.model.Subscription;
import software.amazon.awssdk.services.sns.model.Topic;
import software.amazon.awssdk.services.sns.model.UnsubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;
import software.amazon.awssdk.services.sns.model.SetSubscriptionAttributesRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.StartMessageMoveTaskRequest;
import software.amazon.awssdk.services.sqs.model.StartMessageMoveTaskResponse;
import cl.duoc.backendskatesapp.model.ConsumerControl;

@ExtendWith(MockitoExtension.class)
class AwsMessagingAdminServiceTest {

    private static final String DLQ_URL = "https://sqs.us-east-1.amazonaws.com/123/orders-dlq";
    private static final String ORDERS_URL = "https://sqs.us-east-1.amazonaws.com/123/orders";
    private static final String DLQ_ARN = "arn:aws:sqs:us-east-1:123:orders-dlq";

    @Mock
    private SnsClient snsClient;

    @Mock
    private SqsClient sqsClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ConsumerControlService consumerControlService;

    private AwsMessagingAdminService service;

    @BeforeEach
    void setUp() {
        service = new AwsMessagingAdminService(
                snsClient, sqsClient, objectMapper, consumerControlService, properties(7));
    }

    @Test
    void createsQueueWithConfiguredReceiveCountAndDeadLetterQueue() throws Exception {
        when(sqsClient.createQueue(any(CreateQueueRequest.class))).thenAnswer(invocation -> {
            CreateQueueRequest request = invocation.getArgument(0);
            String queueUrl = request.queueName().endsWith("-dlq") ? DLQ_URL : ORDERS_URL;
            return CreateQueueResponse.builder().queueUrl(queueUrl).build();
        });
        when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
                .thenReturn(GetQueueAttributesResponse.builder()
                        .attributes(Map.of(QueueAttributeName.QUEUE_ARN, DLQ_ARN))
                        .build());
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"deadLetterTargetArn\":\"" + DLQ_ARN + "\",\"maxReceiveCount\":7}");

        assertEquals(ORDERS_URL, service.createQueue("orders", 60, 345600));

        ArgumentCaptor<CreateQueueRequest> requests = ArgumentCaptor.forClass(CreateQueueRequest.class);
        verify(sqsClient, times(2)).createQueue(requests.capture());
        CreateQueueRequest dlqRequest = requests.getAllValues().get(0);
        CreateQueueRequest queueRequest = requests.getAllValues().get(1);
        assertEquals("orders-dlq", dlqRequest.queueName());
        assertEquals("orders", queueRequest.queueName());
        assertEquals("60", queueRequest.attributes().get(QueueAttributeName.VISIBILITY_TIMEOUT));
        assertEquals("345600", queueRequest.attributes().get(QueueAttributeName.MESSAGE_RETENTION_PERIOD));
        assertEquals(
                "{\"deadLetterTargetArn\":\"" + DLQ_ARN + "\",\"maxReceiveCount\":7}",
                queueRequest.attributes().get(QueueAttributeName.REDRIVE_POLICY));
    }

    @Test
    void rejectsNonPositiveReceiveCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AwsMessagingAdminService(
                        snsClient, sqsClient, objectMapper, consumerControlService, properties(0)));
    }

    @Test
    void subscriptionAddsSnsPermissionWithoutRemovingExistingQueuePolicy() throws Exception {
        String topicArn = "arn:aws:sns:us-east-1:123:events";
        String queueArn = "arn:aws:sqs:us-east-1:123:orders";
        String existingPolicyJson = "{\"Version\":\"2012-10-17\",\"Statement\":[]}";
        Map<String, Object> existingStatement = Map.of(
                "Effect", "Allow",
                "Principal", Map.of("AWS", "arn:aws:iam::123:role/existing"),
                "Action", "sqs:ReceiveMessage",
                "Resource", queueArn);
        Map<String, Object> existingPolicy = new HashMap<>();
        existingPolicy.put("Version", "2012-10-17");
        existingPolicy.put("Statement", new ArrayList<>(List.of(existingStatement)));
        doReturn(existingPolicy).when(objectMapper).readValue(anyString(), any(TypeReference.class));
        when(objectMapper.writeValueAsString(any())).thenReturn("merged-policy-json");
        when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenAnswer(invocation -> {
            GetQueueAttributesRequest request = invocation.getArgument(0);
            if (request.attributeNames().contains(QueueAttributeName.QUEUE_ARN)) {
                return GetQueueAttributesResponse.builder()
                        .attributes(Map.of(QueueAttributeName.QUEUE_ARN, queueArn))
                        .build();
            }
            return GetQueueAttributesResponse.builder()
                    .attributes(Map.of(QueueAttributeName.POLICY, existingPolicyJson))
                    .build();
        });
        when(snsClient.subscribe(any(SubscribeRequest.class)))
                .thenReturn(SubscribeResponse.builder().subscriptionArn("subscription-123").build());

        assertEquals(
                "subscription-123",
                service.subscribe(topicArn, ORDERS_URL, Map.of("eventType", List.of("PAYMENT_COMPLETED"))));

        ArgumentCaptor<Object> policy = ArgumentCaptor.forClass(Object.class);
        verify(objectMapper, times(2)).writeValueAsString(policy.capture());
        assertTrue(policy.getAllValues().get(0) instanceof Map<?, ?>);
        Map<?, ?> mergedPolicy = (Map<?, ?>) policy.getAllValues().get(0);
        assertEquals("2012-10-17", mergedPolicy.get("Version"));
        assertTrue(mergedPolicy.get("Statement") instanceof List<?>);
        List<?> statements = (List<?>) mergedPolicy.get("Statement");
        assertTrue(statements.contains(existingStatement));
        assertEquals(2, statements.size());

        ArgumentCaptor<SetQueueAttributesRequest> queuePolicy = ArgumentCaptor.forClass(SetQueueAttributesRequest.class);
        verify(sqsClient).setQueueAttributes(queuePolicy.capture());
        assertEquals(
                "merged-policy-json",
                queuePolicy.getValue().attributes().get(QueueAttributeName.POLICY));
        ArgumentCaptor<SetSubscriptionAttributesRequest> subscriptionAttributes =
                ArgumentCaptor.forClass(SetSubscriptionAttributesRequest.class);
        verify(snsClient, times(2)).setSubscriptionAttributes(subscriptionAttributes.capture());
        assertTrue(subscriptionAttributes.getAllValues().stream()
                .anyMatch(attribute -> "RawMessageDelivery".equals(attribute.attributeName())
                        && "true".equals(attribute.attributeValue())));
        assertTrue(subscriptionAttributes.getAllValues().stream()
                .anyMatch(attribute -> "FilterPolicy".equals(attribute.attributeName())));
    }

    @Test
    void managesTopicsAndSubscriptions() {
        String topicArn = "arn:aws:sns:us-east-1:123:events";
        when(snsClient.createTopic(any(java.util.function.Consumer.class)))
                .thenReturn(CreateTopicResponse.builder().topicArn(topicArn).build());
        when(snsClient.listTopics(any(ListTopicsRequest.class)))
                .thenReturn(ListTopicsResponse.builder().topics(Topic.builder().topicArn(topicArn).build()).build());
        when(snsClient.listSubscriptionsByTopic(any(ListSubscriptionsByTopicRequest.class)))
                .thenReturn(ListSubscriptionsByTopicResponse.builder()
                        .subscriptions(Subscription.builder().subscriptionArn("subscription-123").build())
                        .build());

        assertEquals(topicArn, service.createTopic("events"));
        assertEquals(java.util.List.of(topicArn), service.listTopics());
        assertEquals(
                java.util.List.of("subscription-123"),
                service.listSubscriptions(topicArn));
        service.deleteTopic(topicArn);
        service.deleteSubscription("subscription-123");

        verify(snsClient).deleteTopic(any(DeleteTopicRequest.class));
        verify(snsClient).unsubscribe(any(UnsubscribeRequest.class));
    }

    @Test
    void listsQueueStatusAndDeletesQueue() {
        when(sqsClient.listQueues(any(ListQueuesRequest.class)))
                .thenReturn(ListQueuesResponse.builder().queueUrls(ORDERS_URL).build());
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl(ORDERS_URL).build());
        when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class)))
                .thenReturn(GetQueueAttributesResponse.builder()
                        .attributes(Map.of(QueueAttributeName.VISIBILITY_TIMEOUT, "60"))
                        .build());
        when(sqsClient.deleteQueue(any(DeleteQueueRequest.class)))
                .thenReturn(DeleteQueueResponse.builder().build());

        assertEquals(java.util.List.of(ORDERS_URL), service.listQueues());
        assertEquals("60", service.queueStatus("orders").get("VisibilityTimeout"));
        service.deleteQueue("orders");

        verify(sqsClient).deleteQueue(any(DeleteQueueRequest.class));
    }

    @Test
    void pausesAndResumesNamedConsumersThroughControlService() {
        ConsumerControl paused = new ConsumerControl("orders", true);
        ConsumerControl resumed = new ConsumerControl("orders", false);
        when(consumerControlService.setPaused("orders", true)).thenReturn(paused);
        when(consumerControlService.setPaused("orders", false)).thenReturn(resumed);

        assertEquals(paused, service.setConsumerPaused("orders", true));
        assertEquals(resumed, service.setConsumerPaused("orders", false));
        assertThrows(IllegalArgumentException.class, () -> service.setConsumerPaused("unknown", true));

        verify(consumerControlService).setPaused("orders", true);
        verify(consumerControlService).setPaused("orders", false);
    }

    @Test
    void redrivesAndPurgesOnlyDeadLetterQueues() {
        String ordersArn = "arn:aws:sqs:us-east-1:123:orders";
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class))).thenAnswer(invocation -> {
            GetQueueUrlRequest request = invocation.getArgument(0);
            String queueUrl = "orders-dlq".equals(request.queueName()) ? DLQ_URL : ORDERS_URL;
            return GetQueueUrlResponse.builder().queueUrl(queueUrl).build();
        });
        when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenAnswer(invocation -> {
            GetQueueAttributesRequest request = invocation.getArgument(0);
            String queueArn = DLQ_URL.equals(request.queueUrl()) ? DLQ_ARN : ordersArn;
            return GetQueueAttributesResponse.builder()
                    .attributes(Map.of(QueueAttributeName.QUEUE_ARN, queueArn))
                    .build();
        });
        when(sqsClient.startMessageMoveTask(any(StartMessageMoveTaskRequest.class)))
                .thenReturn(StartMessageMoveTaskResponse.builder().taskHandle("redrive-123").build());
        when(sqsClient.purgeQueue(any(PurgeQueueRequest.class)))
                .thenReturn(PurgeQueueResponse.builder().build());

        assertEquals("redrive-123", service.redriveDlq("orders-dlq", "orders"));
        service.purgeDlq("orders-dlq");
        assertThrows(IllegalArgumentException.class, () -> service.purgeDlq("orders"));

        verify(sqsClient).startMessageMoveTask(any(StartMessageMoveTaskRequest.class));
        verify(sqsClient).purgeQueue(any(PurgeQueueRequest.class));
        verify(sqsClient, never()).deleteQueue(any(DeleteQueueRequest.class));
    }

    private MessagingProperties properties(int maxReceiveCount) {
        MessagingProperties properties = new MessagingProperties();
        properties.setMaxReceiveCount(maxReceiveCount);
        return properties;
    }
}
