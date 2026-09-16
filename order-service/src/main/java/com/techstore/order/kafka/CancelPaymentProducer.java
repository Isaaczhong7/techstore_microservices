package com.techstore.order.kafka;

import com.techstore.kafka.order.CancelPaymentRequested;
import com.techstore.kafka.order.CancelReservationRequested;
import com.techstore.order.dto.CancelPaymentRequest;
import com.techstore.order.dto.SubmitPaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor

public class CancelPaymentProducer {
    private static final String TOPIC = "payment-cancel";
    private final KafkaTemplate<String, CancelPaymentRequested> kafkaTemplate;

        public CompletableFuture<SendResult<String, CancelPaymentRequested>> publish(CancelPaymentRequested request) {

        return kafkaTemplate.send(
                TOPIC,
                request.getOrderId().toString(),
                request
        );
    }
}
