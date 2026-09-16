package com.techstore.order.kafka;

import com.techstore.kafka.order.CancelPaymentRequested;
import com.techstore.kafka.order.ConfirmReservationRequested;
import com.techstore.order.dto.ConfirmReservationRequest;
import com.techstore.order.dto.CreateReservationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ConfirmOrderProducer {
    private static final String TOPIC = "order-confirm";
    private final KafkaTemplate<String, ConfirmReservationRequested> kafkaTemplate;

    public CompletableFuture<SendResult<String, ConfirmReservationRequested>> publish(ConfirmReservationRequested request) {

        return kafkaTemplate.send(
                TOPIC,
                request.getReservationId().toString(),
                request
        );
    }
}
