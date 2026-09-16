package com.techstore.inventory.kafka;

import com.techstore.kafka.inventory.InventoryItemCompleted;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;


@Service
@RequiredArgsConstructor
public class InventoryItemProducer {
    private static final String TOPIC = "inventory-item-update";
    private final KafkaTemplate<String, InventoryItemCompleted> kafkaTemplate;

    public CompletableFuture<SendResult<String, InventoryItemCompleted>> publish(InventoryItemCompleted response) {

        return kafkaTemplate.send(
                TOPIC,
                String.valueOf(response.getProductId()),
                response
        );
    }
}
