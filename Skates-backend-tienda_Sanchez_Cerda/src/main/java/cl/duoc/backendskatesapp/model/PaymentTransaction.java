package cl.duoc.backendskatesapp.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 26)
    private String buyOrder;
    private String sessionId;
    @Column(nullable = false, unique = true, length = 64)
    private String token;
    private BigDecimal amount;
    private String status;
    private String username;
    private String authorizationCode;
    private Instant createdAt;
    private Instant completedAt;
    private String failureReason;

    @OneToMany(mappedBy = "paymentTransaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentTransactionItem> items = new ArrayList<>();

    protected PaymentTransaction() {
    }

        public PaymentTransaction(String buyOrder, String sessionId, String token, BigDecimal amount, String status,
            String username) {
        this.buyOrder = buyOrder;
        this.sessionId = sessionId;
        this.token = token;
        this.amount = amount;
        this.status = status;
        this.username = username;
        this.createdAt = Instant.now();
    }

    public void addItem(PaymentTransactionItem item) {
        item.setPaymentTransaction(this);
        items.add(item);
    }

    public Long getId() { return id; }
    public String getBuyOrder() { return buyOrder; }
    public String getSessionId() { return sessionId; }
    public String getToken() { return token; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getUsername() { return username; }
    public String getAuthorizationCode() { return authorizationCode; }
    public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public List<PaymentTransactionItem> getItems() { return items; }
}
