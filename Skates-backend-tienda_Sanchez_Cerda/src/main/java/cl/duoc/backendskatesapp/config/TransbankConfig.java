package cl.duoc.backendskatesapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cl.transbank.common.IntegrationType;
import cl.transbank.webpay.common.WebpayOptions;
import cl.transbank.webpay.webpayplus.WebpayPlus;

@Configuration
public class TransbankConfig {

    @Bean
    WebpayPlus.Transaction webpayPlusTransaction(
            @Value("${transbank.environment:TEST}") String environment,
            @Value("${transbank.commerce-code:597055555532}") String commerceCode,
            @Value("${transbank.api-key-secret:579B532A7440BB0C9079DED94D31EA1615BACEB56610332264630D42D0A36B1C}") String apiKeySecret) {
        IntegrationType integrationType = IntegrationType.valueOf(environment.trim().toUpperCase());
        return new WebpayPlus.Transaction(new WebpayOptions(commerceCode, apiKeySecret, integrationType));
    }
}
