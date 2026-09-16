package com.techstore.order.kafka;

import com.techstore.kafka.order.ConfirmReservationRequested;
import com.techstore.kafka.order.CreatePaymentRequested;
import com.techstore.order.dto.CreatePaymentRequest;
import com.techstore.order.dto.CreatePaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class CreatePaymentProducer {
    private static final String TOPIC = "payment-create";
    private final KafkaTemplate<String, CreatePaymentRequested> kafkaTemplate;

    public CompletableFuture<SendResult<String, CreatePaymentRequested>> publish(CreatePaymentRequested request) {
        System.out.println("hello from order side payment producer");


        return kafkaTemplate.send(
                TOPIC,
                request.getOrderId().toString(),
                request
        );
    }
}
