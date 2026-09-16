package com.techstore.lookup_service.service;

import com.techstore.kafka.inventory.InventoryItemCompleted;
import com.techstore.kafka.product.ProductItemCompleted;
import com.techstore.lookup_service.config.RestClientConfig;
import com.techstore.lookup_service.dto.InventoryItemResponse;
import com.techstore.lookup_service.dto.ProductItem;
import com.techstore.lookup_service.dto.ProductItemResponse;
import com.techstore.lookup_service.entity.ItemCategory;
import com.techstore.lookup_service.entity.ProductLookupEntity;
import com.techstore.lookup_service.exception.ItemInvalidException;
import com.techstore.lookup_service.repository.ProcessedEventRepository;
import com.techstore.lookup_service.repository.ProductLookupRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class LookupService {
    private static final String PRODUCT_CACHE_PREFIX = "product:";
    private final ProductLookupRepository productLookupRepository;
    private final OutboxEventService outboxEventService;
    private final RestClient productRestClient;
    private final RestClient inventoryRestClient;
    private final RedisTemplate<String, ProductItem> redisTemplate;
    private final ProcessedEventRepository processedEventRepository;


    @Transactional
    public void fetchAllProducts() {

        List<ProductItemResponse> products = fetchProductSnapshot();
        List<InventoryItemResponse> inventory = fetchInventorySnapshot();

        Map<Long, InventoryItemResponse> inventoryByProductId =
                inventory.stream()
                        .collect(Collectors.toMap(
                                InventoryItemResponse::getProductId,
                                Function.identity()
                        ));

        for (ProductItemResponse product : products) {

            InventoryItemResponse inventoryItem =
                    inventoryByProductId.get(product.getProductId());

            saveOrUpdateProductSnapshot(
                    product,
                    inventoryItem
            );
        }

        log.info(
                "Lookup bootstrap completed with {} products",
                products.size()
        );
    }

    private List<ProductItemResponse> fetchProductSnapshot() {

        List<ProductItemResponse> products =
                productRestClient.get()
                        .uri("/api/products")
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        List<ProductItemResponse>>() {}
                        );

        if (products == null) {
            throw new IllegalStateException(
                    "Product service returned null"
            );
        }

        return products;
    }
    private List<InventoryItemResponse> fetchInventorySnapshot() {

        List<InventoryItemResponse> inventoryItems =
                inventoryRestClient.get()
                        .uri("/api/inventory")
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        List<InventoryItemResponse>>() {}
                        );

        if (inventoryItems == null) {
            throw new IllegalStateException(
                    "Inventory service returned null"
            );
        }

        return inventoryItems;
    }

    @Transactional
    public void saveOrUpdateProductSnapshot(
            ProductItemResponse product,
            InventoryItemResponse inventory
    ) {

        ProductLookupEntity entity =
                productLookupRepository
                        .findById(product.getProductId())
                        .orElseGet(() ->
                                ProductLookupEntity.builder()
                                        .productId(
                                                product.getProductId()
                                        )
                                        .build()
                        );

        /*
         * Product state
         */
        if (entity.getProductVersion() == null
                || product.getVersion()
                > entity.getProductVersion()) {

            entity.setProductName(
                    product.getProductName()
            );

            entity.setDescription(
                    product.getDescription()
            );

            entity.setCategory(
                    product.getCategory()
            );

            entity.setPrice(
                    product.getPrice()
            );

            entity.setActive(
                    product.getActive()
            );

            entity.setProductVersion(
                    product.getVersion()
            );
        }

        /*
         * Inventory state
         */
        if (inventory != null &&
                (entity.getInventoryVersion() == null
                        || inventory.getVersion()
                        > entity.getInventoryVersion())) {

            entity.setItemSold(
                    inventory.getItemSold()
            );

            entity.setQuantity(
                    inventory.getQuantity()
            );

            entity.setInventoryVersion(
                    inventory.getVersion()
            );
        }

        productLookupRepository.save(entity);
    }

    public Page<ProductItem> fetchItems(
            int page,
            int size
    ) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page cannot be negative"
            );
        }

        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException(
                    "Size must be between 1 and 100"
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("productId").ascending()
        );

        return productLookupRepository
                .findAll(pageable)
                .map(this::toProductItem);
    }
    private ProductItem toProductItem(
            ProductLookupEntity entity
    ) {

        return ProductItem.builder()
                .productId(entity.getProductId())
                .productName(entity.getProductName())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .price(entity.getPrice())
                .active(entity.getActive())
                .itemSold(entity.getItemSold())
                .quantity(entity.getQuantity())
                .build();
    }




    @Transactional
    public ProductItem getProduct(Long productId) {

        String key = PRODUCT_CACHE_PREFIX + productId;

        // 1. Check Redis
        ProductItem cachedProduct =
                redisTemplate.opsForValue().get(key);

        if (cachedProduct != null) {
            return cachedProduct;
        }

        // 2. Redis miss -> fetch from Lookup PostgreSQL
        ProductLookupEntity entity =
                productLookupRepository.findById(productId)
                        .orElseThrow(() ->
                                new ItemInvalidException(
                                        "Unable to find product: " + productId
                                )
                        );

                log.debug(
                "Redis cache miss for product {}",
                productId
                );




        ProductItem product = ProductItem.builder()
                .productId(entity.getProductId())
                .productName(entity.getProductName())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .price(entity.getPrice())
                .active(entity.getActive())
                .itemSold(entity.getItemSold())
                .quantity(entity.getQuantity())
                .build();

        // 3. Store in Redis
        redisTemplate.opsForValue().set(
                key,
                product,
                Duration.ofMinutes(10)
        );

        return product;
    }

    @Transactional
    @KafkaListener(topics = "inventory-item-update")
    public void inventoryItemUpdate(InventoryItemCompleted update) {


        int inserted =
                processedEventRepository.tryMarkProcessed(
                        update.getEventId(),
                        "INVENTORY_ITEM_COMPLETED"
                );

        if (inserted == 0) {
            return;
        }

        int changed =
                productLookupRepository.upsertInventory(
                        update.getProductId(),
                        update.getQuantity(),
                        update.getItemSold(),
                        update.getVersion()
                );

        if (changed > 0) {
            deleteRedisAfterCommit(update.getProductId());
        }
    }

    @Transactional
    @KafkaListener(topics = "product-item-update")
    public void productItemUpdate(ProductItemCompleted update) {

        int inserted =
                processedEventRepository.tryMarkProcessed(
                        update.getEventId(),
                        "PRODUCT_ITEM_COMPLETED"
                );

        // Duplicate Kafka event
        if (inserted == 0) {
            return;
        }

        int updated =
                productLookupRepository.upsertProduct(
                        update.getProductId(),
                        update.getProductName(),
                        update.getDescription(),
                        update.getCategory().name(),
                        update.getPrice(),
                        update.getActive(),
                        update.getVersion()
                );

        if (updated > 0) {
            deleteRedisAfterCommit(update.getProductId());
        }
    }

    private void deleteRedisAfterCommit(Long productId) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        redisTemplate.delete(
                                PRODUCT_CACHE_PREFIX + productId
                        );
                    }
                }
        );
    }
}
