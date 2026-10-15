package cl.duoc.backendskatesapp.service;

public class PaymentIntegrationException extends RuntimeException {

    public PaymentIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentIntegrationException(String message) {
        super(message);
    }
}
