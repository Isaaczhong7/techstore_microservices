package com.techstore.payment_service.kafka;

import com.techstore.kafka.payment.CreatePaymentCompleted;
import com.techstore.kafka.payment.SubmitPaymentCompleted;
import com.techstore.payment_service.dto.SubmitPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class SubmitPaymentProducer {
    private static final String TOPIC = "payment-submitted";
    private final KafkaTemplate<String, SubmitPaymentCompleted> kafkaTemplate;

    public CompletableFuture<SendResult<String, SubmitPaymentCompleted>> publish(SubmitPaymentCompleted response) {
        System.out.println("sending it in payment services");

        return kafkaTemplate.send(
                TOPIC,
                response.getOrderId().toString(),
                response
        );
    }
}
