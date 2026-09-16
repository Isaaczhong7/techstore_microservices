package com.techstore.product.scheduler;


import com.techstore.product.entity.OutboxEventEntity;
import com.techstore.product.entity.OutboxStatus;
import com.techstore.product.kafka.OutboxEventProducer;
import com.techstore.product.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProducer outboxEventProducer;

    @Scheduled(fixedDelay = 1000)
    public void processOutbox() {

        List<OutboxEventEntity> events =
                outboxEventRepository
                        .findTop100ByStatusOrderByCreatedAtAsc(
                                OutboxStatus.PENDING
                        );

        for (OutboxEventEntity event : events) {

            try {
                outboxEventProducer.publish(event);
            } catch (Exception e) {
                log.error(
                        "Failed to publish outbox event. " +
                                "eventId={}, eventType={}, topic={}, attempts={}",
                        event.getId(),
                        event.getEventType(),
                        event.getTopic(),
                        event.getAttempts(),
                        e
                );
                outboxEventProducer.recordFailure(
                        event.getId()
                );
            }
        }
    }
}