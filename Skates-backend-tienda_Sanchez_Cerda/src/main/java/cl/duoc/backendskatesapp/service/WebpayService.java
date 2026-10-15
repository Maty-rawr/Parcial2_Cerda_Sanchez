package cl.duoc.backendskatesapp.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cl.duoc.backendskatesapp.model.PaymentTransaction;
import cl.duoc.backendskatesapp.model.PaymentTransactionItem;
import cl.duoc.backendskatesapp.controller.payment.WebpayItemRequest;
import cl.duoc.backendskatesapp.controller.payment.WebpayPaymentResponse;
import cl.duoc.backendskatesapp.model.Skate;
import cl.duoc.backendskatesapp.repository.SkateRepository;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCommitResponse;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionStatusResponse;
import cl.transbank.common.IntegrationType;
import cl.transbank.webpay.common.WebpayOptions;

import cl.duoc.backendskatesapp.controller.payment.WebpayCreateResponse;
import cl.duoc.backendskatesapp.repository.PaymentTransactionRepository;

@Service
public class WebpayService {

    private final WebpayPlus.Transaction transaction;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SkateRepository skateRepository;
    private final RestClient restClient;
    private final String commerceCode;
    private final String apiKeySecret;
    private final String createUrl;
    private final String returnUrl;

    private static final Logger logger = LoggerFactory.getLogger(WebpayService.class);

    // 1. Constructor principal para Spring Boot (Apunta la returnUrl por defecto al frontend)
    @Autowired
    public WebpayService(
            PaymentTransactionRepository paymentTransactionRepository,
            SkateRepository skateRepository,
            @Value("${transbank.commerce-code:597055555532}") String commerceCode,
            @Value("${transbank.api-key-secret:579B532A7440BB0C9079DED94D31EA1615BACEB56610332264630D42D0A36B1C}") String apiKeySecret,
            @Value("${transbank.create-url:https://webpay3gint.transbank.cl/rswebpaytransaction/api/webpay/v1.2/transactions}") String createUrl,
            @Value("${transbank.return-url:http://localhost:5173/}") String returnUrl) {
        
        this.transaction = new WebpayPlus.Transaction(
            new WebpayOptions(commerceCode, apiKeySecret, IntegrationType.TEST)
        );
        
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.skateRepository = skateRepository;
        this.restClient = RestClient.create();
        this.commerceCode = commerceCode;
        this.apiKeySecret = apiKeySecret;
        this.createUrl = createUrl;
        this.returnUrl = returnUrl;
    }

    // 2. Constructor secundario para pruebas unitarias (Mocks)
    public WebpayService(
            WebpayPlus.Transaction transaction,
            PaymentTransactionRepository paymentTransactionRepository,
            SkateRepository skateRepository,
            String returnUrl) {
        this.transaction = transaction;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.skateRepository = skateRepository;
        this.restClient = RestClient.create();
        this.commerceCode = "597055555532";
        this.apiKeySecret = "579B532A7440BB0C9079DED94D31EA1615BACEB56610332264630D42D0A36B1C";
        this.createUrl = "https://webpay3gint.transbank.cl/rswebpaytransaction/api/webpay/v1.2/transactions";
        this.returnUrl = returnUrl;
    }

    @Transactional
    public WebpayCreateResponse create(BigDecimal amount, String requestedSessionId, String username,
            List<WebpayItemRequest> requestedItems) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }

        String buyOrder = generateBuyOrder();
        String sessionId = requestedSessionId == null || requestedSessionId.isBlank()
                ? "session-" + UUID.randomUUID()
                : requestedSessionId;

        requestedItems.forEach(item -> skateRepository.findById(item.skateId())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + item.skateId())));

        try {
            Map<String, Object> requestBody = Map.of(
                    "buy_order", buyOrder,
                    "session_id", sessionId,
                    "amount", amount,
                    "return_url", returnUrl);
            
            TransbankCreateResponse response = restClient.post()
                    .uri(createUrl)
                    .header("Tbk-Api-Key-Id", commerceCode)
                    .header("Tbk-Api-Key-Secret", apiKeySecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange((request, clientResponse) -> {
                        int status = clientResponse.getStatusCode().value();
                        logger.info("Transbank create URL={} status={}", createUrl, status);
                        if (status >= 400) {
                            throw new RuntimeException(
                                    "Transbank respondió HTTP " + status + " en " + createUrl);
                        }
                        return clientResponse.bodyTo(TransbankCreateResponse.class);
                    });
            
            PaymentTransaction paymentTransaction = new PaymentTransaction(
                    buyOrder,
                    sessionId,
                    response.token(),
                    amount,
                    "INITIALIZED",
                    username);
            
            requestedItems.forEach(item -> paymentTransaction.addItem(
                    new PaymentTransactionItem(item.skateId(), item.quantity())));
            
            paymentTransactionRepository.save(paymentTransaction);
            
            return new WebpayCreateResponse(
                    response.token(),
                    response.url());
        } catch (Exception ex) {
            throw new RuntimeException("No fue posible crear la transacción en Transbank", ex);
        }
    }

    @Transactional
    public WebpayPaymentResponse commit(String token) {
        requireToken(token);
        PaymentTransaction paymentTransaction = paymentTransactionRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Transacción no encontrada: " + token));

        if ("COMPLETED".equals(paymentTransaction.getStatus())
                || "REJECTED".equals(paymentTransaction.getStatus())
                || "STOCK_REVIEW".equals(paymentTransaction.getStatus())) {
            return responseFor(paymentTransaction);
        }

        try {
            WebpayPlusTransactionCommitResponse response = transaction.commit(token);
            validateResponse(paymentTransaction, response);
            
            Optional<PaymentTransaction> existingOrder = paymentTransactionRepository
                    .findByBuyOrder(response.getBuyOrder());
            if (existingOrder.isPresent() && existingOrder.get() != paymentTransaction
                    && "COMPLETED".equals(existingOrder.get().getStatus())) {
                return responseFor(existingOrder.get());
            }

            paymentTransaction.setAuthorizationCode(response.getAuthorizationCode());
            if (!"AUTHORIZED".equals(response.getStatus()) || response.getResponseCode() != 0) {
                paymentTransaction.setStatus("REJECTED");
                paymentTransaction.setFailureReason("Transbank rechazó la transacción");
                paymentTransactionRepository.save(paymentTransaction);
                return responseFor(paymentTransaction);
            }

            String stockFailure = decrementStock(paymentTransaction);
            if (stockFailure != null) {
                paymentTransaction.setStatus("STOCK_REVIEW");
                paymentTransaction.setFailureReason(stockFailure);
                paymentTransactionRepository.save(paymentTransaction);
                return responseFor(paymentTransaction);
            }

            paymentTransaction.setStatus("COMPLETED");
            paymentTransaction.setCompletedAt(Instant.now());
            paymentTransactionRepository.save(paymentTransaction);
            return responseFor(paymentTransaction);
        } catch (Exception ex) {
            throw new RuntimeException("No fue posible confirmar la transacción en Transbank", ex);
        }
    }

    private String decrementStock(PaymentTransaction paymentTransaction) {
        Map<Long, Integer> quantitiesBySkate = new TreeMap<>();
        for (PaymentTransactionItem item : paymentTransaction.getItems()) {
            quantitiesBySkate.merge(item.getSkateId(), item.getQuantity(), Integer::sum);
        }

        for (Map.Entry<Long, Integer> entry : quantitiesBySkate.entrySet()) {
            Skate skate = skateRepository.findByIdForUpdate(entry.getKey()).orElse(null);
            if (skate == null) {
                return "El producto " + entry.getKey() + " ya no existe";
            }
            if (skate.getStock() < entry.getValue()) {
                return "Stock insuficiente para el producto " + entry.getKey();
            }
        }

        for (Map.Entry<Long, Integer> entry : quantitiesBySkate.entrySet()) {
            Skate skate = skateRepository.findByIdForUpdate(entry.getKey()).orElseThrow();
            skate.setStock(skate.getStock() - entry.getValue());
            skateRepository.save(skate);
        }
        return null;
    }

    private WebpayPaymentResponse responseFor(PaymentTransaction paymentTransaction) {
        boolean completed = "COMPLETED".equals(paymentTransaction.getStatus());
        return new WebpayPaymentResponse(
                paymentTransaction.getBuyOrder(),
                paymentTransaction.getToken(),
                paymentTransaction.getStatus(),
                completed,
                completed,
                paymentTransaction.getAuthorizationCode(),
                completed ? "Compra autorizada y stock actualizado"
                        : paymentTransaction.getFailureReason());
    }

    public WebpayPlusTransactionStatusResponse status(String token) {
        requireToken(token);
        PaymentTransaction paymentTransaction = paymentTransactionRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Transacción no encontrada: " + token));
        try {
            WebpayPlusTransactionStatusResponse response = transaction.status(token);
            validateResponse(paymentTransaction, response);
            return response;
        } catch (Exception ex) {
            throw new RuntimeException("No fue posible consultar la transacción en Transbank", ex);
        }
    }

    private void validateResponse(PaymentTransaction paymentTransaction, WebpayPlusTransactionStatusResponse response) {
        boolean sameOrder = paymentTransaction.getBuyOrder().equals(response.getBuyOrder());
        boolean sameAmount = paymentTransaction.getAmount().compareTo(BigDecimal.valueOf(response.getAmount())) == 0;
        if (!sameOrder || !sameAmount) {
            throw new RuntimeException(
                    "La respuesta de Transbank no coincide con la orden o el monto solicitado");
        }
    }

    private void requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token_ws es obligatorio");
        }
    }

    private record TransbankCreateResponse(String token, String url) {
    }

    private String generateBuyOrder() {
        return "SK" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}