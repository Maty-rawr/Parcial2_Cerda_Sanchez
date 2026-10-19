package cl.duoc.backendskatesapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class AwsMessagingConfiguration {

    @Bean
    SnsClient snsClient(@Value("${app.aws.region}") String region) {
        return SnsClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    SqsClient sqsClient(@Value("${app.aws.region}") String region) {
        return SqsClient.builder()
                .region(Region.of(region))
                .build();
    }
}
