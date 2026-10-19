package cl.duoc.backendskatesapp.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.backendskatesapp.model.ConsumerControl;
import cl.duoc.backendskatesapp.service.AwsMessagingAdminService;

@RestController
@RequestMapping("/api/admin/messaging")
@Profile({"all", "admin"})
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
public class MessagingAdminController {

    private final AwsMessagingAdminService adminService;

    public MessagingAdminController(AwsMessagingAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/queues")
    public List<String> listQueues() {
        return adminService.listQueues();
    }

    @PostMapping("/queues")
    public Map<String, String> createQueue(@Valid @RequestBody CreateQueueRequest request) {
        String queueUrl = adminService.createQueue(
                request.name(),
                request.visibilityTimeoutSeconds(),
                request.retentionSeconds());
        return Map.of("queueUrl", queueUrl);
    }

    @DeleteMapping("/queues/{name}")
    public void deleteQueue(@PathVariable String name) {
        adminService.deleteQueue(name);
    }

    @GetMapping("/queues/{name}/status")
    public Map<String, String> queueStatus(@PathVariable String name) {
        return adminService.queueStatus(name);
    }

    @GetMapping("/topics")
    public List<String> listTopics() {
        return adminService.listTopics();
    }

    @PostMapping("/topics")
    public Map<String, String> createTopic(@Valid @RequestBody CreateTopicRequest request) {
        return Map.of("topicArn", adminService.createTopic(request.name()));
    }

    @DeleteMapping("/topics")
    public void deleteTopic(@RequestParam String topicArn) {
        adminService.deleteTopic(topicArn);
    }

    @PostMapping("/subscriptions")
    public Map<String, String> subscribe(@Valid @RequestBody SubscribeRequest request) {
        return Map.of(
                "subscriptionArn",
                adminService.subscribe(request.topicArn(), request.queueUrl(), request.filterPolicy()));
    }

    @GetMapping("/subscriptions")
    public List<String> listSubscriptions(@RequestParam String topicArn) {
        return adminService.listSubscriptions(topicArn);
    }

    @DeleteMapping("/subscriptions")
    public void deleteSubscription(@RequestParam String subscriptionArn) {
        adminService.deleteSubscription(subscriptionArn);
    }

    @PostMapping("/consumers/{name}/pause")
    public ConsumerControl pauseConsumer(@PathVariable String name) {
        return adminService.setConsumerPaused(name, true);
    }

    @PostMapping("/consumers/{name}/resume")
    public ConsumerControl resumeConsumer(@PathVariable String name) {
        return adminService.setConsumerPaused(name, false);
    }

    @PostMapping("/dlq/{name}/redrive")
    public Map<String, String> redriveDlq(
            @PathVariable String name,
            @RequestParam String destinationQueueName) {
        return Map.of("taskHandle", adminService.redriveDlq(name, destinationQueueName));
    }

    @DeleteMapping("/dlq/{name}")
    public void purgeDlq(@PathVariable String name) {
        adminService.purgeDlq(name);
    }

    public record CreateQueueRequest(
            @NotBlank String name,
            @Min(1) @Max(43200) int visibilityTimeoutSeconds,
            @Min(60) @Max(1209600) int retentionSeconds) {
    }

    public record CreateTopicRequest(@NotBlank String name) {
    }

    public record SubscribeRequest(
            @NotBlank String topicArn,
            @NotBlank String queueUrl,
            Map<String, Object> filterPolicy) {
    }
}
