package cl.duoc.backendskatesapp.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import cl.duoc.backendskatesapp.model.ConsumerControl;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.DeleteTopicRequest;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicRequest;
import software.amazon.awssdk.services.sns.model.ListTopicsRequest;
import software.amazon.awssdk.services.sns.model.SetSubscriptionAttributesRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.UnsubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.StartMessageMoveTaskRequest;

@Service
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class AwsMessagingAdminService {

    private static final Pattern RESOURCE_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,70}$");
    private static final List<QueueAttributeName> QUEUE_ATTRIBUTES = List.of(QueueAttributeName.ALL);

    private final SnsClient snsClient;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final ConsumerControlService consumerControlService;

    public AwsMessagingAdminService(
            SnsClient snsClient,
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            ConsumerControlService consumerControlService) {
        this.snsClient = snsClient;
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.consumerControlService = consumerControlService;
    }

    public String createTopic(String name) {
        validateName(name);
        CreateTopicResponse response = snsClient.createTopic(request -> request.name(name));
        return response.topicArn();
    }

    public void deleteTopic(String topicArn) {
        requireValue(topicArn, "topicArn");
        snsClient.deleteTopic(DeleteTopicRequest.builder().topicArn(topicArn).build());
    }

    public List<String> listTopics() {
        return snsClient.listTopics(ListTopicsRequest.builder().build())
                .topics()
                .stream()
                .map(topic -> topic.topicArn())
                .toList();
    }

    public String createQueue(String name, int visibilityTimeoutSeconds, int retentionSeconds) {
        validateName(name);
        if (visibilityTimeoutSeconds < 1 || visibilityTimeoutSeconds > 43200) {
            throw new IllegalArgumentException("visibilityTimeoutSeconds must be between 1 and 43200");
        }
        if (retentionSeconds < 60 || retentionSeconds > 1209600) {
            throw new IllegalArgumentException("retentionSeconds must be between 60 and 1209600");
        }

        String dlqName = name + "-dlq";
        String dlqUrl = sqsClient.createQueue(CreateQueueRequest.builder()
                .queueName(dlqName)
                .attributes(Map.of(
                        QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600",
                        QueueAttributeName.SQS_MANAGED_SSE_ENABLED, "true"))
                .build()).queueUrl();
        String dlqArn = queueArn(dlqUrl);
        String redrivePolicy = json(Map.of(
                "deadLetterTargetArn", dlqArn,
                "maxReceiveCount", 5));

        return sqsClient.createQueue(CreateQueueRequest.builder()
                .queueName(name)
                .attributes(Map.of(
                        QueueAttributeName.VISIBILITY_TIMEOUT, Integer.toString(visibilityTimeoutSeconds),
                        QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "20",
                        QueueAttributeName.MESSAGE_RETENTION_PERIOD, Integer.toString(retentionSeconds),
                        QueueAttributeName.SQS_MANAGED_SSE_ENABLED, "true",
                        QueueAttributeName.REDRIVE_POLICY, redrivePolicy))
                .build()).queueUrl();
    }

    public void deleteQueue(String name) {
        validateName(name);
        String queueUrl = queueUrl(name);
        sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl).build());
    }

    public List<String> listQueues() {
        return sqsClient.listQueues(ListQueuesRequest.builder().build()).queueUrls();
    }

    public Map<String, String> queueStatus(String name) {
        validateName(name);
        return sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl(name))
                .attributeNames(QUEUE_ATTRIBUTES)
                .build()).attributesAsStrings();
    }

    public String subscribe(
            String topicArn,
            String queueUrl,
            Map<String, Object> filterPolicy) {
        requireValue(topicArn, "topicArn");
        requireValue(queueUrl, "queueUrl");
        String queueArn = queueArn(queueUrl);
        allowTopicToSendToQueue(topicArn, queueUrl, queueArn);

        String subscriptionArn = snsClient.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .returnSubscriptionArn(true)
                .build()).subscriptionArn();
        snsClient.setSubscriptionAttributes(SetSubscriptionAttributesRequest.builder()
                .subscriptionArn(subscriptionArn)
                .attributeName("RawMessageDelivery")
                .attributeValue("true")
                .build());
        if (filterPolicy != null && !filterPolicy.isEmpty()) {
            snsClient.setSubscriptionAttributes(SetSubscriptionAttributesRequest.builder()
                    .subscriptionArn(subscriptionArn)
                    .attributeName("FilterPolicy")
                    .attributeValue(json(filterPolicy))
                    .build());
        }
        return subscriptionArn;
    }

    public List<String> listSubscriptions(String topicArn) {
        requireValue(topicArn, "topicArn");
        return snsClient.listSubscriptionsByTopic(ListSubscriptionsByTopicRequest.builder()
                        .topicArn(topicArn)
                        .build())
                .subscriptions()
                .stream()
                .map(subscription -> subscription.subscriptionArn())
                .toList();
    }

    public void deleteSubscription(String subscriptionArn) {
        requireValue(subscriptionArn, "subscriptionArn");
        snsClient.unsubscribe(UnsubscribeRequest.builder().subscriptionArn(subscriptionArn).build());
    }

    public ConsumerControl setConsumerPaused(String consumerName, boolean paused) {
        validateConsumerName(consumerName);
        return consumerControlService.setPaused(consumerName, paused);
    }

    public String redriveDlq(String dlqName, String destinationQueueName) {
        validateName(dlqName);
        validateName(destinationQueueName);
        String sourceArn = queueArn(queueUrl(dlqName));
        String destinationArn = queueArn(queueUrl(destinationQueueName));
        return sqsClient.startMessageMoveTask(StartMessageMoveTaskRequest.builder()
                .sourceArn(sourceArn)
                .destinationArn(destinationArn)
                .build()).taskHandle();
    }

    public void purgeDlq(String dlqName) {
        validateName(dlqName);
        if (!dlqName.endsWith("-dlq")) {
            throw new IllegalArgumentException("Only a DLQ can be purged by this operation");
        }
        sqsClient.purgeQueue(PurgeQueueRequest.builder()
                .queueUrl(queueUrl(dlqName))
                .build());
    }

    private void allowTopicToSendToQueue(String topicArn, String queueUrl, String queueArn) {
        Map<String, Object> statement = Map.of(
                "Effect", "Allow",
                "Principal", Map.of("Service", "sns.amazonaws.com"),
                "Action", "sqs:SendMessage",
                "Resource", queueArn,
                "Condition", Map.of("ArnEquals", Map.of("aws:SourceArn", topicArn)));
        sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(Map.of(
                        QueueAttributeName.POLICY,
                        json(Map.of(
                                "Version", "2012-10-17",
                                "Statement", List.of(statement)))))
                .build());
    }

    private String queueArn(String queueUrl) {
        return sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build()).attributesAsStrings().get("QueueArn");
    }

    private String queueUrl(String queueName) {
        return sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build()).queueUrl();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize AWS messaging configuration", exception);
        }
    }

    private void validateName(String name) {
        if (name == null || !RESOURCE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Resource names may contain only letters, digits, '_' and '-'");
        }
    }

    private void validateConsumerName(String name) {
        if (!List.of("orders", "inventory", "notifications").contains(name)) {
            throw new IllegalArgumentException("Unknown consumer: " + name);
        }
    }

    private void requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
