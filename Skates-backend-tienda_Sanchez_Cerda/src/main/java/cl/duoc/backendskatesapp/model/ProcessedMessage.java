package cl.duoc.backendskatesapp.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "processed_messages", uniqueConstraints = @UniqueConstraint(
        name = "uk_processed_message_consumer_event",
        columnNames = {"consumer_name", "event_id"}))
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumer_name", nullable = false, length = 40)
    private String consumerName;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Lob
    @Column(name = "event_payload", nullable = false)
    private String eventPayload;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedMessage() {
    }

    public ProcessedMessage(String consumerName, String eventId, String eventType, String eventPayload) {
        this.consumerName = consumerName;
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventPayload = eventPayload;
        this.processedAt = Instant.now();
    }

    public String getConsumerName() {
        return consumerName;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
