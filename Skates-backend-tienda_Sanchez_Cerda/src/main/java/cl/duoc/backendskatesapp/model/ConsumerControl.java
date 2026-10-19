package cl.duoc.backendskatesapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "consumer_controls")
public class ConsumerControl {

    @Id
    @Column(length = 40)
    private String consumerName;

    @Column(nullable = false)
    private boolean paused;

    protected ConsumerControl() {
    }

    public ConsumerControl(String consumerName, boolean paused) {
        this.consumerName = consumerName;
        this.paused = paused;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
