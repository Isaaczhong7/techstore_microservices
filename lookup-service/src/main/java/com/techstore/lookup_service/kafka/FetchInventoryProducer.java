package com.techstore.lookup_service.kafka;

import com.techstore.kafka.lookup.InventoryItemRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class FetchInventoryProducer {
    private static final String TOPIC = "fetch-inventory-request";
    private final KafkaTemplate<String, InventoryItemRequested> kafkaTemplate;
    public CompletableFuture<SendResult<String, InventoryItemRequested>> publish(InventoryItemRequested request) {
        System.out.println("hello from lookup side to fetch product");

        return kafkaTemplate.send(
                TOPIC,
                request.getProductId().toString(),
                request
        );
    }
}
