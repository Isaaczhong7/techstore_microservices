package com.techstore.product.kafka;

import com.techstore.kafka.product.ProductItemCompleted;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ProductItemProducer {
    private static final String TOPIC = "product-item-update";
    private final KafkaTemplate<String, ProductItemCompleted> kafkaTemplate;

    public CompletableFuture<SendResult<String, ProductItemCompleted>> publish(ProductItemCompleted response) {

        return kafkaTemplate.send(
                TOPIC,
                response.getProductId().toString(),
                response
        );
    }
}
