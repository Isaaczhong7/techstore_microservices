package com.techstore.inventory.scheduler;


import com.techstore.inventory.entity.InventoryReservationEntity;
import com.techstore.inventory.entity.ReservationStatus;
import com.techstore.inventory.repository.InventoryReservationRepository;
import com.techstore.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReservationExpirationScheduler {

    private final InventoryReservationRepository
            inventoryReservationRepository;

    private final InventoryService inventoryService;

    @Scheduled(fixedDelay = 5000)
    public void expireReservations() {

        List<InventoryReservationEntity> expiredReservations =
                inventoryReservationRepository
                        .findByStatusInAndExpiresAtBefore(
                                List.of(
                                        ReservationStatus.ACTIVE,
                                        ReservationStatus.PARTIAL
                                ),
                                LocalDateTime.now()
                        );

        for (InventoryReservationEntity reservation :
                expiredReservations) {

            inventoryService.expireReservation(
                    reservation.getId()
            );
        }
    }
}

