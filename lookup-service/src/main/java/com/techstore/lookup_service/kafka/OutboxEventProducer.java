package com.techstore.lookup_service.kafka;



import com.techstore.kafka.inventory.*;
import com.techstore.kafka.lookup.InventoryItemRequested;
import com.techstore.kafka.lookup.ProductItemRequested;
import com.techstore.lookup_service.dto.InventoryItemRequest;
import com.techstore.lookup_service.dto.ProductItemRequest;
import com.techstore.lookup_service.entity.OutboxEventEntity;
import com.techstore.lookup_service.entity.OutboxStatus;
import com.techstore.lookup_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class OutboxEventProducer {

    private final OutboxEventRepository outboxEventRepository;
    private final FetchInventoryProducer fetchInventoryProducer;
    private final FetchProductProducer fetchProductProducer;

    private final ObjectMapper objectMapper;

        @Transactional
        public void publish(OutboxEventEntity event) {

            switch (event.getEventType()) {

                case "FETCH_PRODUCT_ITEM_REQUESTED" -> publishFetchProductItemRequested(event);

                case "FETCH_INVENTORY_ITEM_REQUESTED" -> publishFetchInventoryItemRequested(event);

                default -> throw new IllegalArgumentException(
                        "Unknown outbox event type: "
                                + event.getEventType()
                );
            }

            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());

            outboxEventRepository.save(event);
        }


        public void publishFetchProductItemRequested(OutboxEventEntity event){
            ProductItemRequest payload;
            try {
                payload = objectMapper.readValue(
                        event.getPayload(),
                        ProductItemRequest.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException(
                        "Unable to deserialize outbox event "
                                + event.getId(),
                        e
                );
            }

            ProductItemRequested request = ProductItemRequested.newBuilder()
                    .setProductId(payload.getProductId())
                    .build();

            try{
                fetchProductProducer.publish(request)
                        .join();

            }catch(CompletionException e) {

                throw new IllegalStateException(
                        "Failed to publish create reservation completed outbox event "
                                + event.getId(),
                        e
                );

            }
        }


    public void publishFetchInventoryItemRequested(OutboxEventEntity event){
        InventoryItemRequest payload;
        try {
            payload = objectMapper.readValue(
                    event.getPayload(),
                    InventoryItemRequest.class
            );
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Unable to deserialize outbox event "
                            + event.getId(),
                    e
            );
        }

        InventoryItemRequested request = InventoryItemRequested.newBuilder()
                .setProductId(payload.getProductId())
                .build();

        try{
            fetchInventoryProducer.publish(request)
                    .join();

        }catch(CompletionException e) {

            throw new IllegalStateException(
                    "Failed to publish create reservation completed outbox event "
                            + event.getId(),
                    e
            );

        }
    }


        @Transactional
        public void recordFailure(UUID eventId) {

            OutboxEventEntity event =
                    outboxEventRepository
                            .findById(eventId)
                            .orElseThrow();

            event.setAttempts(
                    event.getAttempts() + 1
            );

            if (event.getAttempts() >= 5) {
                event.setStatus(OutboxStatus.FAILED);
            }
        }

}
