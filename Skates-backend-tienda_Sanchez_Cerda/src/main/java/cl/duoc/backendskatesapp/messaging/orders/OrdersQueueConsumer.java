package cl.duoc.backendskatesapp.messaging.orders;

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
public class OrdersQueueConsumer {

    private final MessagingProperties properties;
    private final QueueMessagePollingService pollingService;

    public OrdersQueueConsumer(MessagingProperties properties, QueueMessagePollingService pollingService) {
        this.properties = properties;
        this.pollingService = pollingService;
    }

    @Scheduled(fixedDelayString = "${app.messaging.poll-interval-ms:1000}")
    public void poll() {
        MessagingProperties.Queue queue = properties.getQueues().getOrders();
        pollingService.poll(MessagingNames.ORDERS_CONSUMER, queue.getUrl(), queue.getDlqUrl());
    }
}
