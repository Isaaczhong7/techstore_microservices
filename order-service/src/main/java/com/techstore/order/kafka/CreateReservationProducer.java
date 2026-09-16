package com.techstore.order.kafka;

import com.techstore.kafka.order.CreatePaymentRequested;
import com.techstore.kafka.order.CreateReservationRequested;
import com.techstore.order.dto.CreateReservationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class CreateReservationProducer {

    private static final String TOPIC = "order-create";
    private final KafkaTemplate<String, CreateReservationRequested> kafkaTemplate;

    public CompletableFuture<SendResult<String, CreateReservationRequested>> publish(CreateReservationRequested request) {

        return kafkaTemplate.send(
                TOPIC,
                request.getOrderId().toString(),
                request
        );
    }
}
