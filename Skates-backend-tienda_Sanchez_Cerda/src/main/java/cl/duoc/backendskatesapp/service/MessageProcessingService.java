package cl.duoc.backendskatesapp.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.backendskatesapp.messaging.MessagingDomainEvent;
import cl.duoc.backendskatesapp.messaging.MessagingNames;
import cl.duoc.backendskatesapp.model.ProcessedMessage;
import cl.duoc.backendskatesapp.repository.ProcessedMessageRepository;

@Service
public class MessageProcessingService {

    private final ProcessedMessageRepository repository;
    private final ObjectMapper objectMapper;

    public MessageProcessingService(ProcessedMessageRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean process(String consumerName, MessagingDomainEvent event) {
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new NonRecoverableMessageException("Event is missing eventId");
        }
        if (!isSupported(consumerName, event.eventType())) {
            throw new NonRecoverableMessageException(
                    "Event type " + event.eventType() + " is not supported by " + consumerName);
        }
        if (repository.existsByConsumerNameAndEventId(consumerName, event.eventId())) {
            return false;
        }

        try {
            repository.saveAndFlush(new ProcessedMessage(
                    consumerName,
                    event.eventId(),
                    event.eventType(),
                    objectMapper.writeValueAsString(event.payload())));
        } catch (JacksonException exception) {
            throw new NonRecoverableMessageException("Event payload cannot be serialized");
        }
        return true;
    }

    private boolean isSupported(String consumerName, String eventType) {
        return switch (consumerName) {
            case MessagingNames.ORDERS_CONSUMER -> MessagingNames.PAYMENT_COMPLETED.equals(eventType)
                    || MessagingNames.PAYMENT_REJECTED.equals(eventType);
            case MessagingNames.INVENTORY_CONSUMER -> MessagingNames.INVENTORY_UPDATED.equals(eventType);
            case MessagingNames.NOTIFICATIONS_CONSUMER -> MessagingNames.PAYMENT_COMPLETED.equals(eventType)
                    || MessagingNames.PAYMENT_REJECTED.equals(eventType)
                    || MessagingNames.INVENTORY_UPDATED.equals(eventType);
            default -> false;
        };
    }
}
