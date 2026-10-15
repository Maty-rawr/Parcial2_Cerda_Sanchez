package cl.duoc.backend_skates_app; // Paquete corregido para coincidir con tu carpeta

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

// IMPORTANTE: Importamos tu verdadero servicio desde su paquete real
import cl.duoc.backendskatesapp.service.WebpayService;
import cl.duoc.backendskatesapp.controller.payment.WebpayPaymentResponse;
import cl.duoc.backendskatesapp.model.PaymentTransaction;
import cl.duoc.backendskatesapp.model.PaymentTransactionItem;
import cl.duoc.backendskatesapp.model.Skate;
import cl.duoc.backendskatesapp.repository.PaymentTransactionRepository;
import cl.duoc.backendskatesapp.repository.SkateRepository;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCommitResponse;

class WebpayServiceTest {

    @Mock
    private WebpayPlus.Transaction transbankTransaction;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private SkateRepository skateRepository;

    private WebpayService webpayService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Este constructor ahora coincidirá perfectamente con el segundo constructor de tu WebpayService real
        webpayService = new WebpayService(
                transbankTransaction,
                paymentTransactionRepository,
                skateRepository,
                "http://localhost:8080/api/payments/webpay/return"
        );
    }

    @Test
    void pagoAutorizadoDescuentaStockYCompletaLaOrden() throws Exception {
        PaymentTransaction payment = payment("AUTHORIZED-ORDER", "token-1", 10000, "user");
        payment.addItem(new PaymentTransactionItem(1L, 2));
        Skate skate = skate(1L, 5);
        WebpayPlusTransactionCommitResponse response = transbankResponse("AUTHORIZED-ORDER", 10000.0, "AUTHORIZED", (byte) 0);

        when(paymentTransactionRepository.findByToken("token-1")).thenReturn(Optional.of(payment));
        when(transbankTransaction.commit("token-1")).thenReturn(response);
        when(skateRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(skate));

        WebpayPaymentResponse result = webpayService.commit("token-1");

        assertEquals("COMPLETED", result.paymentStatus());
        assertTrue(result.completed());
        assertTrue(result.stockUpdated());
        assertEquals(3, skate.getStock());
        verify(skateRepository).save(skate);
    }

    @Test
    void pagoRechazadoNoDescuentaStock() throws Exception {
        PaymentTransaction payment = payment("REJECTED-ORDER", "token-2", 10000, "user");
        payment.addItem(new PaymentTransactionItem(1L, 1));
        WebpayPlusTransactionCommitResponse response = transbankResponse("REJECTED-ORDER", 10000.0, "FAILED", (byte) 5);

        when(paymentTransactionRepository.findByToken("token-2")).thenReturn(Optional.of(payment));
        when(transbankTransaction.commit("token-2")).thenReturn(response);

        WebpayPaymentResponse result = webpayService.commit("token-2");

        assertEquals("REJECTED", result.paymentStatus());
        assertFalse(result.completed());
        verify(skateRepository, never()).findByIdForUpdate(1L);
    }

    @Test
    void stockInsuficienteNoDescuentaNingunProducto() throws Exception {
        PaymentTransaction payment = payment("LOW-STOCK-ORDER", "token-3", 10000, "user");
        payment.addItem(new PaymentTransactionItem(1L, 2));
        payment.addItem(new PaymentTransactionItem(2L, 1));
        Skate first = skate(1L, 5);
        Skate second = skate(2L, 0);
        WebpayPlusTransactionCommitResponse response = transbankResponse("LOW-STOCK-ORDER", 10000.0, "AUTHORIZED", (byte) 0);

        when(paymentTransactionRepository.findByToken("token-3")).thenReturn(Optional.of(payment));
        when(transbankTransaction.commit("token-3")).thenReturn(response);
        when(skateRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(first));
        when(skateRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(second));

        WebpayPaymentResponse result = webpayService.commit("token-3");

        assertEquals("STOCK_REVIEW", result.paymentStatus());
        assertFalse(result.completed());
        assertEquals(5, first.getStock());
        assertEquals(0, second.getStock());
        verify(skateRepository, never()).save(first);
        verify(skateRepository, never()).save(second);
    }

    @Test
    void confirmacionDuplicadaNoVuelveADescontarStock() throws Exception {
        PaymentTransaction payment = payment("DUPLICATE-ORDER", "token-4", 10000, "user");
        payment.addItem(new PaymentTransactionItem(1L, 1));
        Skate skate = skate(1L, 2);
        WebpayPlusTransactionCommitResponse response = transbankResponse("DUPLICATE-ORDER", 10000.0, "AUTHORIZED", (byte) 0);

        when(paymentTransactionRepository.findByToken("token-4")).thenReturn(Optional.of(payment));
        when(transbankTransaction.commit("token-4")).thenReturn(response);
        when(skateRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(skate));

        webpayService.commit("token-4");
        WebpayPaymentResponse duplicate = webpayService.commit("token-4");

        assertEquals("COMPLETED", duplicate.paymentStatus());
        assertEquals(1, skate.getStock());
        verify(transbankTransaction, times(1)).commit("token-4");
        verify(skateRepository, times(1)).save(skate);
    }

    private PaymentTransaction payment(String buyOrder, String token, int amount, String username) {
        return new PaymentTransaction(buyOrder, "session", token, BigDecimal.valueOf(amount), "INITIALIZED", username);
    }

    private Skate skate(Long id, int stock) {
        return new Skate(id, "Street", "DC", 8.0, null, stock);
    }

    private WebpayPlusTransactionCommitResponse transbankResponse(
            String buyOrder, double amount, String status, byte responseCode) {
        WebpayPlusTransactionCommitResponse response = new WebpayPlusTransactionCommitResponse();
        response.setBuyOrder(buyOrder);
        response.setAmount(amount);
        response.setStatus(status);
        response.setResponseCode(responseCode);
        response.setAuthorizationCode("AUTH-1");
        return response;
    }
}