package com.techstore.order.kafka;


import com.techstore.kafka.order.*;
import com.techstore.kafka.order.ProductEntry;
import com.techstore.order.dto.*;
import com.techstore.order.entity.OutboxEventEntity;
import com.techstore.order.entity.OutboxStatus;
import com.techstore.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class OutboxEventProducer {

        private final OutboxEventRepository outboxEventRepository;
        private final CreateReservationProducer createReservationProducer;
        private final CancelOrderProducer cancelOrderProducer;
        private final ConfirmOrderProducer confirmOrderProducer;
        private final QueryProductProducer queryProductProducer;
        private final SubmitPaymentProducer submitPaymentProducer;
        private final CancelPaymentProducer cancelPaymentProducer;
        private final CreatePaymentProducer createPaymentProducer;
        private final ObjectMapper objectMapper;

        @Transactional
        public void publish(OutboxEventEntity event) {

            switch (event.getEventType()) {

                case "PRODUCT_CHECK_REQUESTED" -> publishProductCheck(event);

                case "CREATE_PAYMENT_REQUESTED" -> publishCreatePaymentRequested(event);

                case "SUBMIT_PAYMENT_REQUESTED" -> publishSubmitPaymentRequested(event);

                case "CANCEL_PAYMENT_REQUESTED" -> publishCancelPaymentRequested(event);

                case "CREATE_RESERVATION_REQUESTED" -> publishCreateReservationRequested(event);

                case "CANCEL_RESERVATION_REQUESTED" -> publishCancelReservationRequested(event);

                case "CONFIRM_RESERVATION_REQUESTED" -> publishConfirmReservationRequested(event);

                default -> throw new IllegalArgumentException(
                        "Unknown outbox event type: "
                                + event.getEventType()
                );
            }

            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());

            outboxEventRepository.save(event);
        }

        private void publishProductCheck(OutboxEventEntity event) {

            QueryProductsRequest payload;
            try {
                payload = objectMapper.readValue(
                        event.getPayload(),
                        QueryProductsRequest.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException(
                        "Unable to deserialize outbox event "
                                + event.getId(),
                        e
                );
            }

                List<ProductEntry>
                        products =
                        payload.getProducts()
                                .stream()
                                .map(product ->
                                        ProductEntry.newBuilder()
                                                .setProductId(
                                                        product.getProductId()
                                                )
                                                .setQuantity(
                                                        product.getQuantity()
                                                )
                                                .build()
                                )
                                .toList();

                ProductCheckRequested kafkaEvent =
                        ProductCheckRequested.newBuilder()
                                .setEventId(event.getId())
                                .setOrderId(
                                        payload.getOrderId().toString()
                                )
                                .setProducts(products)
                                .build();

            try {
                /*
                 * Important:
                 * wait until Kafka acknowledges the send.
                 */
                queryProductProducer.publish(kafkaEvent)
                        .join();

            } catch (CompletionException e) {

                throw new IllegalStateException(
                        "Failed to publish query product outbox event "
                                + event.getId(),
                        e
                );
            }
        }

        private void publishCreatePaymentRequested(OutboxEventEntity event){
            CreatePaymentRequest payload;
            try {
                payload = objectMapper.readValue(
                        event.getPayload(),
                        CreatePaymentRequest.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException(
                        "Unable to deserialize outbox event "
                                + event.getId(),
                        e
                );
            }

            CreatePaymentRequested request = CreatePaymentRequested.newBuilder()
                    .setEventId(event.getId())
                    .setReservationId(payload.getReservationId())
                    .setOrderId(payload.getOrderId())
                    .setAmount(payload.getAmount())
                    .setPaymentMethodId(payload.getPaymentMethodId())
                    .build();

            try{
                createPaymentProducer.publish(request)
                        .join();

            }catch(CompletionException e) {

                throw new IllegalStateException(
                        "Failed to publish crete payment requested outbox event "
                                + event.getId(),
                        e
                );

            }

        }

        public void publishSubmitPaymentRequested(OutboxEventEntity event){
            SubmitPaymentRequest payload;
            try {
                payload = objectMapper.readValue(
                        event.getPayload(),
                        SubmitPaymentRequest.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException(
                        "Unable to deserialize outbox event "
                                + event.getId(),
                        e
                );
            }
            SubmitPaymentRequested request = SubmitPaymentRequested.newBuilder()
                    .setEventId(event.getId())
                    .setOrderId(payload.getOrderId())
                    .setPaymentMethodId(payload.getPaymentMethodId())
                    .setPaymentId(payload.getPaymentId())
                    .setCurrencyType(
                            com.techstore.kafka.order.CurrencyType.valueOf(
                                    payload.getCurrencyType().name()
                            ))
                    .build();

            try{
                submitPaymentProducer.publish(request)
                        .join();
            }catch(CompletionException e) {

                throw new IllegalStateException(
                        "Failed to publish submit payment requested outbox event "
                                + event.getId(),
                        e
                );

            }
        }

        public void publishCancelPaymentRequested(OutboxEventEntity event){
            CancelPaymentRequest payload;
            try {
                payload = objectMapper.readValue(
                        event.getPayload(),
                        CancelPaymentRequest.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException(
                        "Unable to deserialize outbox event "
                                + event.getId(),
                        e
                );
            }

            CancelPaymentRequested request = CancelPaymentRequested.newBuilder()
                    .setEventId(event.getId())
                    .setOrderId(payload.getOrderId())
                    .setPaymentId(payload.getPaymentId())
                    .build();

            try{
                cancelPaymentProducer.publish(request)
                        .join();

            }catch(CompletionException e) {

                throw new IllegalStateException(
                        "Failed to publish cancel payment requested outbox event "
                                + event.getId(),
                        e
                );

            }

        }
        public void publishCreateReservationRequested(OutboxEventEntity event){
            CreateReservationRequest payload;
            try {
                payload = objectMapper.readValue(
                        event.getPayload(),
                        CreateReservationRequest.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException(
                        "Unable to deserialize outbox event "
                                + event.getId(),
                        e
                );
            }

            List<ReservationItemRequested> itemList = new ArrayList<>();

            for(ReservationItemRequest item : payload.getItems()){
                ReservationItemRequested itemRequested = ReservationItemRequested.newBuilder()
                        .setProductId(item.getProductId())
                        .setQuantity(item.getQuantity())
                        .build();
                itemList.add(itemRequested);
            }
            CreateReservationRequested request = CreateReservationRequested.newBuilder()
                    .setEventId(event.getId())
                    .setOrderId(payload.getOrderId())
                    .setExpiresAt(payload.getExpiresAt())
                    .setItems(itemList)
                    .build();

            try{
                createReservationProducer.publish(request)
                        .join();

            }catch(CompletionException e) {

                throw new IllegalStateException(
                        "Failed to publish create reservation requested outbox event "
                                + event.getId(),
                        e
                );

            }
        }

    public void publishConfirmReservationRequested(OutboxEventEntity event){
        ConfirmReservationRequest payload;
        try {
            payload = objectMapper.readValue(
                    event.getPayload(),
                    ConfirmReservationRequest.class
            );
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Unable to deserialize outbox event "
                            + event.getId(),
                    e
            );
        }

        ConfirmReservationRequested request = ConfirmReservationRequested.newBuilder()
                .setEventId(event.getId())
                .setOrderId(payload.getOrderId())
                .setReservationId(payload.getReservationId())
                .build();


        try{
            confirmOrderProducer.publish(request)
                    .join();

        }catch(CompletionException e) {

            throw new IllegalStateException(
                    "Failed to publish confirm reservation requested outbox event "
                            + event.getId(),
                    e
            );

        }
    }

    public void publishCancelReservationRequested(OutboxEventEntity event){
        CancelReservationRequest payload;
        try {
            payload = objectMapper.readValue(
                    event.getPayload(),
                    CancelReservationRequest.class
            );
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Unable to deserialize outbox event "
                            + event.getId(),
                    e
            );
        }

        CancelReservationRequested request = CancelReservationRequested.newBuilder()
                .setEventId(event.getId())
                .setOrderId(payload.getOrderId())
                .setReservationId(payload.getReservationId())
                .build();


        try{
            cancelOrderProducer.publish(request)
                    .join();

        }catch(CompletionException e) {

            throw new IllegalStateException(
                    "Failed to publish cancel reservation requested outbox event "
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
