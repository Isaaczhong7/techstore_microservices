package com.techstore.payment_service.kafka;

import com.techstore.kafka.payment.CancelPaymentCompleted;
import com.techstore.kafka.payment.CreatePaymentCompleted;
import com.techstore.kafka.payment.SubmitPaymentCompleted;
import com.techstore.payment_service.dto.CancelPaymentResponse;
import com.techstore.payment_service.dto.CreatePaymentResponse;
import com.techstore.payment_service.dto.SubmitPaymentResponse;
import com.techstore.payment_service.entity.OutboxEventEntity;
import com.techstore.payment_service.entity.OutboxStatus;
import com.techstore.payment_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import java.util.UUID;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class OutboxEventProducer {

    private final OutboxEventRepository outboxEventRepository;
    private final CancelPaymentProducer cancelPaymentProducer;
    private final SubmitPaymentProducer submitPaymentProducer;
    private final CreatePaymentProducer createPaymentProducer;
    private final ObjectMapper objectMapper;

        @Transactional
        public void publish(OutboxEventEntity event) {

            switch (event.getEventType()) {

                case "CREATE_PAYMENT_COMPLETED" -> publishCreatePaymentCompleted(event);

                case "SUBMIT_PAYMENT_COMPLETED" -> publishSubmitPaymentCompleted(event);

                case "CANCEL_PAYMENT_COMPLETED" -> publishCancelPaymentCompleted(event);

                default -> throw new IllegalArgumentException(
                        "Unknown outbox event type: "
                                + event.getEventType()
                );
            }

            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());

            outboxEventRepository.save(event);
        }


    private void publishCreatePaymentCompleted(OutboxEventEntity event){
        CreatePaymentResponse payload;
        try {
            payload = objectMapper.readValue(
                    event.getPayload(),
                    CreatePaymentResponse.class
            );
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Unable to deserialize outbox event "
                            + event.getId(),
                    e
            );
        }

        CreatePaymentCompleted response = CreatePaymentCompleted.newBuilder()
                .setEventId(event.getId())
                .setPaymentMethodId(payload.getPaymentMethodId())
                .setPaymentId(payload.getPaymentId())
                .setOrderId(payload.getOrderId())
                .setStatus(com.techstore.kafka.payment.PaymentStatus.valueOf(
                        payload.getStatus().name()
                ))
                .setReservationId(payload.getReservationId())
                .build();


        try{
            createPaymentProducer.publish(response)
                    .join();

        }catch(CompletionException e) {

            throw new IllegalStateException(
                    "Failed to publish crete payment completed outbox event "
                            + event.getId(),
                    e
            );

        }

    }

    public void publishSubmitPaymentCompleted(OutboxEventEntity event){
        SubmitPaymentResponse payload;
        try {
            payload = objectMapper.readValue(
                    event.getPayload(),
                    SubmitPaymentResponse.class
            );
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Unable to deserialize outbox event "
                            + event.getId(),
                    e
            );
        }
        SubmitPaymentCompleted response = SubmitPaymentCompleted.newBuilder()
                .setEventId(event.getId())
                .setPaymentId(payload.getPaymentId())
                .setReservationId(payload.getReservationId())

                .setStatus(
                        com.techstore.kafka.payment.PaymentStatus.valueOf(
                                payload.getStatus().name()
                        ))
                .setOrderId(payload.getOrderId())
                .build();

        try{
            submitPaymentProducer.publish(response)
                    .join();
        }catch(CompletionException e) {

            throw new IllegalStateException(
                    "Failed to publish submit payment completed outbox event "
                            + event.getId(),
                    e
            );

        }
    }

    public void publishCancelPaymentCompleted(OutboxEventEntity event){
        CancelPaymentResponse payload;
        try {
            payload = objectMapper.readValue(
                    event.getPayload(),
                    CancelPaymentResponse.class
            );
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Unable to deserialize outbox event "
                            + event.getId(),
                    e
            );
        }

        CancelPaymentCompleted response = CancelPaymentCompleted.newBuilder()
                .setEventId(event.getId())
                .setPaymentId(payload.getPaymentId())
                .setOrderId(payload.getOrderId())
                .setReservationId(payload.getReservationId())
                .setStatus(com.techstore.kafka.payment.PaymentStatus.valueOf(
                        payload.getStatus().name()
                ))
                .build();


        try{
            cancelPaymentProducer.publish(response)
                    .join();

        }catch(CompletionException e) {

            throw new IllegalStateException(
                    "Failed to publish cancel payment completed outbox event "
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
