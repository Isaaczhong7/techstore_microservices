package com.techstore.lookup_service.kafka;

import com.techstore.kafka.lookup.ProductItemRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
@Service
@RequiredArgsConstructor
public class FetchProductProducer {
    private static final String TOPIC = "fetch-product-request";
    private final KafkaTemplate<String, ProductItemRequested> kafkaTemplate;
    public CompletableFuture<SendResult<String, ProductItemRequested>> publish(ProductItemRequested request) {
        System.out.println("hello from lookup side to fetch product");

        return kafkaTemplate.send(
                TOPIC,
                request.getProductId().toString(),
                request
        );
    }
}
