package com.techstore.lookup_service.service;

import com.techstore.lookup_service.entity.OutboxStatus;
import com.techstore.lookup_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import com.techstore.lookup_service.entity.OutboxEventEntity;
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

