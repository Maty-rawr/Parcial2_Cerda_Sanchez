package cl.duoc.backendskatesapp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.backendskatesapp.model.ProcessedMessage;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {
    boolean existsByConsumerNameAndEventId(String consumerName, String eventId);

    List<ProcessedMessage> findTop50ByOrderByProcessedAtDesc();
}
