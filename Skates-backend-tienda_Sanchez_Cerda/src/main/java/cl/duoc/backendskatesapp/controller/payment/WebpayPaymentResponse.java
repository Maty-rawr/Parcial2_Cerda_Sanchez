package cl.duoc.backendskatesapp.controller.payment;

public record WebpayPaymentResponse(
        String buyOrder,
        String token,
        String paymentStatus,
        boolean completed,
        boolean stockUpdated,
        String authorizationCode,
        String message) {
}
