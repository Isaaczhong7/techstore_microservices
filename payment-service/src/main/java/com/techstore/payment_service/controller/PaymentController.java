package com.techstore.payment_service.controller;

import com.techstore.payment_service.dto.CancelPaymentRequest;
import com.techstore.payment_service.dto.CancelPaymentResponse;
import com.techstore.payment_service.dto.CreatePaymentRequest;
import com.techstore.payment_service.dto.CreatePaymentResponse;
import com.techstore.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

//    @PostMapping("create")
//    public CreatePaymentResponse createPayment(CreatePaymentRequest request) throws InterruptedException {
//        return paymentService.createPayment(request);
//    }
//
//    @PostMapping("cancel")
//    public CancelPaymentResponse cancelPayment(CancelPaymentRequest request){
//        return paymentService.cancelPayment(request);
//    }





}
