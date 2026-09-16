package com.techstore.product.kafka;

import com.techstore.kafka.order.CancelReservationRequested;
import com.techstore.kafka.product.ProductCheckCompleted;
import com.techstore.product.dto.QueryProductsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ProductCheckedProducer {
    private static final String TOPIC = "product-check";
    private final KafkaTemplate<String, ProductCheckCompleted> kafkaTemplate;

    public CompletableFuture<SendResult<String, ProductCheckCompleted>> publish(ProductCheckCompleted response) {

        return kafkaTemplate.send(
                TOPIC,
                response.getOrderId().toString(),
                response
        );
    }
}
