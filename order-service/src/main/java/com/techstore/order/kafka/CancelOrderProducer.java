package com.techstore.order.kafka;

import com.techstore.kafka.order.CancelReservationRequested;
import com.techstore.order.dto.CancelReservationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class CancelOrderProducer {
    private static final String TOPIC = "order-cancel";
    private final KafkaTemplate<String, CancelReservationRequested> kafkaTemplate;

    public CompletableFuture<SendResult<String, CancelReservationRequested>> publish(CancelReservationRequested request) {

        return kafkaTemplate.send(
                TOPIC,
                request.getReservationId().toString(),
                request
        );
    }
}
