package com.techstore.lookup_service.service;

import com.techstore.kafka.inventory.InventoryItemCompleted;
import com.techstore.kafka.product.ProductItemCompleted;
import com.techstore.lookup_service.dto.InventoryItemResponse;
import com.techstore.lookup_service.dto.ProductItem;
import com.techstore.lookup_service.dto.ProductItemResponse;
import com.techstore.lookup_service.entity.ItemCategory;
import com.techstore.lookup_service.entity.ProductLookupEntity;
import com.techstore.lookup_service.exception.ItemInvalidException;
import com.techstore.lookup_service.repository.ProcessedEventRepository;
import com.techstore.lookup_service.repository.ProductLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
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

class LookupServiceTest {

    private ProductLookupRepository productLookupRepository;
    private RedisTemplate<String, ProductItem> redisTemplate;
    private ValueOperations<String, ProductItem> valueOperations;
    private ProcessedEventRepository processedEventRepository;
    private LookupService lookupService;

    @BeforeEach
    void setUp() {
        productLookupRepository = mock(ProductLookupRepository.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lookupService = new LookupService(
                productLookupRepository,
                mock(OutboxEventService.class),
                mock(RestClient.class),
                mock(RestClient.class),
                redisTemplate,
                processedEventRepository
        );
    }

    @Test
    void saveOrUpdateProductSnapshotCreatesNewSnapshotWithProductAndInventoryState() {
        when(productLookupRepository.findById(10L)).thenReturn(Optional.empty());

        lookupService.saveOrUpdateProductSnapshot(
                productResponse(10L, "Laptop", 2L),
                inventoryResponse(10L, 8L, 1L, 3L)
        );

        var saved = org.mockito.ArgumentCaptor.forClass(ProductLookupEntity.class);
        verify(productLookupRepository).save(saved.capture());
        assertThat(saved.getValue().getProductId()).isEqualTo(10L);
        assertThat(saved.getValue().getProductName()).isEqualTo("Laptop");
        assertThat(saved.getValue().getProductVersion()).isEqualTo(2L);
        assertThat(saved.getValue().getQuantity()).isEqualTo(8L);
        assertThat(saved.getValue().getItemSold()).isEqualTo(1L);
        assertThat(saved.getValue().getInventoryVersion()).isEqualTo(3L);
    }

    @Test
    void saveOrUpdateProductSnapshotIgnoresOlderVersions() {
        ProductLookupEntity existing = entity(10L, "Current", 5L, 4L);
        existing.setQuantity(10L);
        existing.setItemSold(1L);
        when(productLookupRepository.findById(10L)).thenReturn(Optional.of(existing));

        lookupService.saveOrUpdateProductSnapshot(
                productResponse(10L, "Old", 3L),
                inventoryResponse(10L, 99L, 99L, 2L)
        );

        assertThat(existing.getProductName()).isEqualTo("Current");
        assertThat(existing.getProductVersion()).isEqualTo(5L);
        assertThat(existing.getQuantity()).isEqualTo(10L);
        assertThat(existing.getItemSold()).isEqualTo(1L);
        assertThat(existing.getInventoryVersion()).isEqualTo(4L);
        verify(productLookupRepository).save(existing);
    }

    @Test
    void fetchItemsRejectsInvalidPageAndSize() {
        assertThatThrownBy(() -> lookupService.fetchItems(-1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page cannot be negative");
        assertThatThrownBy(() -> lookupService.fetchItems(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Size must be between 1 and 100");
        assertThatThrownBy(() -> lookupService.fetchItems(0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Size must be between 1 and 100");
    }

    @Test
    void fetchItemsMapsRepositoryPageToProductItems() {
        ProductLookupEntity entity = entity(10L, "Laptop", 2L, 3L);
        entity.setQuantity(8L);
        entity.setItemSold(1L);
        when(productLookupRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<ProductItem> page = lookupService.fetchItems(0, 10);

        assertThat(page.getContent())
                .extracting(ProductItem::getProductId, ProductItem::getProductName, ProductItem::getQuantity, ProductItem::getItemSold)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(10L, "Laptop", 8L, 1L));
    }

    @Test
    void getProductReturnsCachedProductWithoutRepositoryLookup() {
        ProductItem cached = productItem(10L, "Cached");
        when(valueOperations.get("product:10")).thenReturn(cached);

        ProductItem product = lookupService.getProduct(10L);

        assertThat(product).isSameAs(cached);
        verifyNoInteractions(productLookupRepository);
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void getProductLoadsFromRepositoryAndCachesOnMiss() {
        ProductLookupEntity entity = entity(10L, "Laptop", 2L, 3L);
        entity.setQuantity(8L);
        entity.setItemSold(1L);
        when(valueOperations.get("product:10")).thenReturn(null);
        when(productLookupRepository.findById(10L)).thenReturn(Optional.of(entity));

        ProductItem product = lookupService.getProduct(10L);

        assertThat(product.getProductName()).isEqualTo("Laptop");
        assertThat(product.getQuantity()).isEqualTo(8L);
        verify(valueOperations).set(eq("product:10"), eq(product), eq(Duration.ofMinutes(10)));
    }

    @Test
    void getProductThrowsWhenProductDoesNotExist() {
        when(valueOperations.get("product:404")).thenReturn(null);
        when(productLookupRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lookupService.getProduct(404L))
                .isInstanceOf(ItemInvalidException.class)
                .hasMessage("Unable to find product: 404");
    }

    @Test
    void inventoryItemUpdateIgnoresDuplicateEvents() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.tryMarkProcessed(eventId, "INVENTORY_ITEM_COMPLETED")).thenReturn(0);

        lookupService.inventoryItemUpdate(new InventoryItemCompleted(eventId, 10L, 2L, 8L, 3L));

        verifyNoInteractions(productLookupRepository);
        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void inventoryItemUpdateUpsertsAndDeletesCacheAfterCommit() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.tryMarkProcessed(eventId, "INVENTORY_ITEM_COMPLETED")).thenReturn(1);
        when(productLookupRepository.upsertInventory(10L, 8L, 2L, 3L)).thenReturn(1);

        runWithTransactionSynchronization(() ->
                lookupService.inventoryItemUpdate(new InventoryItemCompleted(eventId, 10L, 2L, 8L, 3L))
        );

        verify(redisTemplate).delete("product:10");
    }

    @Test
    void productItemUpdateUpsertsAndDeletesCacheAfterCommit() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.tryMarkProcessed(eventId, "PRODUCT_ITEM_COMPLETED")).thenReturn(1);
        when(productLookupRepository.upsertProduct(
                eq(10L),
                eq("Laptop"),
                eq("Fast"),
                eq("LAPTOP"),
                eq(new BigDecimal("1299.99")),
                eq(true),
                eq(4L)
        )).thenReturn(1);

        runWithTransactionSynchronization(() ->
                lookupService.productItemUpdate(new ProductItemCompleted(
                        eventId,
                        10L,
                        "Laptop",
                        "Fast",
                        com.techstore.kafka.product.ItemCategory.LAPTOP,
                        new BigDecimal("1299.99"),
                        true,
                        4L
                ))
        );

        verify(redisTemplate).delete("product:10");
    }

    @Test
    void productItemUpdateDoesNotDeleteCacheWhenNoRowChanged() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.tryMarkProcessed(eventId, "PRODUCT_ITEM_COMPLETED")).thenReturn(1);
        when(productLookupRepository.upsertProduct(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        lookupService.productItemUpdate(new ProductItemCompleted(
                eventId,
                10L,
                "Laptop",
                "Fast",
                com.techstore.kafka.product.ItemCategory.LAPTOP,
                new BigDecimal("1299.99"),
                true,
                4L
        ));

        verify(redisTemplate, never()).delete(any(String.class));
    }

    private static void runWithTransactionSynchronization(Runnable runnable) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            runnable.run();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static ProductItemResponse productResponse(Long productId, String name, Long version) {
        return ProductItemResponse.builder()
                .productId(productId)
                .productName(name)
                .description("Fast")
                .category(ItemCategory.LAPTOP)
                .price(new BigDecimal("1299.99"))
                .active(true)
                .version(version)
                .build();
    }

    private static InventoryItemResponse inventoryResponse(Long productId, Long quantity, Long itemSold, Long version) {
        return InventoryItemResponse.builder()
                .productId(productId)
                .quantity(quantity)
                .itemSold(itemSold)
                .version(version)
                .build();
    }

    private static ProductLookupEntity entity(Long productId, String name, Long productVersion, Long inventoryVersion) {
        return ProductLookupEntity.builder()
                .productId(productId)
                .productName(name)
                .description("Fast")
                .category(ItemCategory.LAPTOP)
                .price(new BigDecimal("1299.99"))
                .active(true)
                .productVersion(productVersion)
                .inventoryVersion(inventoryVersion)
                .build();
    }

    private static ProductItem productItem(Long productId, String name) {
        return ProductItem.builder()
                .productId(productId)
                .productName(name)
                .description("Fast")
                .category(ItemCategory.LAPTOP)
                .price(new BigDecimal("1299.99"))
                .active(true)
                .quantity(8L)
                .itemSold(1L)
                .build();
    }
}
