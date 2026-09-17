package com.techstore.order.service;

import com.techstore.kafka.inventory.CancelReservationCompleted;
import com.techstore.kafka.inventory.ConfirmReservationCompleted;
import com.techstore.kafka.inventory.InventoryReservationCompleted;
import com.techstore.kafka.inventory.ReservationItemCompleted;
import com.techstore.kafka.payment.CancelPaymentCompleted;
import com.techstore.kafka.payment.CreatePaymentCompleted;
import com.techstore.kafka.payment.SubmitPaymentCompleted;
import com.techstore.kafka.product.ProductCheckCompleted;
import com.techstore.kafka.product.ProductEntryCompleted;
import com.techstore.order.dto.CancelReservationRequest;
import com.techstore.order.dto.ConfirmReservationRequest;
import com.techstore.order.dto.CreateOrderRequest;
import com.techstore.order.dto.CreatePaymentRequest;
import com.techstore.order.dto.CustomerType;
import com.techstore.order.dto.OrderItemRequest;
import com.techstore.order.dto.QueryProductsRequest;
import com.techstore.order.dto.ReservationStatusResponse;
import com.techstore.order.dto.SubmitPaymentRequest;
import com.techstore.order.entity.CurrencyType;
import com.techstore.order.entity.OrderEntity;
import com.techstore.order.entity.OrderItemEntity;
import com.techstore.order.entity.OrderItemStatus;
import com.techstore.order.entity.OrderStatus;
import com.techstore.order.entity.PaymentStatus;
import com.techstore.order.exception.OrderNotFoundException;
import com.techstore.order.exception.PaymentIdNotFoundException;
import com.techstore.order.repository.OrderRepository;
import com.techstore.order.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceTest {
    private static final UUID PRODUCT_1 = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID PRODUCT_2 = UUID.fromString("00000000-0000-0000-0000-000000000020");

    private OrderRepository orderRepository;
    private OutboxEventService outboxEventService;
    private ProcessedEventRepository processedEventRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        outboxEventService = mock(OutboxEventService.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        orderService = new OrderService(orderRepository, outboxEventService, processedEventRepository);
    }

    @Test
    void createOrderPersistsPendingOrderAndRequestsProductCheck() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerType(CustomerType.GUEST)
                .email("guest@example.com")
                .items(List.of(
                        OrderItemRequest.builder().productId(PRODUCT_1).quantity(2L).build(),
                        OrderItemRequest.builder().productId(PRODUCT_2).quantity(1L).build()
                ))
                .build();

        ReservationStatusResponse response = orderService.createOrder(request);

        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(saved.capture());
        assertThat(response.getOrderId()).isEqualTo(saved.getValue().getId());
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getExpiresAt()).isEqualTo(saved.getValue().getExpiresAt());
        assertThat(saved.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getValue().getItems())
                .extracting(OrderItemEntity::getProductId, OrderItemEntity::getQuantity, OrderItemEntity::getStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_1, 2L, OrderItemStatus.PENDING),
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_2, 1L, OrderItemStatus.PENDING)
                );

        ArgumentCaptor<QueryProductsRequest> payload = ArgumentCaptor.forClass(QueryProductsRequest.class);
        verify(outboxEventService).saveEvent(eq(saved.getValue().getId()), eq("PRODUCT_CHECK_REQUESTED"), eq("query-product"), payload.capture());
        assertThat(payload.getValue().getProducts())
                .extracting(com.techstore.order.dto.ProductEntry::getProductId, com.techstore.order.dto.ProductEntry::getQuantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_1, 2L),
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_2, 1L)
                );
    }

    @Test
    void createOrderRejectsInvalidItems() {
        CreateOrderRequest missingProduct = CreateOrderRequest.builder()
                .customerType(CustomerType.GUEST)
                .email("guest@example.com")
                .items(List.of(OrderItemRequest.builder().quantity(1L).build()))
                .build();

        assertThatThrownBy(() -> orderService.createOrder(missingProduct))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product ID is required");

        CreateOrderRequest badQuantity = CreateOrderRequest.builder()
                .customerType(CustomerType.GUEST)
                .email("guest@example.com")
                .items(List.of(OrderItemRequest.builder().productId(PRODUCT_1).quantity(0L).build()))
                .build();

        assertThatThrownBy(() -> orderService.createOrder(badQuantity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be greater than 0");
    }

    @Test
    void productCheckUpdatesUnitPricesAndRequestsInventoryReservation() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.PENDING, PaymentStatus.PENDING, item(PRODUCT_1, 2L), item(PRODUCT_2, 1L));
        when(processedEventRepository.tryMarkProcessed(eventId, "PRODUCT_CHECK_COMPLETED")).thenReturn(1);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        orderService.productCheck(new ProductCheckCompleted(
                eventId,
                orderId.toString(),
                List.of(
                        new ProductEntryCompleted(PRODUCT_1, "Laptop", new BigDecimal("100.00"), true),
                        new ProductEntryCompleted(PRODUCT_2, "Mouse", new BigDecimal("25.00"), true)
                )
        ));

        assertThat(order.getItems())
                .extracting(OrderItemEntity::getUnitPrice)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("25.00"));
        verify(orderRepository).save(order);
        verify(outboxEventService).saveEvent(eq(orderId), eq("CREATE_RESERVATION_REQUESTED"), eq("order-create"), any());
    }

    @Test
    void productCheckIgnoresDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.tryMarkProcessed(eventId, "PRODUCT_CHECK_COMPLETED")).thenReturn(0);

        orderService.productCheck(new ProductCheckCompleted(eventId, UUID.randomUUID().toString(), List.of()));

        verifyNoInteractions(orderRepository, outboxEventService);
    }

    @Test
    void productCheckThrowsWhenProductResponseIsMissingItem() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.PENDING, PaymentStatus.PENDING, item(PRODUCT_1, 2L));
        when(processedEventRepository.tryMarkProcessed(eventId, "PRODUCT_CHECK_COMPLETED")).thenReturn(1);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.productCheck(new ProductCheckCompleted(
                eventId,
                orderId.toString(),
                List.of(new ProductEntryCompleted(PRODUCT_2, "Mouse", new BigDecimal("25.00"), true))
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing product information");
    }

    @Test
    void reservationUpdateMapsItemsCalculatesReservedTotalAndRequestsPaymentCreation() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.PENDING, PaymentStatus.PENDING, item(PRODUCT_1, 2L), item(PRODUCT_2, 1L));
        order.getItems().get(0).setUnitPrice(new BigDecimal("100.00"));
        order.getItems().get(1).setUnitPrice(new BigDecimal("25.00"));
        when(processedEventRepository.tryMarkProcessed(eventId, "CREATE_RESERVATION_COMPLETED")).thenReturn(1);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        orderService.reservationUpdate(new InventoryReservationCompleted(
                eventId,
                reservationId,
                orderId,
                com.techstore.kafka.inventory.ReservationStatus.PARTIAL,
                List.of(
                        new ReservationItemCompleted(PRODUCT_1, 2L, com.techstore.kafka.inventory.ReservationItemStatus.RESERVED),
                        new ReservationItemCompleted(PRODUCT_2, 1L, com.techstore.kafka.inventory.ReservationItemStatus.OUT_OF_STOCK)
                )
        ));

        assertThat(order.getReservationId()).isEqualTo(reservationId);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PARTIAL);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("200.00");
        assertThat(order.getItems())
                .extracting(OrderItemEntity::getStatus)
                .containsExactly(OrderItemStatus.RESERVED, OrderItemStatus.CANCELLED);
        verify(orderRepository).save(order);
        verify(outboxEventService).saveEvent(eq(orderId), eq("CREATE_PAYMENT_REQUESTED"), eq("payment-create"), any(CreatePaymentRequest.class));
    }

    @Test
    void createPaymentUpdateStoresPaymentIdAndStatus() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.PENDING, PaymentStatus.PENDING, item(PRODUCT_1, 1L));
        when(processedEventRepository.tryMarkProcessed(eventId, "CREATE_PAYMENT_COMPLETED")).thenReturn(1);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        orderService.CreatePaymentUpdate(new CreatePaymentCompleted(
                eventId,
                paymentId,
                orderId,
                UUID.randomUUID(),
                com.techstore.kafka.payment.PaymentStatus.PENDING,
                methodId
        ));

        assertThat(order.getPaymentID()).isEqualTo(paymentId);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(orderRepository).save(order);
    }

    @Test
    void submitPaymentRequiresPaymentId() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.PENDING, PaymentStatus.PENDING, item(PRODUCT_1, 1L));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.submitPayment(SubmitPaymentRequest.builder().orderId(orderId).build()))
                .isInstanceOf(PaymentIdNotFoundException.class)
                .hasMessageContaining("payment id is not found");
    }

    @Test
    void submitPaymentMarksConfirmingAndPublishesPaymentSubmitRequest() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.PENDING, PaymentStatus.PENDING, item(PRODUCT_1, 1L));
        order.setPaymentID(paymentId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        SubmitPaymentRequest request = SubmitPaymentRequest.builder()
                .orderId(orderId)
                .paymentId(paymentId)
                .currencyType(CurrencyType.USD)
                .paymentMethodId(UUID.randomUUID())
                .build();

        ReservationStatusResponse response = orderService.submitPayment(request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getExpiresAt()).isEqualTo(order.getExpiresAt());
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CONFIRMING);
        verify(orderRepository).save(order);
        verify(outboxEventService).saveEvent(orderId, "SUBMIT_PAYMENT_REQUESTED", "payment-submit", request);
    }

    @Test
    void submitPaymentUpdateOnSuccessRequestsInventoryConfirmation() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.PENDING, PaymentStatus.CONFIRMING, item(PRODUCT_1, 1L));
        order.setPaymentID(paymentId);
        order.setReservationId(reservationId);
        when(processedEventRepository.tryMarkProcessed(eventId, "SUBMIT_PAYMENT_COMPLETED")).thenReturn(1);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        orderService.submitPaymentUpdate(new SubmitPaymentCompleted(
                eventId,
                paymentId,
                com.techstore.kafka.payment.PaymentStatus.SUCCEEDED,
                orderId,
                reservationId
        ));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMING);
        verify(outboxEventService).saveEvent(eq(orderId), eq("CONFIRM_RESERVATION_REQUESTED"), eq("order-confirm"), any(ConfirmReservationRequest.class));
    }

    @Test
    void submitPaymentUpdateStoresFailedStatusWithoutConfirmingInventory() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.PENDING, PaymentStatus.CONFIRMING, item(PRODUCT_1, 1L));
        order.setPaymentID(paymentId);
        when(processedEventRepository.tryMarkProcessed(eventId, "SUBMIT_PAYMENT_COMPLETED")).thenReturn(1);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        orderService.submitPaymentUpdate(new SubmitPaymentCompleted(
                eventId,
                paymentId,
                com.techstore.kafka.payment.PaymentStatus.FAILED,
                orderId,
                UUID.randomUUID()
        ));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        verify(outboxEventService, never()).saveEvent(any(), any(), any(), any());
    }

    @Test
    void submitPaymentUpdateRejectsMismatchedPaymentId() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.PENDING, PaymentStatus.CONFIRMING, item(PRODUCT_1, 1L));
        order.setPaymentID(UUID.randomUUID());
        when(processedEventRepository.tryMarkProcessed(eventId, "SUBMIT_PAYMENT_COMPLETED")).thenReturn(1);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.submitPaymentUpdate(new SubmitPaymentCompleted(
                eventId,
                UUID.randomUUID(),
                com.techstore.kafka.payment.PaymentStatus.SUCCEEDED,
                orderId,
                UUID.randomUUID()
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment ID does not match order payment ID");
    }

    @Test
    void confirmOrderUpdateMarksReservedItemsConfirmed() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.CONFIRMING, PaymentStatus.SUCCEEDED, item(PRODUCT_1, 1L));
        order.getItems().get(0).setStatus(OrderItemStatus.RESERVED);
        when(processedEventRepository.tryMarkProcessed(eventId, "CONFIRM_RESERVATION_COMPLETED")).thenReturn(1);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        orderService.confirmOrderUpdate(new ConfirmReservationCompleted(
                eventId,
                com.techstore.kafka.inventory.ReservationStatus.CONFIRMED,
                reservationId,
                orderId
        ));

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.CONFIRMED);
    }

    @Test
    void confirmOrderUpdateCompensatesPaymentWhenInventoryConfirmationFailsAfterPaymentSucceeded() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.CONFIRMING, PaymentStatus.SUCCEEDED, item(PRODUCT_1, 1L));
        order.setPaymentID(paymentId);
        when(processedEventRepository.tryMarkProcessed(eventId, "CONFIRM_RESERVATION_COMPLETED")).thenReturn(1);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        orderService.confirmOrderUpdate(new ConfirmReservationCompleted(
                eventId,
                com.techstore.kafka.inventory.ReservationStatus.PARTIAL,
                UUID.randomUUID(),
                orderId
        ));

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLING);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLING);
        verify(outboxEventService).saveEvent(eq(orderId), eq("CANCEL_PAYMENT_REQUESTED"), eq("payment-cancel"), any());
    }

    @Test
    void cancelOrderRequestsReservationCancellation() {
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.PENDING, PaymentStatus.PENDING, item(PRODUCT_1, 1L));
        order.setReservationId(reservationId);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        ReservationStatusResponse response = orderService.cancelOrder(CancelReservationRequest.builder().orderId(orderId).build());

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLING);
        assertThat(response.getExpiresAt()).isEqualTo(order.getExpiresAt());
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLING);
        verify(outboxEventService).saveEvent(eq(orderId), eq("CANCEL_RESERVATION_REQUESTED"), eq("order-cancel"), any(CancelReservationRequest.class));
    }

    @Test
    void cancelOrderRejectsPaymentCurrentlyConfirming() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.PENDING, PaymentStatus.CONFIRMING, item(PRODUCT_1, 1L));
        order.setReservationId(UUID.randomUUID());
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(CancelReservationRequest.builder().orderId(orderId).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Order cannot be cancelled while payment is processing");
    }

    @Test
    void cancelOrderUpdateCancelsOrderWhenPaymentNeverSucceeded() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.CANCELLING, PaymentStatus.PENDING, item(PRODUCT_1, 1L));
        order.getItems().get(0).setStatus(OrderItemStatus.RESERVED);
        when(processedEventRepository.tryMarkProcessed(eventId, "CANCEL_RESERVATION_COMPLETED")).thenReturn(1);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        orderService.cancelOrderUpdate(new CancelReservationCompleted(
                eventId,
                orderId,
                UUID.randomUUID(),
                com.techstore.kafka.inventory.ReservationStatus.CANCEL
        ));

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(order.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
    }

    @Test
    void cancelOrderUpdateRefundsWhenPaymentSucceeded() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.CANCELLING, PaymentStatus.SUCCEEDED, item(PRODUCT_1, 1L));
        order.setPaymentID(paymentId);
        when(processedEventRepository.tryMarkProcessed(eventId, "CANCEL_RESERVATION_COMPLETED")).thenReturn(1);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        orderService.cancelOrderUpdate(new CancelReservationCompleted(
                eventId,
                orderId,
                UUID.randomUUID(),
                com.techstore.kafka.inventory.ReservationStatus.CANCEL
        ));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLING);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLING);
        verify(outboxEventService).saveEvent(eq(orderId), eq("CANCEL_PAYMENT_REQUESTED"), eq("payment-cancel"), any());
    }

    @Test
    void cancelPaymentUpdateCompletesCancellationSaga() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.CANCELLING, PaymentStatus.CANCELLING, item(PRODUCT_1, 1L));
        order.setPaymentID(paymentId);
        when(processedEventRepository.tryMarkProcessed(eventId, "CANCEL_PAYMENT_COMPLETED")).thenReturn(1);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        orderService.cancelPaymentUpdate(new CancelPaymentCompleted(
                eventId,
                paymentId,
                com.techstore.kafka.payment.PaymentStatus.CANCELLED,
                orderId,
                UUID.randomUUID()
        ));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void getOrderByIdThrowsWhenOrderDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(orderId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("unable to find order" + orderId);
    }

    @Test
    void getOrderByIdReturnsFullOrderEntryWithExpirationAndItems() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.PENDING, PaymentStatus.PENDING, item(PRODUCT_1, 2L));
        order.setPaymentID(paymentId);
        order.setReservationId(reservationId);
        order.setTotalAmount(BigDecimal.valueOf(499.99));
        order.getItems().get(0).setUnitPrice(BigDecimal.valueOf(249.995));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        var response = orderService.getOrderById(orderId);

        assertThat(response.getId()).isEqualTo(orderId);
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
        assertThat(response.getReservationId()).isEqualTo(reservationId);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getExpiresAt()).isEqualTo(order.getExpiresAt());
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductId()).isEqualTo(PRODUCT_1);
    }

    @Test
    void getAllOrdersReturnsOrderEntriesWithPaymentAndItems() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        OrderEntity order = order(orderId, OrderStatus.CONFIRMED, PaymentStatus.SUCCEEDED, item(PRODUCT_1, 2L));
        order.setPaymentID(paymentId);
        order.setReservationId(reservationId);
        order.setTotalAmount(BigDecimal.valueOf(1999));
        order.getItems().get(0).setUnitPrice(BigDecimal.valueOf(999.50));
        order.getItems().get(0).setStatus(OrderItemStatus.RESERVED);
        when(orderRepository.findAll()).thenReturn(List.of(order));

        var response = orderService.getAllOrders();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(orderId);
        assertThat(response.get(0).getPaymentId()).isEqualTo(paymentId);
        assertThat(response.get(0).getReservationId()).isEqualTo(reservationId);
        assertThat(response.get(0).getPaymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.get(0).getTotalAmount()).isEqualByComparingTo("1999");
        assertThat(response.get(0).getExpiresAt()).isEqualTo(order.getExpiresAt());
        assertThat(response.get(0).getItems()).hasSize(1);
        assertThat(response.get(0).getItems().get(0).getProductId()).isEqualTo(PRODUCT_1);
        assertThat(response.get(0).getItems().get(0).getUnitPrice()).isEqualByComparingTo("999.50");
    }

    private static OrderEntity order(UUID id, OrderStatus orderStatus, PaymentStatus paymentStatus, OrderItemEntity... items) {
        OrderEntity order = OrderEntity.builder()
                .id(id)
                .customerType(CustomerType.GUEST)
                .email("guest@example.com")
                .orderStatus(orderStatus)
                .paymentStatus(paymentStatus)
                .totalAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .build();
        for (OrderItemEntity item : items) {
            order.addItem(item);
        }
        return order;
    }

    private static OrderItemEntity item(UUID productId, Long quantity) {
        return OrderItemEntity.builder()
                .productId(productId)
                .quantity(quantity)
                .status(OrderItemStatus.PENDING)
                .build();
    }
}
