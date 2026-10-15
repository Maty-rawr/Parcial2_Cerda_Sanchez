package cl.duoc.backendskatesapp.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cl.duoc.backendskatesapp.service.SkateNoEncontradoException;
import cl.duoc.backendskatesapp.service.PaymentIntegrationException;
import cl.duoc.backendskatesapp.service.PaymentTransactionNotFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SkateNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> manejarNoEncontrada(SkateNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cuerpoError(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarValidacion(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpoError(detalle));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpoError(ex.getMessage()));
    }

    @ExceptionHandler(PaymentIntegrationException.class)
    public ResponseEntity<Map<String, Object>> manejarErrorDePago(PaymentIntegrationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(cuerpoError(ex.getMessage()));
    }

    @ExceptionHandler(PaymentTransactionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> manejarTransaccionNoEncontrada(PaymentTransactionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cuerpoError(ex.getMessage()));
    }

    private Map<String, Object> cuerpoError(String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("mensaje", mensaje);
        return cuerpo;
    }
}