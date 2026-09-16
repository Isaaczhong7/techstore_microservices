package com.techstore.product.service;


import com.techstore.product.entity.OutboxEventEntity;
import com.techstore.product.entity.OutboxStatus;
import com.techstore.product.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

        private final OutboxEventRepository outboxEventRepository;
        private final ObjectMapper objectMapper;

        public void saveEvent(
                UUID aggregateId,
                String eventType,
                String topic,
                Object payload
        ) {

            try {

                String json =
                        objectMapper.writeValueAsString(payload);

                OutboxEventEntity event =
                        OutboxEventEntity.builder()
                                .id(UUID.randomUUID())
                                .aggregateId(aggregateId)
                                .eventType(eventType)
                                .topic(topic)
                                .payload(json)
                                .status(OutboxStatus.PENDING)
                                .createdAt(LocalDateTime.now())
                                .attempts(0)
                                .build();

                outboxEventRepository.save(event);

            } catch (CompletionException e) {

                throw new IllegalStateException(
                        "Unable to serialize outbox event",
                        e
                );
            }
        }
}

