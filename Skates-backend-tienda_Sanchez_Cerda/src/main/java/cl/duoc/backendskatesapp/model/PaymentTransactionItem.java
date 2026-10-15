package cl.duoc.backendskatesapp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_transaction_items")
public class PaymentTransactionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long skateId;
    private Integer quantity;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_transaction_id", nullable = false)
    private PaymentTransaction paymentTransaction;

    protected PaymentTransactionItem() {
    }

    public PaymentTransactionItem(Long skateId, Integer quantity) {
        this.skateId = skateId;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public Long getSkateId() { return skateId; }
    public Integer getQuantity() { return quantity; }
    public PaymentTransaction getPaymentTransaction() { return paymentTransaction; }
    public void setPaymentTransaction(PaymentTransaction paymentTransaction) { this.paymentTransaction = paymentTransaction; }
}
