package com.techstore.payment_service.service;


import com.techstore.kafka.order.CancelPaymentRequested;
import com.techstore.kafka.order.CreatePaymentRequested;
import com.techstore.kafka.order.SubmitPaymentRequested;

import com.techstore.payment_service.dto.*;
import com.techstore.payment_service.entity.PaymentEntity;
import com.techstore.payment_service.entity.PaymentStatus;

import com.techstore.payment_service.exception.DuplicatePaymentRequestException;
import com.techstore.payment_service.exception.PaymentCancelErrorException;
import com.techstore.payment_service.exception.PaymentIdNotFoundException;
import com.techstore.payment_service.exception.UnmatchedPaymentMethodException;
import com.techstore.payment_service.repository.PaymentRepository;
import com.techstore.payment_service.repository.ProcessedEventRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventService outboxEventService;


    @KafkaListener(topics = "payment-create")
    @Transactional
    public void createPayment(CreatePaymentRequested request)  {
        int inserted =
                processedEventRepository.tryMarkProcessed(
                        request.getEventId(),
                        "CREATE_PAYMENT_REQUESTED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }

        CreatePaymentRequest createPaymentRequest = CreatePaymentRequest.builder()
                .PaymentMethodId(request.getPaymentMethodId())
                .reservationId(request.getReservationId())
                .orderId(request.getOrderId())
                .amount(request.getAmount())
                .build();

        PaymentEntity paymentEntry = PaymentEntity.builder()
                .orderId(createPaymentRequest.getOrderId())
                .amount(createPaymentRequest.getAmount())
                .status(PaymentStatus.PENDING)
                .paymentMethodId(createPaymentRequest.getPaymentMethodId())
                .createdAt(LocalDateTime.now())
                .reservationId(createPaymentRequest.getReservationId())
                .build();

        paymentRepository.save(paymentEntry);

        CreatePaymentResponse payload = CreatePaymentResponse.builder()
                .paymentId(paymentEntry.getId())
                .reservationId(paymentEntry.getReservationId())
                .status(paymentEntry.getStatus())
                .PaymentMethodId(paymentEntry.getPaymentMethodId())
                .orderId(paymentEntry.getOrderId())
                .build();


        outboxEventService.saveEvent(
                paymentEntry.getOrderId(),
                "CREATE_PAYMENT_COMPLETED",
                "payment-created",
                payload
        );

    }


    @KafkaListener(topics = "payment-submit")
    @Transactional
    public void submitPayment(SubmitPaymentRequested request)  {
        int inserted =
                processedEventRepository.tryMarkProcessed(
                        request.getEventId(),
                        "SUBMIT_PAYMENT_REQUESTED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }
        System.out.println("payment submit recevied in payment");

        SubmitPaymentRequest submitPaymentRequest = SubmitPaymentRequest.builder()
                .paymentMethodId(request.getPaymentMethodId())
                .paymentId(request.getPaymentId())
                .currencyType(com.techstore.payment_service.entity.CurrencyType.valueOf(
                        request.getCurrencyType().name()
                ))
                .build();

        PaymentEntity paymentEntry = paymentRepository.findByIdForUpdate(submitPaymentRequest.getPaymentId())
                        .orElseThrow(() -> new PaymentIdNotFoundException(submitPaymentRequest.getPaymentId()));

        if (paymentEntry.getStatus() == PaymentStatus.SUCCEEDED) {
            return;
        }
        if (paymentEntry.getStatus() != PaymentStatus.PENDING) {
            throw new DuplicatePaymentRequestException("Payment is already completed: "
                    + paymentEntry.getStatus());
        }

        if (!paymentEntry.getPaymentMethodId()
                .equals(request.getPaymentMethodId())) {

            throw new UnmatchedPaymentMethodException(
                    "Current paymentMethodId is: "
                            + request.getPaymentMethodId()
                            + ", but expected: "
                            + paymentEntry.getPaymentMethodId()
            );
        }
        paymentEntry.setCurrency(submitPaymentRequest.getCurrencyType());
        paymentEntry.setStatus(PaymentStatus.SUCCEEDED);
        System.out.println(paymentEntry.getStatus());
        paymentRepository.save(paymentEntry);

        SubmitPaymentResponse payload = SubmitPaymentResponse.builder()
                .orderId(paymentEntry.getOrderId())
                .paymentId(paymentEntry.getId())
                .reservationId(paymentEntry.getReservationId())
                .status(paymentEntry.getStatus())
                .build();

        outboxEventService.saveEvent(
                paymentEntry.getOrderId(),
                "SUBMIT_PAYMENT_COMPLETED",
                "payment-submitted",
                payload

        );

    }

    @KafkaListener(topics = "payment-cancel")
    @Transactional
    public void cancelPayment(CancelPaymentRequested request){
        int inserted =
                processedEventRepository.tryMarkProcessed(
                        request.getEventId(),
                        "CANCEL_PAYMENT_REQUESTED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }
        CancelPaymentRequest cancelPaymentRequest = CancelPaymentRequest.builder()
                .paymentId(request.getPaymentId())
                .orderId(request.getOrderId())
                .build();

        PaymentEntity paymentEntry = paymentRepository.findByIdForUpdate(cancelPaymentRequest.getPaymentId())
                .orElseThrow(() -> new PaymentIdNotFoundException(cancelPaymentRequest.getPaymentId()));


        if (paymentEntry.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new PaymentCancelErrorException("Payment cannot be cancel. Current Status is "
                    + paymentEntry.getStatus());
        }

        paymentEntry.setStatus(PaymentStatus.CANCELLED);

        paymentRepository.save(paymentEntry);

        CancelPaymentResponse payload = CancelPaymentResponse.builder()
                .status(paymentEntry.getStatus())
                .paymentId(paymentEntry.getId())
                .reservationId(paymentEntry.getReservationId())
                .orderId(paymentEntry.getOrderId())
                .build();

        outboxEventService.saveEvent(
                paymentEntry.getOrderId(),
                "CANCEL_PAYMENT_COMPLETED",
                "payment-cancelled",
                payload
        );
    }
}
