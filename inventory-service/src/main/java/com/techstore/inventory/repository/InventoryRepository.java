package com.techstore.inventory.repository;

import com.techstore.inventory.entity.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;


public interface InventoryRepository extends JpaRepository<InventoryEntity, UUID> {
    Optional<InventoryEntity> findByProductId(Long productId);

    @Modifying(

            flushAutomatically = true
    )
    @Query("""
        UPDATE InventoryEntity i
        SET i.reservedQuantity = i.reservedQuantity + :quantity,
            i.version = i.version + 1
        WHERE i.productId = :productId
          AND (i.quantity - i.reservedQuantity) >= :quantity
    """)
    int reserveIfAvailable(
            @Param("productId") Long productId,
            @Param("quantity") Long quantity
    );

    @Modifying( flushAutomatically = true)
    @Query("""
        UPDATE InventoryEntity i
        SET i.reservedQuantity = i.reservedQuantity - :quantity,
            i.version = i.version + 1
        WHERE i.productId = :productId
          AND i.reservedQuantity >= :quantity
    """)
    int releaseReserved(
            @Param("productId") Long productId,
            @Param("quantity") Long quantity
    );


    @Modifying( flushAutomatically = true)
    @Query("""
        UPDATE InventoryEntity i
        SET i.quantity = i.quantity - :quantity,
            i.reservedQuantity = i.reservedQuantity - :quantity,
            i.itemSold = i.itemSold + :quantity,
            i.version = i.version + 1
        WHERE i.productId = :productId
          AND i.reservedQuantity >= :quantity
          AND i.quantity >= :quantity
    """)
    int confirmReserved(
            @Param("productId") Long productId,
            @Param("quantity") Long quantity
    );

    @Modifying( flushAutomatically = true)
    @Query("""
        UPDATE InventoryEntity i
        SET i.quantity = i.quantity + :quantity,
        i.itemSold = i.itemSold - :quantity,
        i.version = i.version + 1
        WHERE i.productId = :productId
        AND i.itemSold >= :quantity
    """)
    int reverseQuantity(
            @Param("productId") Long productId,
            @Param("quantity") Long quantity
    );

    @Modifying( flushAutomatically = true)
    @Query("""
        UPDATE InventoryEntity i
        SET i.quantity = i.quantity + :quantity,
            i.version = i.version + 1
        WHERE i.productId = :productId
    """)
    int addQuantity(
            @Param("productId") Long productId,
            @Param("quantity") Long quantity
    );


}
