package com.techstore.inventory.kafka;


import com.techstore.inventory.dto.*;
import com.techstore.inventory.entity.OutboxEventEntity;
import com.techstore.inventory.entity.OutboxStatus;
import com.techstore.inventory.repository.OutboxEventRepository;
import com.techstore.kafka.inventory.*;

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
    private final ReservationCreatedProducer reservationCreatedProducer;
    private final ReservationCancelledProducer reservationCancelledProducer;
    private final ReservationConfirmedProducer reservationConfirmedProducer;
    private final InventoryItemProducer inventoryItemProducer;
    private final ObjectMapper objectMapper;

        @Transactional
        public void publish(OutboxEventEntity event) {

            switch (event.getEventType()) {

                case "CREATE_RESERVATION_COMPLETED" -> publishCreateReservationCompleted(event);

                case "CANCEL_RESERVATION_COMPLETED" -> publishCancelReservationCompleted(event);

                case "CONFIRM_RESERVATION_COMPLETED" -> publishConfirmReservationCompleted(event);

                case "INVENTORY_ITEM_COMPLETED" -> publishInventoryItemCompleted(event);

                default -> throw new IllegalArgumentException(
                        "Unknown outbox event type: "
                                + event.getEventType()
                );
            }

            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());

            outboxEventRepository.save(event);
        }
        public void publishInventoryItemCompleted(OutboxEventEntity event){
            InventoryItemResponse payload;

            try {
                payload = objectMapper.readValue(
                        event.getPayload(),
                        InventoryItemResponse.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException(
                        "Unable to deserialize outbox event "
                                + event.getId(),
                        e
                );
            }

            InventoryItemCompleted response = InventoryItemCompleted.newBuilder()
                    .setEventId(event.getId())
                    .setProductId(payload.getProductId())
                    .setItemSold(payload.getItemSold())
                    .setQuantity(payload.getQuantity())
                    .setVersion(payload.getVersion())
                    .build();

            try {
                inventoryItemProducer.publish(response)
                        .join();
            }catch(CompletionException e) {
                throw new IllegalStateException(
                        "Failed to publish inventory item completed outbox event "
                                + event.getId(),
                        e
                );
            }
        }


        public void publishCreateReservationCompleted(OutboxEventEntity event){
            InventoryReservationResponse payload;
            try {
                payload = objectMapper.readValue(
                        event.getPayload(),
                        InventoryReservationResponse.class
                );
            } catch (JacksonException e) {
                throw new IllegalStateException(
                        "Unable to deserialize outbox event "
                                + event.getId(),
                        e
                );
            }

            List<ReservationItemCompleted> reservationItems =
                    payload.getItems()
                            .stream()
                            .map(item ->
                                    ReservationItemCompleted.newBuilder()
                                            .setProductId(item.getProductId())
                                            .setQuantity(item.getQuantity())
                                            .setStatus(com.techstore.kafka.inventory.ReservationItemStatus.valueOf(
                                                    item.getStatus().name()
                                            ))
                                            .build()
                            )
                            .toList();

            InventoryReservationCompleted response = InventoryReservationCompleted.newBuilder()
                    .setEventId(event.getId())
                    .setReservationId(payload.getReservationId())
                    .setOrderId(payload.getOrderId())
                    .setStatus( com.techstore.kafka.inventory.ReservationStatus.valueOf(
                            payload.getStatus().name()
                    ))
                    .setItems(reservationItems)
                    .build();

            try{
                reservationCreatedProducer.publish(response)
                        .join();

            }catch(CompletionException e) {

                throw new IllegalStateException(
                        "Failed to publish create reservation completed outbox event "
                                + event.getId(),
                        e
                );

            }
        }

    public void publishConfirmReservationCompleted(OutboxEventEntity event){
        ConfirmReservationResponse payload;
        try {
            payload = objectMapper.readValue(
                    event.getPayload(),
                    ConfirmReservationResponse.class
            );
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Unable to deserialize outbox event "
                            + event.getId(),
                    e
            );
        }

        ConfirmReservationCompleted response =
                ConfirmReservationCompleted.newBuilder()
                        .setEventId(event.getId())
                        .setOrderId(payload.getOrderId())
                        .setReservationId(payload.getReservationId())
                        .setStatus(
                                com.techstore.kafka.inventory.ReservationStatus
                                        .valueOf(payload.getStatus().name())
                        )
                        .build();


        try{
            reservationConfirmedProducer.publish(response)
                    .join();

        }catch(CompletionException e) {

            throw new IllegalStateException(
                    "Failed to publish confirm reservation completed outbox event "
                            + event.getId(),
                    e
            );

        }
    }

    public void publishCancelReservationCompleted(OutboxEventEntity event){
        CancelReservationResponse payload;
        try {
            payload = objectMapper.readValue(
                    event.getPayload(),
                    CancelReservationResponse.class
            );
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Unable to deserialize outbox event "
                            + event.getId(),
                    e
            );
        }
        CancelReservationCompleted response =
                CancelReservationCompleted.newBuilder()
                        .setEventId(event.getId())
                        .setReservationId(payload.getReservationId())
                        .setOrderId(payload.getOrderId())
                        .setStatus(
                                com.techstore.kafka.inventory.ReservationStatus
                                        .valueOf(payload.getStatus().name())
                        )
                        .build();

        try{
            reservationCancelledProducer.publish(response)
                    .join();

        }catch(CompletionException e) {

            throw new IllegalStateException(
                    "Failed to publish cancel reservation completed outbox event "
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
