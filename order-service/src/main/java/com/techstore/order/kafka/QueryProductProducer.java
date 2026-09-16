package com.techstore.order.kafka;

import com.techstore.kafka.order.CreateReservationRequested;
import com.techstore.kafka.order.ProductCheckRequested;
import com.techstore.order.dto.QueryProductsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;


@Service
@RequiredArgsConstructor
public class QueryProductProducer {
    private static final String TOPIC = "query-product";
    private final KafkaTemplate<String, ProductCheckRequested> kafkaTemplate;

    public CompletableFuture<SendResult<String, ProductCheckRequested>> publish(ProductCheckRequested request) {

        return kafkaTemplate.send(
                TOPIC,
                request.getOrderId().toString(),
                request
        );
    }
}
