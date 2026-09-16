package com.techstore.inventory.kafka;

import com.techstore.inventory.dto.InventoryReservationResponse;
import com.techstore.kafka.inventory.ConfirmReservationCompleted;
import com.techstore.kafka.inventory.InventoryReservationCompleted;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ReservationCreatedProducer {
    private static final String TOPIC = "reservation-created";
    private final KafkaTemplate<String, InventoryReservationCompleted> kafkaTemplate;

    public CompletableFuture<SendResult<String, InventoryReservationCompleted>> publish(InventoryReservationCompleted response) {
        System.out.println("hello resevervation created from inventory consumer");

        return kafkaTemplate.send(
                TOPIC,
                response.getOrderId().toString(),
                response
        );
    }
}
