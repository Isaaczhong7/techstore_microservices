package com.techstore.order.service;

import com.techstore.kafka.inventory.CancelReservationCompleted;
import com.techstore.kafka.inventory.ConfirmReservationCompleted;
import com.techstore.kafka.inventory.InventoryReservationCompleted;
import com.techstore.kafka.inventory.ReservationItemCompleted;

import com.techstore.kafka.payment.CancelPaymentCompleted;
import com.techstore.kafka.payment.CreatePaymentCompleted;
import com.techstore.kafka.payment.SubmitPaymentCompleted;
import com.techstore.kafka.product.ProductCheckCompleted;
import com.techstore.order.dto.*;
import com.techstore.order.entity.*;
import com.techstore.order.exception.OrderNotFoundException;
import com.techstore.order.exception.PaymentIdNotFoundException;
import com.techstore.order.repository.OrderRepository;
import com.techstore.order.repository.ProcessedEventRepository;
import jakarta.transaction.Transactional;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OutboxEventService outboxEventService;
    private final ProcessedEventRepository processedEventRepository;
    private final int expiresInMinutes = 3;
    private void validateOrderItem(
            OrderItemRequest item
    ) {

        if (item.getProductId() == null) {
            throw new IllegalArgumentException(
                    "Product ID is required"
            );
        }

        if (item.getQuantity() == null
                || item.getQuantity() <= 0) {

            throw new IllegalArgumentException(
                    "Quantity must be greater than 0"
            );
        }
    }
    @Transactional
    public ReservationStatusResponse createOrder(
            CreateOrderRequest request
    ) {

        OrderEntity order = OrderEntity.builder()
                .id(UUID.randomUUID())
                .customerId(request.getCustomerId())
                .email(request.getEmail())
                .customerType(request.getCustomerType())
                .orderStatus(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(expiresInMinutes))
                .totalAmount(BigDecimal.ZERO)
                .build();

        List<com.techstore.order.dto.ProductEntry> productEntries = new ArrayList<>();;

        // 1. Validate products and create order items
        for (OrderItemRequest item : request.getItems()) {

            validateOrderItem(item);
            com.techstore.order.dto.ProductEntry entry = com.techstore.order.dto.ProductEntry.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .build();

            OrderItemEntity orderItem = OrderItemEntity.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .status(OrderItemStatus.PENDING)
                    .build();

            order.addItem(orderItem);

            productEntries.add(entry);

        }
        QueryProductsRequest payload = QueryProductsRequest.builder()
                .orderId(order.getId())
                .products(productEntries)
                .build();

        orderRepository.save(order);


        outboxEventService.saveEvent(
                order.getId(),
                "PRODUCT_CHECK_REQUESTED",
                "query-product",
                payload
        );

        return ReservationStatusResponse.builder()
                .orderId(order.getId())
                .paymentId(order.getPaymentID())
                .reservationId(order.getReservationId())
                .status(order.getOrderStatus())
                .expiresAt(order.getExpiresAt())
                .build();
    }



    @Transactional
    @KafkaListener(topics = "product-check")
    public void productCheck(ProductCheckCompleted response){

        int inserted =
                processedEventRepository.tryMarkProcessed(
                        response.getEventId(),
                        "PRODUCT_CHECK_COMPLETED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }

        UUID orderId = UUID.fromString(response.getOrderId().toString());
        OrderEntity entity = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() ->
                        new OrderNotFoundException(orderId)
                        );

        if (entity.getReservationId() != null) {
            return;
        }

        Map<UUID, com.techstore.kafka.product.ProductEntryCompleted> productsById =
                response.getProductList()
                        .stream()
                        .collect(Collectors.toMap(
                                com.techstore.kafka.product.ProductEntryCompleted::getProductId,
                                Function.identity()
                        ));

        entity.getItems().forEach(item -> {

            var product = productsById.get(item.getProductId());

            if (product == null) {
                throw new IllegalStateException(
                        "Missing product information for product: "
                                + item.getProductId()
                );
            }

            item.setUnitPrice(product.getPrice());
        });

        orderRepository.save(entity);

        createInventoryReservation(entity);

    }

    @Transactional
    @KafkaListener(topics ="reservation-created")
    public void reservationUpdate(InventoryReservationCompleted reservationResponse){
        System.out.println("hello reservation-created");

        int inserted =
                processedEventRepository.tryMarkProcessed(
                        reservationResponse.getEventId(),
                        "CREATE_RESERVATION_COMPLETED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }


        List<ReservationItemResponse> items = new ArrayList<>();
        for(ReservationItemCompleted itemFromInventory : reservationResponse.getItems()){
            ReservationItemResponse item = ReservationItemResponse.builder()
                    .productId(itemFromInventory.getProductId())
                    .quantity(itemFromInventory.getQuantity())
                    .status(    com.techstore.order.entity.ReservationItemStatus.valueOf(
                            itemFromInventory.getStatus().name()
                    ))
                    .build();
            items.add(item);
        }
        InventoryReservationResponse inventoryReservationResponse = InventoryReservationResponse.builder()
                .reservationId(reservationResponse.getReservationId())
                .orderId(reservationResponse.getOrderId())
                .status( ReservationStatus.valueOf(
                        reservationResponse.getStatus().name()
                ))
                .items(items)
                .build();

        OrderEntity order = orderRepository.findByIdForUpdate( inventoryReservationResponse.getOrderId())
                .orElseThrow(() ->
                        new OrderNotFoundException(inventoryReservationResponse.getOrderId())
                );

        if(order.getPaymentID() != null){
            return;
        }

        //  Map inventory results → order item statuses
        updateOrderItemsFromReservation(
                order,
                inventoryReservationResponse
        );

        //  Copy reservation information
        order.setReservationId(
                inventoryReservationResponse.getReservationId()
        );



        //  Total only successfully reserved products
        order.setTotalAmount(
                calculateReservedTotal(order)
        );

        //  PENDING / PARTIAL / CANCELLED
        updateOrderStatus(
                order,
                inventoryReservationResponse
        );

        orderRepository.save(order);

        CreatePaymentRequest payload = CreatePaymentRequest.builder()
                .paymentMethodId(UUID.randomUUID())
                .orderId(order.getId())
                .reservationId(order.getReservationId())
                .amount(order.getTotalAmount())
                .build();

        outboxEventService.saveEvent(
                order.getId(),
                "CREATE_PAYMENT_REQUESTED",
                "payment-create",
                payload

        );

    }
    @Transactional
    public ReservationStatusResponse submitPayment(SubmitPaymentRequest request){

        OrderEntity order =
                orderRepository.findByIdForUpdate(request.getOrderId())
                        .orElseThrow(() ->
                                new OrderNotFoundException(request.getOrderId())
                        );

        if(order.getPaymentID() == null){
            throw new PaymentIdNotFoundException("payment id is not found" + order.getId() );
        }


        if (order.getOrderStatus() != OrderStatus.PENDING
                && order.getOrderStatus() != OrderStatus.PARTIAL) {

            throw new RuntimeException(
                    "Order cannot be confirmed. Current status: "
                            + order.getOrderStatus()
            );
        }

        if (order.getExpiresAt() != null
                && LocalDateTime.now().isAfter(order.getExpiresAt())) {
            throw new IllegalStateException(
                    "Reservation has expired for order: " + order.getId()
            );
        }
        order.setPaymentStatus(PaymentStatus.CONFIRMING);
        orderRepository.save(order);
        System.out.println("submitted into kafka from order");

        outboxEventService.saveEvent(
                order.getId(),
                "SUBMIT_PAYMENT_REQUESTED",
                "payment-submit",
                request
        );

        return ReservationStatusResponse.builder()
                .orderId(order.getId())
                .paymentId(order.getPaymentID())
                .reservationId(order.getReservationId())
                .status(order.getOrderStatus())
                .expiresAt(order.getExpiresAt())
                .build();

    }

    @KafkaListener(topics = "payment-submitted")
    @Transactional
    public void submitPaymentUpdate(SubmitPaymentCompleted response) {

        int inserted =
                processedEventRepository.tryMarkProcessed(
                        response.getEventId(),
                        "SUBMIT_PAYMENT_COMPLETED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }

        OrderEntity order = orderRepository
                .findByIdForUpdate(response.getOrderId())
                .orElseThrow(() ->
                        new OrderNotFoundException(response.getOrderId())
                );

        PaymentStatus paymentStatus =
                PaymentStatus.valueOf(response.getStatus().name());

        // Optional validation: make sure response belongs to this order
        if (!response.getPaymentId().equals(order.getPaymentID())) {
            throw new IllegalStateException(
                    "Payment ID does not match order payment ID"
            );
        }

        // Duplicate successful event
        if (order.getPaymentStatus() == PaymentStatus.SUCCEEDED) {
            return;
        }

        // Payment failed
        if (paymentStatus != PaymentStatus.SUCCEEDED) {

            order.setPaymentStatus(paymentStatus);

            // Do NOT change orderStatus here.
            // It should remain PENDING or PARTIAL.

            return;
        }

        // Payment succeeded
        order.setPaymentStatus(PaymentStatus.SUCCEEDED);

        // Now inventory confirmation begins
        order.setOrderStatus(OrderStatus.CONFIRMING);

        if (order.getReservationId() == null) {
            throw new IllegalStateException(
                    "Reservation ID is missing for order: " + order.getId()
            );
        }
        ConfirmReservationRequest payload = ConfirmReservationRequest.builder()
                .orderId(order.getId())
                .reservationId(order.getReservationId())
                .build();

        outboxEventService.saveEvent(
                order.getId(),
                "CONFIRM_RESERVATION_REQUESTED",
                "order-confirm",
                payload
        );
    }


    @KafkaListener(topics = "reservation-confirmed")
    @Transactional
    public void confirmOrderUpdate(ConfirmReservationCompleted response) {

        int inserted =
                processedEventRepository.tryMarkProcessed(
                        response.getEventId(),
                        "CONFIRM_RESERVATION_COMPLETED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }

        OrderEntity order = orderRepository
                .findByIdForUpdate(response.getOrderId())
                .orElseThrow(() ->
                        new OrderNotFoundException(response.getOrderId())
                );

        ReservationStatus reservationStatus =
                ReservationStatus.valueOf(
                        response.getStatus().name()
                );

        // Inventory confirmation succeeded
        if (reservationStatus == ReservationStatus.CONFIRMED) {

            order.setOrderStatus(OrderStatus.CONFIRMED);

            for (OrderItemEntity item : order.getItems()) {
                if (item.getStatus() == OrderItemStatus.RESERVED) {
                    item.setStatus(OrderItemStatus.CONFIRMED);
                }
            }

            return;
        }

        if(order.getPaymentStatus() == PaymentStatus.CANCELLING && order.getOrderStatus() == OrderStatus.CANCELLING){
            return;
        }
        if(order.getPaymentStatus() == PaymentStatus.CANCELLED && order.getOrderStatus() == OrderStatus.CANCELLED){
            return;
        }

        // Inventory confirmation failed after payment succeeded
        // -> compensate by refunding/cancelling payment
        if (order.getPaymentStatus() == PaymentStatus.SUCCEEDED) {

            order.setOrderStatus(OrderStatus.CANCELLING);

            cancelPayment(order);

            return;
        }

        throw new IllegalStateException(
                "Inventory confirmation failed. Reservation status: "
                        + reservationStatus
                        + ", payment status: "
                        + order.getPaymentStatus()
        );
    }
    @Transactional
    public ReservationStatusResponse cancelOrder(
            CancelReservationRequest request
    ) {

        OrderEntity entity = orderRepository
                .findByIdForUpdate(request.getOrderId())
                .orElseThrow(() ->
                        new OrderNotFoundException(request.getOrderId())
                );

        // Idempotency
        if (entity.getOrderStatus() == OrderStatus.CANCELLED) {
            return ReservationStatusResponse.builder()
                    .orderId(entity.getId())
                    .paymentId(entity.getPaymentID())
                    .reservationId(entity.getReservationId())
                    .status(entity.getOrderStatus())
                    .expiresAt(entity.getExpiresAt())
                    .build();
        }

        if (entity.getOrderStatus() == OrderStatus.CANCELLING) {
            return ReservationStatusResponse.builder()
                    .orderId(entity.getId())
                    .paymentId(entity.getPaymentID())
                    .reservationId(entity.getReservationId())
                    .status(entity.getOrderStatus())
                    .expiresAt(entity.getExpiresAt())
                    .build();
        }

        if (entity.getPaymentStatus() == PaymentStatus.CONFIRMING) {
            throw new IllegalStateException(
                    "Order cannot be cancelled while payment is processing"
            );
        }

        if (entity.getReservationId() == null) {
            throw new IllegalStateException(
                    "No reservation exists for order: " + entity.getId()
            );
        }

        entity.setOrderStatus(OrderStatus.CANCELLING);

        CancelReservationRequest payload = CancelReservationRequest.builder()
                .reservationId(entity.getReservationId())
                .orderId(entity.getId())
                .build();

        outboxEventService.saveEvent(
                entity.getId(),
                "CANCEL_RESERVATION_REQUESTED",
                "order-cancel",
                payload
        );

        return ReservationStatusResponse.builder()
                .orderId(entity.getId())
                .paymentId(entity.getPaymentID())
                .reservationId(entity.getReservationId())
                .status(entity.getOrderStatus())
                .expiresAt(entity.getExpiresAt())
                .build();
    }

    @KafkaListener(topics = "reservation-cancelled")
    @Transactional
    public void cancelOrderUpdate(CancelReservationCompleted response){

        int inserted =
                processedEventRepository.tryMarkProcessed(
                        response.getEventId(),
                        "CANCEL_RESERVATION_COMPLETED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }

        CancelReservationResponse cancelReservationResponse =
                CancelReservationResponse.builder()
                        .orderId(response.getOrderId())
                        .reservationId(response.getReservationId())
                        .status(
                                com.techstore.order.entity.ReservationStatus.valueOf(
                                        response.getStatus().name()
                                )
                        )
                        .build();

        OrderEntity entity = orderRepository
                .findByIdForUpdate(cancelReservationResponse.getOrderId())
                .orElseThrow(() ->
                        new OrderNotFoundException(
                                cancelReservationResponse.getOrderId()
                        )
                );

        // Duplicate event / already completed
        if (entity.getOrderStatus() == OrderStatus.CANCELLED) {
            return;
        }

        if (cancelReservationResponse.getStatus() != ReservationStatus.CANCEL) {
            throw new IllegalStateException(
                    "Inventory reservation cancellation failed. Current status: "
                            + cancelReservationResponse.getStatus()
            );
        }

        // Inventory cancellation succeeded
        for (OrderItemEntity item : entity.getItems()) {

            if (item.getStatus() == OrderItemStatus.RESERVED
                    || item.getStatus() == OrderItemStatus.PENDING) {

                item.setStatus(OrderItemStatus.CANCELLED);
            }
        }

        /*
         * If payment succeeded, customer was charged.
         * We need to refund/cancel payment before the whole order
         * can become CANCELLED.
         */
        if (entity.getPaymentStatus() == PaymentStatus.SUCCEEDED) {

            entity.setOrderStatus(OrderStatus.CANCELLING);

            cancelPayment(entity);

            return;
        }

        /*
         * No successful payment -> nothing needs to be refunded.
         */
        if (entity.getPaymentStatus() == PaymentStatus.PENDING
                || entity.getPaymentStatus() == PaymentStatus.FAILED
                || entity.getPaymentStatus() == PaymentStatus.CANCELLED) {

            entity.setPaymentStatus(PaymentStatus.CANCELLED);
            entity.setOrderStatus(OrderStatus.CANCELLED);

            return;
        }

        /*
         * Payment cancellation already in progress.
         */
        if (entity.getPaymentStatus() == PaymentStatus.CANCELLING) {
            entity.setOrderStatus(OrderStatus.CANCELLING);
            return;
        }

        throw new IllegalStateException(
                "Unable to cancel order. Unexpected payment status: "
                        + entity.getPaymentStatus()
        );
    }

    @Transactional
    public void cancelPayment(OrderEntity entity) {


        // Idempotency
        if (entity.getPaymentStatus() == PaymentStatus.CANCELLED) {
            return;
        }

        if (entity.getPaymentStatus() == PaymentStatus.CANCELLING) {
            return;
        }

        // If payment never succeeded, there is nothing to refund/cancel
        if (entity.getPaymentStatus() == PaymentStatus.PENDING
                || entity.getPaymentStatus() == PaymentStatus.FAILED) {

            entity.setPaymentStatus(PaymentStatus.CANCELLED);
            return;
        }

        // Only a successful payment needs cancellation/refund
        if (entity.getPaymentStatus() != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException(
                    "Unable to cancel payment. Current payment status: "
                            + entity.getPaymentStatus()
            );
        }

        if (entity.getPaymentID() == null) {
            throw new IllegalStateException(
                    "Payment ID is missing for order: " + entity.getId()
            );
        }

        entity.setPaymentStatus(PaymentStatus.CANCELLING);

        CancelPaymentRequest payload = CancelPaymentRequest.builder()
                .paymentId(entity.getPaymentID())
                .orderId(entity.getId())
                .build();

        outboxEventService.saveEvent(
                entity.getId(),
                "CANCEL_PAYMENT_REQUESTED",
                "payment-cancel",
                payload
        );

    }

    @Transactional
    @KafkaListener(topics = "payment-created")
    public void CreatePaymentUpdate(CreatePaymentCompleted response){

        int inserted =
                processedEventRepository.tryMarkProcessed(
                        response.getEventId(),
                        "CREATE_PAYMENT_COMPLETED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }

        CreatePaymentResponse createPaymentResponse = CreatePaymentResponse.builder()
                .paymentId(response.getPaymentId())
                .PaymentMethodId(response.getPaymentMethodId())
                .orderId(response.getOrderId())
                .status(com.techstore.order.entity.PaymentStatus.valueOf(
                        response.getStatus().name()
                ))
                .reservationId(response.getReservationId())
                .build();

        OrderEntity entity = orderRepository.findByIdForUpdate(createPaymentResponse.getOrderId())
                .orElseThrow(()->
                        new OrderNotFoundException(createPaymentResponse.getOrderId())
                       );

        entity.setPaymentID(createPaymentResponse.getPaymentId());
        entity.setPaymentStatus(createPaymentResponse.getStatus());

        orderRepository.save(entity);
    }

    @Transactional
    @KafkaListener(topics ="payment-cancelled")
    public void cancelPaymentUpdate(CancelPaymentCompleted response){
        int inserted =
                processedEventRepository.tryMarkProcessed(
                        response.getEventId(),
                        "CANCEL_PAYMENT_COMPLETED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }
        CancelPaymentResponse cancelPaymentResponse = CancelPaymentResponse.builder()
                .paymentId(response.getPaymentId())
                .orderId(response.getOrderId())
                .reservationId(response.getReservationId())
                .status(com.techstore.order.entity.PaymentStatus.valueOf(
                        response.getStatus().name()
                ))
                .build();

        if (cancelPaymentResponse.getStatus() != PaymentStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Payment cancellation failed. Result: "
                            + cancelPaymentResponse.getStatus()
            );
        }

        OrderEntity entity = orderRepository.findByIdForUpdate(cancelPaymentResponse.getOrderId())
                .orElseThrow(()->
                        new OrderNotFoundException(cancelPaymentResponse.getOrderId())
                );


        // Idempotency
        if (entity.getPaymentStatus() == PaymentStatus.CANCELLED) {
            return;
        }

        if (entity.getPaymentStatus() != PaymentStatus.CANCELLING) {
            throw new IllegalStateException(
                    "Unable to update payment cancellation. Current status: "
                            + entity.getPaymentStatus()
            );
        }


        entity.setPaymentStatus(PaymentStatus.CANCELLED);

        // Cancellation saga is now complete
        entity.setOrderStatus(OrderStatus.CANCELLED);
    }

    private BigDecimal calculateReservedTotal(
            OrderEntity order
    ) {

        return order.getItems()
                .stream()
                .filter(item ->
                        item.getStatus()
                                == OrderItemStatus.RESERVED
                )
                .map(item ->
                        item.getUnitPrice()
                                .multiply(
                                        BigDecimal.valueOf(
                                                item.getQuantity()
                                        )
                                )
                )
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }
    private void updateOrderItemsFromReservation(
            OrderEntity order,
            InventoryReservationResponse reservation
    ) {

        for (OrderItemEntity orderItem : order.getItems()) {

            ReservationItemResponse inventoryItem =
                    reservation.getItems()
                            .stream()
                            .filter(item ->
                                    item.getProductId()
                                            .equals(
                                                    orderItem.getProductId()
                                            )
                            )
                            .findFirst()
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Missing inventory result for product: "
                                                    + orderItem.getProductId()
                                    )
                            );

            switch (inventoryItem.getStatus()) {

                case RESERVED ->
                        orderItem.setStatus(
                                OrderItemStatus.RESERVED
                        );

                case OUT_OF_STOCK,
                        INVENTORY_NOT_FOUND ->
                        orderItem.setStatus(
                                OrderItemStatus.CANCELLED
                        );

                default ->
                        throw new RuntimeException(
                                "Unexpected reservation item status: "
                                        + inventoryItem.getStatus()
                        );
            }
        }
    }
    private void updateOrderStatus(
            OrderEntity order,
            InventoryReservationResponse reservation
    ) {

        switch (reservation.getStatus()) {

            case ACTIVE ->
                    order.setOrderStatus(
                            OrderStatus.PENDING
                    );

            case PARTIAL ->
                    order.setOrderStatus(
                            OrderStatus.PARTIAL
                    );



            default ->
                    throw new RuntimeException(
                            "Unexpected reservation status: "
                                    + reservation.getStatus()
                    );
        }
    }
    private void createInventoryReservation(OrderEntity order) {

        List<ReservationItemRequest> itemList =
                order.getItems()
                        .stream()
                        .map(item ->
                                ReservationItemRequest.builder()
                                        .productId(item.getProductId())
                                        .quantity(item.getQuantity())
                                        .build()
                        )
                        .toList();

        CreateReservationRequest payload = CreateReservationRequest.builder()
                .orderId(order.getId())
                .items(itemList)
                .expiresAt(order.getExpiresAt())
                .build();

        outboxEventService.saveEvent(
                order.getId(),
                "CREATE_RESERVATION_REQUESTED",
                "order-create",
                payload
        );
    }

    public ReservationStatusResponse getOrderById(UUID id){
        OrderEntity entity = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "unable to find order" + id
                ));

        return ReservationStatusResponse.builder()
                .orderId(entity.getId())
                .paymentId(entity.getPaymentID())
                .reservationId(entity.getReservationId())
                .status(entity.getOrderStatus())
                .expiresAt(entity.getExpiresAt())
                .build();
    }




    public List<OrderResponse> getAllOrders() {

        return orderRepository.findAll()
                .stream()
                .map(this::toOrderResponse)
                .toList();
    }

    private OrderResponse toOrderResponse(OrderEntity order) {
        return OrderResponse.builder()
                .id(order.getId())
                .reservationId(order.getReservationId())
                .paymentId(order.getPaymentID())
                .customerId(order.getCustomerId())
                .email(order.getEmail())
                .customerType(order.getCustomerType())
                .status(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .expiresAt(order.getExpiresAt())
                .items(order.getItems()
                        .stream()
                        .map(item -> OrderItemResponse.builder()
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .status(item.getStatus())
                                .build())
                        .toList())
                .build();
    }
}
