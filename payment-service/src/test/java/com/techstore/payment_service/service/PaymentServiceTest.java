package com.techstore.payment_service.service;

import com.techstore.kafka.order.CancelPaymentRequested;
import com.techstore.kafka.order.CreatePaymentRequested;
import com.techstore.kafka.order.SubmitPaymentRequested;
import com.techstore.payment_service.dto.CancelPaymentResponse;
import com.techstore.payment_service.dto.CreatePaymentResponse;
import com.techstore.payment_service.dto.SubmitPaymentResponse;
import com.techstore.payment_service.entity.CurrencyType;
import com.techstore.payment_service.entity.PaymentEntity;
import com.techstore.payment_service.entity.PaymentStatus;
import com.techstore.payment_service.exception.DuplicatePaymentRequestException;
import com.techstore.payment_service.exception.PaymentCancelErrorException;
import com.techstore.payment_service.exception.PaymentIdNotFoundException;
import com.techstore.payment_service.exception.UnmatchedPaymentMethodException;
import com.techstore.payment_service.repository.PaymentRepository;
import com.techstore.payment_service.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private ProcessedEventRepository processedEventRepository;
    private OutboxEventService outboxEventService;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        outboxEventService = mock(OutboxEventService.class);
        paymentService = new PaymentService(paymentRepository, processedEventRepository, outboxEventService);
    }

    @Test
    void createPaymentPersistsPendingPaymentAndPublishesCompletion() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        when(processedEventRepository.tryMarkProcessed(eventId, "CREATE_PAYMENT_REQUESTED")).thenReturn(1);
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> {
            PaymentEntity payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            return payment;
        });

        paymentService.createPayment(new CreatePaymentRequested(eventId, orderId, reservationId, new BigDecimal("42.50"), methodId));

        ArgumentCaptor<PaymentEntity> saved = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(saved.getValue().getReservationId()).isEqualTo(reservationId);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("42.50");
        assertThat(saved.getValue().getPaymentMethodId()).isEqualTo(methodId);
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);

        ArgumentCaptor<CreatePaymentResponse> payload = ArgumentCaptor.forClass(CreatePaymentResponse.class);
        verify(outboxEventService).saveEvent(eq(orderId), eq("CREATE_PAYMENT_COMPLETED"), eq("payment-created"), payload.capture());
        assertThat(payload.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payload.getValue().getPaymentMethodId()).isEqualTo(methodId);
    }

    @Test
    void createPaymentIgnoresDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.tryMarkProcessed(eventId, "CREATE_PAYMENT_REQUESTED")).thenReturn(0);

        paymentService.createPayment(new CreatePaymentRequested(eventId, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, UUID.randomUUID()));

        verifyNoInteractions(paymentRepository, outboxEventService);
    }

    @Test
    void submitPaymentMarksPendingPaymentSucceededAndPublishesCompletion() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        PaymentEntity payment = payment(paymentId, PaymentStatus.PENDING, methodId);
        when(processedEventRepository.tryMarkProcessed(eventId, "SUBMIT_PAYMENT_REQUESTED")).thenReturn(1);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        paymentService.submitPayment(new SubmitPaymentRequested(
                eventId,
                payment.getOrderId(),
                paymentId,
                com.techstore.kafka.order.CurrencyType.USD,
                methodId
        ));

        assertThat(payment.getCurrency()).isEqualTo(CurrencyType.USD);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository).save(payment);
        verify(outboxEventService).saveEvent(eq(payment.getOrderId()), eq("SUBMIT_PAYMENT_COMPLETED"), eq("payment-submitted"), any(SubmitPaymentResponse.class));
    }

    @Test
    void submitPaymentReturnsWithoutPublishingWhenAlreadySucceeded() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        PaymentEntity payment = payment(paymentId, PaymentStatus.SUCCEEDED, methodId);
        when(processedEventRepository.tryMarkProcessed(eventId, "SUBMIT_PAYMENT_REQUESTED")).thenReturn(1);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        paymentService.submitPayment(new SubmitPaymentRequested(eventId, payment.getOrderId(), paymentId, com.techstore.kafka.order.CurrencyType.USD, methodId));

        verify(paymentRepository, never()).save(any());
        verify(outboxEventService, never()).saveEvent(any(), any(), any(), any());
    }

    @Test
    void submitPaymentThrowsWhenPaymentIsNotPending() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        PaymentEntity payment = payment(paymentId, PaymentStatus.CANCELLED, methodId);
        when(processedEventRepository.tryMarkProcessed(eventId, "SUBMIT_PAYMENT_REQUESTED")).thenReturn(1);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.submitPayment(new SubmitPaymentRequested(eventId, payment.getOrderId(), paymentId, com.techstore.kafka.order.CurrencyType.USD, methodId)))
                .isInstanceOf(DuplicatePaymentRequestException.class)
                .hasMessageContaining("Payment is already completed");
    }

    @Test
    void submitPaymentThrowsWhenPaymentMethodDoesNotMatch() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentEntity payment = payment(paymentId, PaymentStatus.PENDING, UUID.randomUUID());
        when(processedEventRepository.tryMarkProcessed(eventId, "SUBMIT_PAYMENT_REQUESTED")).thenReturn(1);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.submitPayment(new SubmitPaymentRequested(eventId, payment.getOrderId(), paymentId, com.techstore.kafka.order.CurrencyType.USD, UUID.randomUUID())))
                .isInstanceOf(UnmatchedPaymentMethodException.class)
                .hasMessageContaining("but expected");
    }

    @Test
    void submitPaymentIgnoresDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.tryMarkProcessed(eventId, "SUBMIT_PAYMENT_REQUESTED")).thenReturn(0);

        paymentService.submitPayment(new SubmitPaymentRequested(eventId, UUID.randomUUID(), UUID.randomUUID(), com.techstore.kafka.order.CurrencyType.USD, UUID.randomUUID()));

        verifyNoInteractions(paymentRepository, outboxEventService);
    }

    @Test
    void cancelPaymentCancelsSucceededPaymentAndPublishesCompletion() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentEntity payment = payment(paymentId, PaymentStatus.SUCCEEDED, UUID.randomUUID());
        when(processedEventRepository.tryMarkProcessed(eventId, "CANCEL_PAYMENT_REQUESTED")).thenReturn(1);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        paymentService.cancelPayment(new CancelPaymentRequested(eventId, paymentId, payment.getOrderId()));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentRepository).save(payment);
        verify(outboxEventService).saveEvent(eq(payment.getOrderId()), eq("CANCEL_PAYMENT_COMPLETED"), eq("payment-cancelled"), any(CancelPaymentResponse.class));
    }

    @Test
    void cancelPaymentThrowsWhenPaymentIsNotSucceeded() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentEntity payment = payment(paymentId, PaymentStatus.PENDING, UUID.randomUUID());
        when(processedEventRepository.tryMarkProcessed(eventId, "CANCEL_PAYMENT_REQUESTED")).thenReturn(1);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancelPayment(new CancelPaymentRequested(eventId, paymentId, payment.getOrderId())))
                .isInstanceOf(PaymentCancelErrorException.class)
                .hasMessageContaining("Payment cannot be cancel");
    }

    @Test
    void cancelPaymentThrowsWhenPaymentDoesNotExist() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(processedEventRepository.tryMarkProcessed(eventId, "CANCEL_PAYMENT_REQUESTED")).thenReturn(1);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.cancelPayment(new CancelPaymentRequested(eventId, paymentId, UUID.randomUUID())))
                .isInstanceOf(PaymentIdNotFoundException.class);
    }

    private static PaymentEntity payment(UUID paymentId, PaymentStatus status, UUID methodId) {
        return PaymentEntity.builder()
                .id(paymentId)
                .orderId(UUID.randomUUID())
                .reservationId(UUID.randomUUID())
                .amount(new BigDecimal("42.50"))
                .status(status)
                .paymentMethodId(methodId)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
