package com.techstore.order.kafka;

import com.techstore.kafka.order.ProductCheckRequested;
import com.techstore.kafka.order.SubmitPaymentRequested;
import com.techstore.order.dto.CancelReservationRequest;
import com.techstore.order.dto.SubmitPaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class SubmitPaymentProducer {
    private static final String TOPIC = "payment-submit";
    private final KafkaTemplate<String, SubmitPaymentRequested> kafkaTemplate;

    public CompletableFuture<SendResult<String, SubmitPaymentRequested>> publish(SubmitPaymentRequested request) {

        return kafkaTemplate.send(
                TOPIC,
                request.getOrderId().toString(),
                request
        );
    }
}
