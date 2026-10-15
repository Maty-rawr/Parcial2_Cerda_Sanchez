package cl.duoc.backendskatesapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.backendskatesapp.model.PaymentTransaction;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByToken(String token);

    Optional<PaymentTransaction> findByBuyOrder(String buyOrder);
}
