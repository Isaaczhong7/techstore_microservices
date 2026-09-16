package com.techstore.lookup_service.kafka;

import com.techstore.kafka.lookup.InventoryItemRequested;
import com.techstore.kafka.lookup.ProductItemRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class FetchInventoryProducer {
    private static final String TOPIC = "fetch-inventory-request";
    private final KafkaTemplate<Long, InventoryItemRequested> kafkaTemplate;
    public CompletableFuture<SendResult<Long, InventoryItemRequested>> publish(InventoryItemRequested request) {
        System.out.println("hello from lookup side to fetch product");

        return kafkaTemplate.send(
                TOPIC,
                request.getProductId(),
                request
        );
    }
}
