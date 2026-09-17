package com.techstore.lookup_service.repository;

import com.techstore.lookup_service.entity.ItemCategory;
import com.techstore.lookup_service.entity.ProductLookupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface ProductLookupRepository extends JpaRepository<ProductLookupEntity, UUID> {
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query(value = """
    INSERT INTO product_lookup (
        product_id,
        quantity,
        item_sold,
        inventory_version,
        updated_at
    )
    VALUES (
        :productId,
        :quantity,
        :itemSold,
        :version,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (product_id)
    DO UPDATE SET
        quantity = EXCLUDED.quantity,
        item_sold = EXCLUDED.item_sold,
        inventory_version = EXCLUDED.inventory_version,
        updated_at = CURRENT_TIMESTAMP
    WHERE product_lookup.inventory_version IS NULL
       OR product_lookup.inventory_version < EXCLUDED.inventory_version
    """, nativeQuery = true)
    int upsertInventory(
            @Param("productId") UUID productId,
            @Param("quantity") Long quantity,
            @Param("itemSold") Long itemSold,
            @Param("version") Long version
    );

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query(value = """
        INSERT INTO product_lookup (
            product_id,
            product_name,
            description,
            category,
            price,
            active,
            product_version,
            updated_at
        )
        VALUES (
            :productId,
            :productName,
            :description,
            :category,
            :price,
            :active,
            :version,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (product_id)
        DO UPDATE SET
            product_name = EXCLUDED.product_name,
            description = EXCLUDED.description,
            category = EXCLUDED.category,
            price = EXCLUDED.price,
            active = EXCLUDED.active,
            product_version = EXCLUDED.product_version,
            updated_at = CURRENT_TIMESTAMP
        WHERE product_lookup.product_version IS NULL
           OR product_lookup.product_version < EXCLUDED.product_version
        """, nativeQuery = true)
    int upsertProduct(
            @Param("productId") UUID productId,
            @Param("productName") String productName,
            @Param("description") String description,
            @Param("category") String category,
            @Param("price") BigDecimal price,
            @Param("active") Boolean active,
            @Param("version") Long version
    );

}
