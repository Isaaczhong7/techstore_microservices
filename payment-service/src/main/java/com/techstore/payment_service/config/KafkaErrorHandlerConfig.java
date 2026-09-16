package com.techstore.payment_service.config;

import com.techstore.payment_service.exception.PaymentIdNotFoundException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlerConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) ->
                                new TopicPartition(
                                        record.topic() + ".DLT",
                                        record.partition()
                                )
                );


        // 1 second delay, 3 retries
        FixedBackOff fixedBackOff =
                new FixedBackOff(1000L, 3L);

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        recoverer,
                        fixedBackOff
                );

        // Go directly to DLT - don't retry
        errorHandler.addNotRetryableExceptions(
                PaymentIdNotFoundException.class
        );

        return errorHandler;
    }
}
