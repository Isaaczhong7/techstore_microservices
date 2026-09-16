package com.techstore.inventory.kafka;

import com.techstore.inventory.dto.ConfirmReservationResponse;
import com.techstore.kafka.inventory.CancelReservationCompleted;
import com.techstore.kafka.inventory.ConfirmReservationCompleted;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Service
public class ReservationConfirmedProducer {
    private static final String TOPIC = "reservation-confirmed";
    private final KafkaTemplate<String, ConfirmReservationCompleted> kafkaTemplate;

    public CompletableFuture<SendResult<String, ConfirmReservationCompleted>> publish(ConfirmReservationCompleted response) {

        return kafkaTemplate.send(
                TOPIC,
                response.getOrderId().toString(),
                response
        );
    }
}
