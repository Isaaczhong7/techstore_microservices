package com.techstore.inventory.service;

import com.techstore.inventory.dto.CreateInventoryRequest;
import com.techstore.inventory.dto.InventoryItemResponse;
import com.techstore.inventory.dto.UpdateProductRequest;
import com.techstore.inventory.entity.InventoryEntity;
import com.techstore.inventory.entity.InventoryReservationEntity;
import com.techstore.inventory.entity.InventoryReservationItemEntity;
import com.techstore.inventory.entity.ReservationItemStatus;
import com.techstore.inventory.entity.ReservationStatus;
import com.techstore.inventory.exception.InventoryConsistencyException;
import com.techstore.inventory.exception.InvalidCancellationException;
import com.techstore.inventory.exception.ProductIdNotFoundException;
import com.techstore.inventory.exception.ReservationNotFoundException;
import com.techstore.inventory.repository.InventoryRepository;
import com.techstore.inventory.repository.InventoryReservationRepository;
import com.techstore.inventory.repository.ProcessedEventRepository;
import com.techstore.kafka.order.CancelReservationRequested;
import com.techstore.kafka.order.ConfirmReservationRequested;
import com.techstore.kafka.order.CreateReservationRequested;
import com.techstore.kafka.order.ReservationItemRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class InventoryServiceTest {
    private static final UUID PRODUCT_1 = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID PRODUCT_2 = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID MISSING_PRODUCT = UUID.fromString("00000000-0000-0000-0000-000000000404");

    private InventoryRepository inventoryRepository;
    private InventoryReservationRepository inventoryReservationRepository;
    private OutboxEventService outboxEventService;
    private ProcessedEventRepository processedEventRepository;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(InventoryRepository.class);
        inventoryReservationRepository = mock(InventoryReservationRepository.class);
        outboxEventService = mock(OutboxEventService.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        inventoryService = new InventoryService(
                inventoryRepository,
                inventoryReservationRepository,
                outboxEventService,
                processedEventRepository
        );
    }

    @Test
    void createInventorySavesDefaultStockRowsAndPublishesInventoryEvents() {
        CreateInventoryRequest first = CreateInventoryRequest.builder()
                .productId(PRODUCT_1)
                .quantity(7L)
                .build();
        CreateInventoryRequest second = CreateInventoryRequest.builder()
                .productId(PRODUCT_2)
                .quantity(3L)
                .build();

        when(inventoryRepository.save(any(InventoryEntity.class)))
                .thenAnswer(invocation -> {
                    InventoryEntity entity = invocation.getArgument(0);
                    entity.setId(UUID.randomUUID());
                    return entity;
                });
        when(inventoryRepository.findByProductId(PRODUCT_1))
                .thenReturn(Optional.of(inventory(PRODUCT_1, 7L, 0L, 0L, 0L)));
        when(inventoryRepository.findByProductId(PRODUCT_2))
                .thenReturn(Optional.of(inventory(PRODUCT_2, 3L, 0L, 0L, 0L)));

        inventoryService.createInventory(List.of(first, second));

        ArgumentCaptor<InventoryEntity> saved = ArgumentCaptor.forClass(InventoryEntity.class);
        verify(inventoryRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(
                        InventoryEntity::getProductId,
                        InventoryEntity::getQuantity,
                        InventoryEntity::getReservedQuantity,
                        InventoryEntity::getItemSold,
                        InventoryEntity::getVersion
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_1, 7L, 0L, 0L, 0L),
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_2, 3L, 0L, 0L, 0L)
                );
        verify(outboxEventService, times(2)).saveEvent(any(UUID.class), eq("INVENTORY_ITEM_COMPLETED"), eq("inventory-item-update"), any(InventoryItemResponse.class));
    }

    @Test
    void updateSpecificProductRejectsInvalidQuantity() {
        assertThatThrownBy(() -> inventoryService.updateSpecificProduct(
                PRODUCT_1,
                new UpdateProductRequest(0L)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be greater than 0");

        verifyNoInteractions(inventoryRepository, outboxEventService);
    }

    @Test
    void updateSpecificProductAddsQuantityAndPublishesInventoryEvent() {
        InventoryEntity inventory = inventory(PRODUCT_1, 12L, 0L, 1L, 5L);
        when(inventoryRepository.addQuantity(PRODUCT_1, 4L)).thenReturn(1);
        when(inventoryRepository.findByProductId(PRODUCT_1)).thenReturn(Optional.of(inventory));

        inventoryService.updateSpecificProduct(
                PRODUCT_1,
                new UpdateProductRequest(4L)
        );

        verify(inventoryRepository).addQuantity(PRODUCT_1, 4L);
        verify(outboxEventService).saveEvent(
                eq(inventory.getId()),
                eq("INVENTORY_ITEM_COMPLETED"),
                eq("inventory-item-update"),
                any(InventoryItemResponse.class)
        );
    }

    @Test
    void updateSpecificProductThrowsWhenProductDoesNotExist() {
        when(inventoryRepository.addQuantity(MISSING_PRODUCT, 2L)).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.updateSpecificProduct(
                MISSING_PRODUCT,
                new UpdateProductRequest(2L)
        ))
                .isInstanceOf(ProductIdNotFoundException.class)
                .hasMessage("Unable to find product id: " + MISSING_PRODUCT);

        verify(outboxEventService, never()).saveEvent(any(), any(), any(), any());
    }

    @Test
    void createReservationIgnoresDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.tryMarkProcessed(eventId, "CREATE_RESERVATION_REQUESTED"))
                .thenReturn(0);

        inventoryService.createReservation(createReservationRequest(eventId, UUID.randomUUID()));

        verify(processedEventRepository).tryMarkProcessed(eventId, "CREATE_RESERVATION_REQUESTED");
        verifyNoInteractions(inventoryRepository, inventoryReservationRepository, outboxEventService);
    }

    @Test
    void createReservationReservesAllItemsAndPublishesCompletion() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(processedEventRepository.tryMarkProcessed(eventId, "CREATE_RESERVATION_REQUESTED"))
                .thenReturn(1);
        when(inventoryRepository.reserveIfAvailable(PRODUCT_1, 2L)).thenReturn(1);
        when(inventoryRepository.reserveIfAvailable(PRODUCT_2, 3L)).thenReturn(1);
        when(inventoryReservationRepository.save(any(InventoryReservationEntity.class)))
                .thenAnswer(invocation -> {
                    InventoryReservationEntity reservation = invocation.getArgument(0);
                    reservation.setId(UUID.randomUUID());
                    return reservation;
                });

        inventoryService.createReservation(createReservationRequest(eventId, orderId));

        ArgumentCaptor<InventoryReservationEntity> saved = ArgumentCaptor.forClass(InventoryReservationEntity.class);
        verify(inventoryReservationRepository).save(saved.capture());
        assertThat(saved.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(saved.getValue().getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(saved.getValue().getItems())
                .extracting(
                        InventoryReservationItemEntity::getProductId,
                        InventoryReservationItemEntity::getQuantity,
                        InventoryReservationItemEntity::getStatus
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_1, 2L, ReservationItemStatus.RESERVED),
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_2, 3L, ReservationItemStatus.RESERVED)
                );
        verify(outboxEventService).saveEvent(
                eq(orderId),
                eq("CREATE_RESERVATION_COMPLETED"),
                eq("reservation-created"),
                any()
        );
    }

    @Test
    void createReservationMarksPartialWhenSomeItemsCannotBeReserved() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(processedEventRepository.tryMarkProcessed(eventId, "CREATE_RESERVATION_REQUESTED"))
                .thenReturn(1);
        when(inventoryRepository.reserveIfAvailable(PRODUCT_1, 2L)).thenReturn(1);
        when(inventoryRepository.reserveIfAvailable(PRODUCT_2, 3L)).thenReturn(0);
        when(inventoryRepository.findByProductId(PRODUCT_2)).thenReturn(Optional.of(inventory(PRODUCT_2, 1L, 0L, 0L, 0L)));
        when(inventoryReservationRepository.save(any(InventoryReservationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.createReservation(createReservationRequest(eventId, orderId));

        ArgumentCaptor<InventoryReservationEntity> saved = ArgumentCaptor.forClass(InventoryReservationEntity.class);
        verify(inventoryReservationRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ReservationStatus.PARTIAL);
        assertThat(saved.getValue().getItems())
                .extracting(InventoryReservationItemEntity::getStatus)
                .containsExactly(ReservationItemStatus.RESERVED, ReservationItemStatus.OUT_OF_STOCK);
    }

    @Test
    void expireReservationReleasesReservedItemsOnlyAfterExpiration() {
        UUID reservationId = UUID.randomUUID();
        InventoryReservationEntity reservation = reservation(
                reservationId,
                ReservationStatus.PARTIAL,
                LocalDateTime.now().minusMinutes(1),
                item(PRODUCT_1, 2L, ReservationItemStatus.RESERVED),
                item(PRODUCT_2, 1L, ReservationItemStatus.OUT_OF_STOCK)
        );
        when(inventoryReservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.of(reservation));
        when(inventoryRepository.releaseReserved(PRODUCT_1, 2L)).thenReturn(1);

        inventoryService.expireReservation(reservationId);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservation.getItems())
                .extracting(InventoryReservationItemEntity::getStatus)
                .containsExactly(ReservationItemStatus.EXPIRED, ReservationItemStatus.OUT_OF_STOCK);
        verify(inventoryRepository).releaseReserved(PRODUCT_1, 2L);
        verify(inventoryRepository, never()).releaseReserved(PRODUCT_2, 1L);
        verify(inventoryReservationRepository).save(reservation);
    }

    @Test
    void expireReservationDoesNothingWhenReservationHasNotExpired() {
        UUID reservationId = UUID.randomUUID();
        InventoryReservationEntity reservation = reservation(
                reservationId,
                ReservationStatus.ACTIVE,
                LocalDateTime.now().plusMinutes(1),
                item(PRODUCT_1, 2L, ReservationItemStatus.RESERVED)
        );
        when(inventoryReservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.of(reservation));

        inventoryService.expireReservation(reservationId);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        verify(inventoryRepository, never()).releaseReserved(any(), any());
        verify(inventoryReservationRepository, never()).save(any());
    }

    @Test
    void confirmReservationConfirmsReservedItemsAndPublishesCompletion() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        InventoryReservationEntity reservation = reservation(
                reservationId,
                ReservationStatus.ACTIVE,
                LocalDateTime.now().plusMinutes(1),
                item(PRODUCT_1, 2L, ReservationItemStatus.RESERVED),
                item(PRODUCT_2, 3L, ReservationItemStatus.RESERVED)
        );
        reservation.setOrderId(orderId);
        when(processedEventRepository.tryMarkProcessed(eventId, "CONFIRM_RESERVATION_REQUESTED"))
                .thenReturn(1);
        when(inventoryReservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.of(reservation));
        when(inventoryRepository.confirmReserved(PRODUCT_1, 2L)).thenReturn(1);
        when(inventoryRepository.confirmReserved(PRODUCT_2, 3L)).thenReturn(1);
        when(inventoryRepository.findByProductId(PRODUCT_1)).thenReturn(Optional.of(inventory(PRODUCT_1, 8L, 0L, 2L, 1L)));
        when(inventoryRepository.findByProductId(PRODUCT_2)).thenReturn(Optional.of(inventory(PRODUCT_2, 7L, 0L, 3L, 1L)));

        inventoryService.confirmReservation(new ConfirmReservationRequested(eventId, orderId, reservationId));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getItems())
                .extracting(InventoryReservationItemEntity::getStatus)
                .containsExactly(ReservationItemStatus.CONFIRMED, ReservationItemStatus.CONFIRMED);
        verify(outboxEventService).saveEvent(
                eq(orderId),
                eq("CONFIRM_RESERVATION_COMPLETED"),
                eq("reservation-confirmed"),
                any()
        );
    }

    @Test
    void confirmReservationLeavesPartialWhenOnlyReservedItemsCanBeConfirmed() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        InventoryReservationEntity reservation = reservation(
                reservationId,
                ReservationStatus.PARTIAL,
                LocalDateTime.now().plusMinutes(1),
                item(PRODUCT_1, 2L, ReservationItemStatus.RESERVED),
                item(PRODUCT_2, 3L, ReservationItemStatus.OUT_OF_STOCK)
        );
        when(processedEventRepository.tryMarkProcessed(eventId, "CONFIRM_RESERVATION_REQUESTED"))
                .thenReturn(1);
        when(inventoryReservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.of(reservation));
        when(inventoryRepository.confirmReserved(PRODUCT_1, 2L)).thenReturn(1);
        when(inventoryRepository.findByProductId(PRODUCT_1)).thenReturn(Optional.of(inventory(PRODUCT_1, 8L, 0L, 2L, 1L)));

        inventoryService.confirmReservation(new ConfirmReservationRequested(eventId, orderId, reservationId));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIAL);
        assertThat(reservation.getItems())
                .extracting(InventoryReservationItemEntity::getStatus)
                .containsExactly(ReservationItemStatus.CONFIRMED, ReservationItemStatus.OUT_OF_STOCK);
        verify(inventoryRepository, never()).confirmReserved(PRODUCT_2, 3L);
    }

    @Test
    void confirmReservationThrowsWhenInventoryCannotBeDebited() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        InventoryReservationEntity reservation = reservation(
                reservationId,
                ReservationStatus.ACTIVE,
                LocalDateTime.now().plusMinutes(1),
                item(PRODUCT_1, 2L, ReservationItemStatus.RESERVED)
        );
        when(processedEventRepository.tryMarkProcessed(eventId, "CONFIRM_RESERVATION_REQUESTED"))
                .thenReturn(1);
        when(inventoryReservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.of(reservation));
        when(inventoryRepository.confirmReserved(PRODUCT_1, 2L)).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.confirmReservation(
                new ConfirmReservationRequested(eventId, orderId, reservationId)
        ))
                .isInstanceOf(InventoryConsistencyException.class)
                .hasMessageContaining("Unable to confirm reserved inventory");

        verify(outboxEventService, never()).saveEvent(eq(orderId), any(), any(), any());
    }

    @Test
    void confirmReservationIgnoresDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.tryMarkProcessed(eventId, "CONFIRM_RESERVATION_REQUESTED"))
                .thenReturn(0);

        inventoryService.confirmReservation(new ConfirmReservationRequested(
                eventId,
                UUID.randomUUID(),
                UUID.randomUUID()
        ));

        verifyNoInteractions(inventoryReservationRepository, inventoryRepository, outboxEventService);
    }

    @Test
    void cancelActiveReservationReleasesReservedItemsAndPublishesCompletion() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        InventoryReservationEntity reservation = reservation(
                reservationId,
                ReservationStatus.ACTIVE,
                LocalDateTime.now().plusMinutes(1),
                item(PRODUCT_1, 2L, ReservationItemStatus.RESERVED),
                item(PRODUCT_2, 1L, ReservationItemStatus.OUT_OF_STOCK)
        );
        reservation.setOrderId(orderId);
        when(processedEventRepository.tryMarkProcessed(eventId, "CANCEL_RESERVATION_REQUESTED"))
                .thenReturn(1);
        when(inventoryReservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.of(reservation));
        when(inventoryRepository.releaseReserved(PRODUCT_1, 2L)).thenReturn(1);

        inventoryService.cancelReservation(new CancelReservationRequested(eventId, orderId, reservationId));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCEL);
        assertThat(reservation.getItems())
                .extracting(InventoryReservationItemEntity::getStatus)
                .containsExactly(ReservationItemStatus.RELEASED, ReservationItemStatus.OUT_OF_STOCK);
        verify(outboxEventService).saveEvent(
                eq(orderId),
                eq("CANCEL_RESERVATION_COMPLETED"),
                eq("reservation-cancelled"),
                any()
        );
    }

    @Test
    void cancelConfirmedReservationRestoresSoldInventoryAndPublishesInventoryUpdates() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        InventoryReservationEntity reservation = reservation(
                reservationId,
                ReservationStatus.CONFIRMED,
                LocalDateTime.now().plusMinutes(1),
                item(PRODUCT_1, 2L, ReservationItemStatus.CONFIRMED)
        );
        reservation.setOrderId(orderId);
        InventoryEntity inventory = inventory(PRODUCT_1, 10L, 0L, 0L, 3L);
        when(processedEventRepository.tryMarkProcessed(eventId, "CANCEL_RESERVATION_REQUESTED"))
                .thenReturn(1);
        when(inventoryReservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.of(reservation));
        when(inventoryRepository.reverseQuantity(PRODUCT_1, 2L)).thenReturn(1);
        when(inventoryRepository.findByProductId(PRODUCT_1)).thenReturn(Optional.of(inventory));

        inventoryService.cancelReservation(new CancelReservationRequested(eventId, orderId, reservationId));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCEL);
        assertThat(reservation.getItems().get(0).getStatus()).isEqualTo(ReservationItemStatus.RELEASED);
        verify(outboxEventService).saveEvent(
                eq(inventory.getId()),
                eq("INVENTORY_ITEM_COMPLETED"),
                eq("inventory-item-update"),
                any(InventoryItemResponse.class)
        );
        verify(outboxEventService).saveEvent(
                eq(orderId),
                eq("CANCEL_RESERVATION_COMPLETED"),
                eq("reservation-cancelled"),
                any()
        );
    }

    @Test
    void cancelReservationThrowsForExpiredReservation() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        InventoryReservationEntity reservation = reservation(
                reservationId,
                ReservationStatus.EXPIRED,
                LocalDateTime.now().minusMinutes(1),
                item(PRODUCT_1, 2L, ReservationItemStatus.EXPIRED)
        );
        when(processedEventRepository.tryMarkProcessed(eventId, "CANCEL_RESERVATION_REQUESTED"))
                .thenReturn(1);
        when(inventoryReservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> inventoryService.cancelReservation(
                new CancelReservationRequested(eventId, orderId, reservationId)
        ))
                .isInstanceOf(InvalidCancellationException.class)
                .hasMessageContaining("unable to cancel reservation");

        verify(outboxEventService, never()).saveEvent(eq(orderId), any(), any(), any());
    }

    @Test
    void releaseItemThrowsWhenRepositoryCannotReleaseStock() {
        when(inventoryRepository.releaseReserved(PRODUCT_1, 2L)).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.releaseItem(PRODUCT_1, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to release reserved inventory");
    }

    @Test
    void getInventoryByProductIdThrowsWhenProductDoesNotExist() {
        when(inventoryRepository.findByProductId(MISSING_PRODUCT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getInventoryByProductId(MISSING_PRODUCT))
                .isInstanceOf(ProductIdNotFoundException.class)
                .hasMessage("Unable to find product id: " + MISSING_PRODUCT);
    }

    @Test
    void expireReservationThrowsWhenReservationDoesNotExist() {
        UUID reservationId = UUID.randomUUID();
        when(inventoryReservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.expireReservation(reservationId))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    private static CreateReservationRequested createReservationRequest(UUID eventId, UUID orderId) {
        return new CreateReservationRequested(
                eventId,
                orderId,
                LocalDateTime.now().plusMinutes(3),
                List.of(
                        new ReservationItemRequested(PRODUCT_1, 2L),
                        new ReservationItemRequested(PRODUCT_2, 3L)
                )
        );
    }

    private static InventoryEntity inventory(
            UUID productId,
            Long quantity,
            Long reservedQuantity,
            Long itemSold,
            Long version
    ) {
        return InventoryEntity.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .quantity(quantity)
                .reservedQuantity(reservedQuantity)
                .itemSold(itemSold)
                .version(version)
                .build();
    }

    private static InventoryReservationEntity reservation(
            UUID reservationId,
            ReservationStatus status,
            LocalDateTime expiresAt,
            InventoryReservationItemEntity... items
    ) {
        InventoryReservationEntity reservation = InventoryReservationEntity.builder()
                .id(reservationId)
                .orderId(UUID.randomUUID())
                .status(status)
                .expiresAt(expiresAt)
                .build();
        for (InventoryReservationItemEntity item : items) {
            reservation.addItem(item);
        }
        return reservation;
    }

    private static InventoryReservationItemEntity item(
            UUID productId,
            Long quantity,
            ReservationItemStatus status
    ) {
        return InventoryReservationItemEntity.builder()
                .productId(productId)
                .quantity(quantity)
                .status(status)
                .build();
    }
}
