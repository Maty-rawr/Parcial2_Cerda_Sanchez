package cl.duoc.backendskatesapp.controller.payment;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record WebpayCreateRequest(
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "1.0", message = "El monto debe ser mayor que cero")
        BigDecimal amount,
        @Size(max = 61, message = "El sessionId no puede superar 61 caracteres")
        String sessionId,
        @NotEmpty(message = "La compra debe contener al menos un producto")
        List<@Valid WebpayItemRequest> items) {
}
