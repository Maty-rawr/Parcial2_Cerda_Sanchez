package cl.duoc.backendskatesapp.messaging.notifications;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cl.duoc.backendskatesapp.config.MessagingProperties;
import cl.duoc.backendskatesapp.messaging.MessagingNames;
import cl.duoc.backendskatesapp.messaging.QueueMessagePollingService;

@Component
@Profile("worker")
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class NotificationsQueueConsumer {

    private final MessagingProperties properties;
    private final QueueMessagePollingService pollingService;

    public NotificationsQueueConsumer(MessagingProperties properties, QueueMessagePollingService pollingService) {
        this.properties = properties;
        this.pollingService = pollingService;
    }

    @Scheduled(fixedDelayString = "${app.messaging.poll-interval-ms:1000}")
    public void poll() {
        MessagingProperties.Queue queue = properties.getQueues().getNotifications();
        pollingService.poll(MessagingNames.NOTIFICATIONS_CONSUMER, queue.getUrl(), queue.getDlqUrl());
    }
}
