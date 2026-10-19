package cl.duoc.backendskatesapp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("worker")
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
@EnableScheduling
public class MessagingWorkerConfiguration {
}
