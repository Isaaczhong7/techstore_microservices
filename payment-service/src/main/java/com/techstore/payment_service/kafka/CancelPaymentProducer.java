package com.techstore.payment_service.kafka;

import com.techstore.kafka.order.CancelReservationRequested;
import com.techstore.kafka.payment.CancelPaymentCompleted;
import com.techstore.payment_service.dto.CancelPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor

public class CancelPaymentProducer {
    private static final String TOPIC = "payment-cancelled";
    private final KafkaTemplate<String, CancelPaymentCompleted> kafkaTemplate;

        public CompletableFuture<SendResult<String, CancelPaymentCompleted>> publish(CancelPaymentCompleted response) {

        return kafkaTemplate.send(
                TOPIC,
                response.getOrderId().toString(),
                response
        );
    }
}
