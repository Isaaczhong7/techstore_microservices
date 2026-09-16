package com.techstore.inventory.kafka;

import com.techstore.inventory.dto.CancelReservationResponse;
import com.techstore.kafka.inventory.CancelReservationCompleted;
import com.techstore.kafka.order.CancelReservationRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Service
public class ReservationCancelledProducer {
    private static final String TOPIC = "reservation-cancelled";
    private final KafkaTemplate<String, CancelReservationCompleted> kafkaTemplate;

    public CompletableFuture<SendResult<String, CancelReservationCompleted>> publish(CancelReservationCompleted response) {

        return kafkaTemplate.send(
                TOPIC,
                response.getOrderId().toString(),
                response
        );
    }
}
