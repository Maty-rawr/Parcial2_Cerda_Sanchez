package cl.duoc.backendskatesapp.service;

public class PaymentTransactionNotFoundException extends RuntimeException {

    public PaymentTransactionNotFoundException(String token) {
        super("No existe una transacción local para el token " + token);
    }
}
