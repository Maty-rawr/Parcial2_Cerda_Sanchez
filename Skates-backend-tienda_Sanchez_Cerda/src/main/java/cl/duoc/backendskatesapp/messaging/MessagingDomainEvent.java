package cl.duoc.backendskatesapp.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MessagingDomainEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        Map<String, Object> payload) {

    public static MessagingDomainEvent create(String eventType, Map<String, Object> payload) {
        return new MessagingDomainEvent(UUID.randomUUID().toString(), eventType, Instant.now(), payload);
    }
}
