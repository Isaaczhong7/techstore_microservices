package com.techstore.order.controller;

import com.techstore.order.dto.*;
import com.techstore.order.service.OrderService;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public ReservationStatusResponse createOrder(@RequestBody CreateOrderRequest request) {
       return orderService.createOrder(request);
    }

//    @PostMapping("/confirm")
//    public ReservationStatusResponse submitOder(@RequestBody ConfirmReservationRequest request){
//        return orderService.confirmOrder(request);
//    }

    @PostMapping("/cancel")
    public ReservationStatusResponse cancelTransaction(@RequestBody CancelReservationRequest request){
        return orderService.cancelOrder(request);
    }

    @PostMapping("/payment")
    public ReservationStatusResponse submitPayment(@RequestBody SubmitPaymentRequest request){
        return orderService.submitPayment(request);
    }


    @GetMapping("/{id}")
    public ReservationStatusResponse getOrderById(@PathVariable UUID id) {
        return orderService.getOrderById(id);

    }

    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }
}
