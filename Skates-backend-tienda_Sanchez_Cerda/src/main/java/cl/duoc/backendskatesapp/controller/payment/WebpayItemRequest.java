package cl.duoc.backendskatesapp.controller.payment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WebpayItemRequest(
        @NotNull(message = "El producto es obligatorio") Long skateId,
        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser mayor que cero") Integer quantity) {
}
