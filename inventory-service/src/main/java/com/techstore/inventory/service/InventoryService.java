package com.techstore.inventory.service;

import com.techstore.inventory.dto.*;
import com.techstore.inventory.entity.*;
import com.techstore.inventory.exception.*;
import com.techstore.inventory.repository.InventoryRepository;
import com.techstore.inventory.repository.InventoryReservationRepository;
import com.techstore.inventory.kafka.ReservationCancelledProducer;
import com.techstore.inventory.kafka.ReservationConfirmedProducer;
import com.techstore.inventory.kafka.ReservationCreatedProducer;
import com.techstore.inventory.repository.ProcessedEventRepository;
import com.techstore.kafka.inventory.CancelReservationCompleted;
import com.techstore.kafka.inventory.ConfirmReservationCompleted;
import com.techstore.kafka.inventory.InventoryReservationCompleted;
import com.techstore.kafka.inventory.ReservationItemCompleted;
import com.techstore.kafka.order.CancelReservationRequested;
import com.techstore.kafka.order.ConfirmReservationRequested;
import com.techstore.kafka.order.CreateReservationRequested;
import com.techstore.kafka.order.ReservationItemRequested;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final OutboxEventService outboxEventService;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void createInventory(List<CreateInventoryRequest> req) {
        for(CreateInventoryRequest reqEntry : req) {
            InventoryEntity savedEntry = InventoryEntity.builder()
                    .productId(reqEntry.getProductId())
                    .quantity(reqEntry.getQuantity())
                    .itemSold(0L)
                    .reservedQuantity(0L)
                    .version(0L)
                    .build();

            inventoryRepository.save(savedEntry);
            getInventoryByProductId(savedEntry.getProductId());
        }
    }

    @Transactional
    public void updateSpecificProduct(UUID productId, UpdateProductRequest request) {

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than 0"
            );
        }

        int updated = inventoryRepository.addQuantity(
                productId,
                request.getQuantity()
        );

        if (updated == 0) {
            throw new ProductIdNotFoundException(
                    "Unable to find product id: " + productId
            );
        }

        getInventoryByProductId(productId);


    }

    @KafkaListener(topics = "order-create")
    @Transactional
    public void createReservation(CreateReservationRequested request){

        int inserted =
                processedEventRepository.tryMarkProcessed(
                        request.getEventId(),
                        "CREATE_RESERVATION_REQUESTED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }
        System.out.println("trying to create reservation in inventory");
        List<ReservationItemRequest> items = new ArrayList<>();
        for(ReservationItemRequested itemFrom : request.getItems()){
            ReservationItemRequest item = ReservationItemRequest.builder()
                    .productId(itemFrom.getProductId())
                    .quantity(itemFrom.getQuantity())
                    .build();
            items.add(item);
        }

        CreateReservationRequest createReservationRequest = CreateReservationRequest.builder()
                .expiresAt(request.getExpiresAt())
                .orderId(request.getOrderId())
                .items(items)
                .build();
        boolean hasReservedItem = true;

        InventoryReservationEntity reservationEntry = InventoryReservationEntity.builder()
                .orderId(request.getOrderId())
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .status(ReservationStatus.ACTIVE)
                .build();

        for(ReservationItemRequest item : createReservationRequest.getItems()){
            ReservationItemStatus itemStatus = reserveItem(item.getProductId(), item.getQuantity());
            if(itemStatus != ReservationItemStatus.RESERVED){
                hasReservedItem = false;
            }
            InventoryReservationItemEntity itemEntry = InventoryReservationItemEntity.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .status(itemStatus)
                    .build();

            reservationEntry.addItem(itemEntry);
        }

        if(!hasReservedItem){
            reservationEntry.setStatus(ReservationStatus.PARTIAL);
        }

        inventoryReservationRepository.save(reservationEntry);


        List<ReservationItemResponse> reservationItems =
                reservationEntry.getItems()
                        .stream()
                        .map(item ->
                                ReservationItemResponse.builder()
                                        .productId(item.getProductId())
                                        .quantity(item.getQuantity())
                                        .status(item.getStatus())
                                        .build()
                        )
                        .toList();

        InventoryReservationResponse payload = InventoryReservationResponse.builder()
                .reservationId(reservationEntry.getId())
                .orderId(reservationEntry.getOrderId())
                .status(reservationEntry.getStatus())
                .items(reservationItems)
                .build();

        outboxEventService.saveEvent(
                reservationEntry.getOrderId(),
                "CREATE_RESERVATION_COMPLETED",
                "reservation-created",
                payload
        );

    }



    private ReservationItemStatus reserveItem(UUID productId, Long quantity) {
        validateQuantity(quantity);

        int updatedRows =
                inventoryRepository.reserveIfAvailable(
                        productId,
                        quantity
                );

        if (updatedRows == 1) {
            return ReservationItemStatus.RESERVED;
        }

        boolean inventoryExists =
                inventoryRepository
                        .findByProductId(productId)
                        .isPresent();

        if (!inventoryExists) {
            return ReservationItemStatus.INVENTORY_NOT_FOUND;
        }

        return ReservationItemStatus.OUT_OF_STOCK;

    }



    public void validateQuantity(Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than 0"
            );
        }
    }

    @Transactional
    public void expireReservation(UUID reservationId) {

        InventoryReservationEntity reservation =
                inventoryReservationRepository
                        .findByIdForUpdate(reservationId)
                        .orElseThrow(() ->
                                new ReservationNotFoundException(
                                        reservationId
                                )
                        );

        // Only ACTIVE or PARTIAL reservations can expire
        if (reservation.getStatus() != ReservationStatus.ACTIVE
                && reservation.getStatus() != ReservationStatus.PARTIAL) {
            return;
        }

        // Not expired yet
        if (reservation.getExpiresAt()
                .isAfter(LocalDateTime.now())) {
            return;
        }

        for (InventoryReservationItemEntity item :
                reservation.getItems()) {

            // IMPORTANT:
            // only release stock that was actually reserved
            if (item.getStatus()
                    != ReservationItemStatus.RESERVED) {
                continue;
            }

            releaseItem(
                    item.getProductId(),
                    item.getQuantity()
            );

            item.setStatus(
                    ReservationItemStatus.EXPIRED
            );
        }

        reservation.setStatus(
                ReservationStatus.EXPIRED
        );

        inventoryReservationRepository.save(reservation);
    }

    @Transactional
    public void releaseItem(UUID productId, Long quantity) {

        validateQuantity(quantity);

        int updatedRows =
                inventoryRepository.releaseReserved(
                        productId,
                        quantity
                );

        if (updatedRows == 0) {
            throw new IllegalStateException(
                    "Unable to release reserved inventory. "
                            + "Product: " + productId
                            + ", quantity: " + quantity
            );
        }


    }

    @KafkaListener(topics = "order-confirm")
    @Transactional
    public void confirmReservation(ConfirmReservationRequested request) {

        int inserted =
                processedEventRepository.tryMarkProcessed(
                        request.getEventId(),
                        "CONFIRM_RESERVATION_REQUESTED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }

        UUID reservationId = request.getReservationId();

        InventoryReservationEntity entity = inventoryReservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() ->
                    new ReservationNotFoundException(reservationId)
                );

        if (entity.getStatus() == ReservationStatus.CONFIRMED) {
            return;
        }

        if (entity.getStatus() != ReservationStatus.ACTIVE
                && entity.getStatus() != ReservationStatus.PARTIAL) {

            throw new IllegalStateException(
                    "Cannot confirm reservation " + reservationId
                            + " with status " + entity.getStatus()
            );
        }

        for (InventoryReservationItemEntity item : entity.getItems()) {

            if (item.getStatus() != ReservationItemStatus.RESERVED) {
                continue;
            }

            int updated = inventoryRepository.confirmReserved(
                    item.getProductId(),
                    item.getQuantity()
            );

            if (updated != 1) {
                throw new InventoryConsistencyException(
                        "Unable to confirm reserved inventory. "
                                + "reservationId=" + reservationId
                                + ", productId=" + item.getProductId()
                );
            }

            item.setStatus(ReservationItemStatus.CONFIRMED);
            getInventoryByProductId(item.getProductId());
        }

        boolean allConfirmed = entity.getItems()
                .stream()
                .allMatch(item ->
                        item.getStatus() == ReservationItemStatus.CONFIRMED
                );

        entity.setStatus(
                allConfirmed
                        ? ReservationStatus.CONFIRMED
                        : ReservationStatus.PARTIAL
        );
        ConfirmReservationResponse payload = ConfirmReservationResponse.builder()
                .status(entity.getStatus())
                .reservationId(entity.getId())
                .orderId(entity.getOrderId())
                .build();

        outboxEventService.saveEvent(
                entity.getOrderId(),
                "CONFIRM_RESERVATION_COMPLETED",
                "reservation-confirmed",
                payload
        );

    }

    @KafkaListener(topics = "order-cancel")
    @Transactional
    public void cancelReservation(CancelReservationRequested request) {
        int inserted =
                processedEventRepository.tryMarkProcessed(
                        request.getEventId(),
                        "CANCEL_RESERVATION_REQUESTED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }
        System.out.println("order is trying to cancel");

        UUID reservationId = request.getReservationId();

        InventoryReservationEntity entity =
                inventoryReservationRepository
                        .findByIdForUpdate(reservationId)
                        .orElseThrow(() ->
                                new ReservationNotFoundException(
                                        reservationId
                                )
                        );
        System.out.println("current status is " + entity.getStatus());
        // Duplicate Kafka message -> already processed
        if (entity.getStatus() == ReservationStatus.CANCEL) {
            return;
        }

        // Only ACTIVE or PARTIAL or CONFIRMED reservations can be cancelled
        if (entity.getStatus() != ReservationStatus.ACTIVE
                && entity.getStatus() != ReservationStatus.PARTIAL && entity.getStatus() != ReservationStatus.CONFIRMED) {
            throw new InvalidCancellationException("unable to cancel reservation current status is " + entity.getStatus());
        }

        if (entity.getStatus() == ReservationStatus.ACTIVE
                || entity.getStatus() == ReservationStatus.PARTIAL) {
            for (InventoryReservationItemEntity item : entity.getItems()) {

                if (item.getStatus() != ReservationItemStatus.RESERVED) {
                    continue;
                }

                int updated = inventoryRepository.releaseReserved(
                        item.getProductId(),
                        item.getQuantity()
                );

                if (updated != 1) {
                    throw new InventoryConsistencyException(
                            "Unable to release reserved inventory. "
                                    + "reservationId=" + reservationId
                                    + ", productId=" + item.getProductId()
                    );
                }

                item.setStatus(ReservationItemStatus.RELEASED);
            }


        } else {

            for (InventoryReservationItemEntity item : entity.getItems()) {

                if (item.getStatus() != ReservationItemStatus.CONFIRMED) {
                    continue;
                }

                int updated = inventoryRepository.reverseQuantity(
                        item.getProductId(),
                        item.getQuantity()
                );

                if (updated != 1) {
                    throw new InventoryConsistencyException(
                            "Unable to restore confirmed inventory. "
                                    + "reservationId=" + entity.getId()
                                    + ", productId=" + item.getProductId()
                    );
                }

                item.setStatus(ReservationItemStatus.RELEASED);

                getInventoryByProductId(item.getProductId()); // Trigger outbox event for inventory item update

            }
        }

        entity.setStatus(ReservationStatus.CANCEL);

        CancelReservationResponse payload = CancelReservationResponse.builder()
                .status(entity.getStatus())
                .reservationId(entity.getId())
                .orderId(entity.getOrderId())
                .build();

        outboxEventService.saveEvent(
                entity.getOrderId(),
                "CANCEL_RESERVATION_COMPLETED",
                "reservation-cancelled",
                payload

        );

    }


    public void getInventoryByProductId(UUID productId) {
        InventoryEntity entry = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductIdNotFoundException(
                        "Unable to find product id: " + productId
                ));

        InventoryItemResponse response = InventoryItemResponse.builder()
                .productId(entry.getProductId())
                .quantity(entry.getQuantity())
                .itemSold(entry.getItemSold())
                .version(entry.getVersion())
                .build();

        outboxEventService.saveEvent(
                entry.getId(),
                "INVENTORY_ITEM_COMPLETED",
                "inventory-item-update",
                response
        );
    }

    public List<InventoryItemResponse> getAllInventory(){
        return inventoryRepository.findAll()
                .stream()
                .map(
                        entry -> InventoryItemResponse.builder()
                                .productId(entry.getProductId())
                                .quantity(entry.getQuantity())
                                .itemSold(entry.getItemSold())
                                .version(entry.getVersion())
                                .build()
                ).toList();
    }
}
