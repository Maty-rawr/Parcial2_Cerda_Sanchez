package cl.duoc.backendskatesapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.backendskatesapp.model.ProcessedMessage;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {
    boolean existsByConsumerNameAndEventId(String consumerName, String eventId);
}
