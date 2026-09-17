package com.techstore.product.kafka;

import com.techstore.kafka.product.ProductCheckCompleted;
import com.techstore.kafka.product.ProductEntryCompleted;
import com.techstore.kafka.product.ProductItemCompleted;
import com.techstore.product.dto.ProductItemResponse;
import com.techstore.product.entity.OutboxStatus;
import com.techstore.product.dto.QueryProductsResponse;
import com.techstore.product.entity.OutboxEventEntity;
import com.techstore.product.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class OutboxEventProducer {

        private final OutboxEventRepository outboxEventRepository;
        private final ProductCheckedProducer productCheckedProducer;
        private final ProductItemProducer productItemProducer;
        private final ObjectMapper objectMapper;

        @Transactional
        public void publish(OutboxEventEntity event) {

            switch (event.getEventType()) {

                case "PRODUCT_CHECK_COMPLETED" -> publishProductCheckCompleted(event);

                case "PRODUCT_ITEM_COMPLETED" -> publishProductItemCompleted(event);
                default -> throw new IllegalArgumentException(
                        "Unknown outbox event type: "
                                + event.getEventType()
                );
            }

            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());

            outboxEventRepository.save(event);
        }
        public void publishProductItemCompleted(OutboxEventEntity event){

            ProductItemResponse payload;
            try {
                payload = objectMapper.readValue(
                        event.getPayload(),
                        ProductItemResponse.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException(
                        "Unable to deserialize outbox event "
                                + event.getId(),
                        e
                );
            }

            ProductItemCompleted response = ProductItemCompleted.newBuilder()
                    .setProductId(payload.getProductId())
                    .setActive(payload.getActive())
                    .setPrice(payload.getPrice())
                    .setProductName(payload.getProductName())
                    .setEventId(event.getId())
                    .setVersion(payload.getVersion())
                    .setDescription(payload.getDescription())
                    .setCategory(
                            com.techstore.kafka.product.ItemCategory.valueOf(
                                    payload.getCategory().name()
                            ))
                    .build();

            try{
                productItemProducer.publish(response)
                        .join();
            } catch (CompletionException e) {
                throw new IllegalStateException(
                        "Failed to publish product item outbox event "
                                + event.getId(),
                        e
                );
            }
        }
        private void publishProductCheckCompleted(OutboxEventEntity event) {

            QueryProductsResponse payload;
            try {
                payload = objectMapper.readValue(
                        event.getPayload(),
                        QueryProductsResponse.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException(
                        "Unable to deserialize outbox event "
                                + event.getId(),
                        e
                );
            }
            List<ProductEntryCompleted> completedEntries = new ArrayList<>();
            for(ProductItemResponse entry: payload.getProductList()){

                ProductEntryCompleted completedEntry = ProductEntryCompleted.newBuilder()
                        .setProductId(entry.getProductId())
                        .setActive(entry.getActive())
                        .setPrice(entry.getPrice())
                        .setProductName(entry.getProductName())
                        .build();
                completedEntries.add(completedEntry);
            }

            ProductCheckCompleted response = ProductCheckCompleted.newBuilder()
                    .setEventId(event.getId())
                    .setOrderId(payload.getOrderId().toString())
                    .setProductList(completedEntries)
                    .build();




            try {
                /*
                 * Important:
                 * wait until Kafka acknowledges the send.
                 */
                productCheckedProducer.publish(response)
                        .join();

            } catch (CompletionException e) {

                throw new IllegalStateException(
                        "Failed to publish query product outbox event "
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
