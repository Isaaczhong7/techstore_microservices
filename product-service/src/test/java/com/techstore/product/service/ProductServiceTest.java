package com.techstore.product.service;

import com.techstore.kafka.order.ProductCheckRequested;
import com.techstore.product.dto.CreateProductRequest;
import com.techstore.product.dto.ProductEntry;
import com.techstore.product.dto.ProductItemResponse;
import com.techstore.product.dto.QueryProductsResponse;
import com.techstore.product.dto.UpdateProductRequest;
import com.techstore.product.entity.ItemCategory;
import com.techstore.product.entity.ProductEntity;
import com.techstore.product.exception.ProductNotFoundException;
import com.techstore.product.repository.ProcessedEventRepository;
import com.techstore.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {
    private static final UUID PRODUCT_1 = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID PRODUCT_2 = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID MISSING_PRODUCT = UUID.fromString("00000000-0000-0000-0000-000000000404");

    private ProductRepository productRepository;
    private OutboxEventService outboxEventService;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        outboxEventService = mock(OutboxEventService.class);
        ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
        productService = new ProductService(productRepository, outboxEventService, processedEventRepository);
    }

    @Test
    void createProductPersistsEveryEntryAndPublishesProductItemEvents() {
        ProductEntry laptop = productEntry("Laptop", ItemCategory.LAPTOP, "Fast", "1299.99", true);
        ProductEntry mouse = productEntry("Mouse", ItemCategory.MOUSE, "Precise", "39.99", true);

        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity product = invocation.getArgument(0);
            product.setId(product.getProductName().equals("Laptop") ? PRODUCT_1 : PRODUCT_2);
            product.setVersion(0L);
            return product;
        });
        when(productRepository.findById(PRODUCT_1)).thenReturn(Optional.of(product(PRODUCT_1, "Laptop", ItemCategory.LAPTOP, "Fast", "1299.99", true, 0L)));
        when(productRepository.findById(PRODUCT_2)).thenReturn(Optional.of(product(PRODUCT_2, "Mouse", ItemCategory.MOUSE, "Precise", "39.99", true, 0L)));

        List<ProductItemResponse> created = productService.createProduct(CreateProductRequest.builder().items(List.of(laptop, mouse)).build());

        ArgumentCaptor<ProductEntity> saved = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(ProductEntity::getProductName, ProductEntity::getCategory, ProductEntity::getDescription, ProductEntity::getPrice, ProductEntity::isActive)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Laptop", ItemCategory.LAPTOP, "Fast", new BigDecimal("1299.99"), true),
                        org.assertj.core.groups.Tuple.tuple("Mouse", ItemCategory.MOUSE, "Precise", new BigDecimal("39.99"), true)
                );
        verify(outboxEventService, times(2)).saveEvent(any(UUID.class), eq("PRODUCT_ITEM_COMPLETED"), eq("product-item-update"), any(ProductItemResponse.class));
        assertThat(created).extracting(ProductItemResponse::getProductId).containsExactly(PRODUCT_1, PRODUCT_2);
    }

    @Test
    void productItemCompletedWrapperThrowsWhenProductDoesNotExist() {
        when(productRepository.findById(MISSING_PRODUCT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.ProductItemCompletedWrapper(MISSING_PRODUCT))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found with id: " + MISSING_PRODUCT);

        verify(outboxEventService, never()).saveEvent(any(), any(), any(), any());
    }

    @Test
    void getAllProductsMapsEntitiesToResponses() {
        when(productRepository.findAll()).thenReturn(List.of(
                product(PRODUCT_1, "Laptop", ItemCategory.LAPTOP, "Fast", "1299.99", true, 2L),
                product(PRODUCT_2, "Mouse", ItemCategory.MOUSE, "Precise", "39.99", false, 1L)
        ));

        List<ProductItemResponse> products = productService.getAllProducts();

        assertThat(products)
                .extracting(ProductItemResponse::getProductId, ProductItemResponse::getProductName, ProductItemResponse::getCategory, ProductItemResponse::getActive, ProductItemResponse::getVersion)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_1, "Laptop", ItemCategory.LAPTOP, true, 2L),
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_2, "Mouse", ItemCategory.MOUSE, false, 1L)
                );
    }

    @Test
    void updateProductAppliesOnlyProvidedFieldsAndPublishesUpdate() {
        ProductEntity product = product(PRODUCT_1, "Old", ItemCategory.DESKTOP, "Old desc", "500.00", false, 4L);
        when(productRepository.findById(PRODUCT_1)).thenReturn(Optional.of(product));

        var status = productService.updateProduct(
                PRODUCT_1,
                new UpdateProductRequest("New", null, ItemCategory.LAPTOP, new BigDecimal("699.00"), true)
        );

        assertThat(status.getMessage()).isEqualTo("success");
        assertThat(product.getProductName()).isEqualTo("New");
        assertThat(product.getDescription()).isEqualTo("Old desc");
        assertThat(product.getCategory()).isEqualTo(ItemCategory.LAPTOP);
        assertThat(product.getPrice()).isEqualByComparingTo("699.00");
        assertThat(product.isActive()).isTrue();
        verify(outboxEventService).saveEvent(any(UUID.class), eq("PRODUCT_ITEM_COMPLETED"), eq("product-item-update"), any(ProductItemResponse.class));
    }

    @Test
    void updateProductThrowsWhenProductDoesNotExist() {
        when(productRepository.findById(MISSING_PRODUCT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(MISSING_PRODUCT, new UpdateProductRequest()))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found with id: " + MISSING_PRODUCT);
    }

    @Test
    void checkProductPublishesProductCheckForRequestedProducts() {
        UUID orderId = UUID.randomUUID();
        when(productRepository.findById(PRODUCT_1)).thenReturn(Optional.of(product(PRODUCT_1, "Laptop", ItemCategory.LAPTOP, "Fast", "1299.99", true, 2L)));
        when(productRepository.findById(PRODUCT_2)).thenReturn(Optional.of(product(PRODUCT_2, "Mouse", ItemCategory.MOUSE, "Precise", "39.99", false, 1L)));

        productService.checkProduct(new ProductCheckRequested(
                UUID.randomUUID(),
                orderId.toString(),
                List.of(
                        new com.techstore.kafka.order.ProductEntry(PRODUCT_1, 2L),
                        new com.techstore.kafka.order.ProductEntry(PRODUCT_2, 1L)
                )
        ));

        ArgumentCaptor<QueryProductsResponse> payload = ArgumentCaptor.forClass(QueryProductsResponse.class);
        verify(outboxEventService).saveEvent(eq(orderId), eq("PRODUCT_CHECK_COMPLETED"), eq("product-check"), payload.capture());
        assertThat(payload.getValue().getProductList())
                .extracting(ProductItemResponse::getProductId, ProductItemResponse::getProductName, ProductItemResponse::getActive)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_1, "Laptop", true),
                        org.assertj.core.groups.Tuple.tuple(PRODUCT_2, "Mouse", false)
                );
    }

    @Test
    void checkProductThrowsWhenAnyRequestedProductIsMissing() {
        UUID orderId = UUID.randomUUID();
        when(productRepository.findById(PRODUCT_1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.checkProduct(new ProductCheckRequested(
                UUID.randomUUID(),
                orderId.toString(),
                List.of(new com.techstore.kafka.order.ProductEntry(PRODUCT_1, 2L))
        )))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("unable to find product");

        verify(outboxEventService, never()).saveEvent(any(), any(), any(), any());
    }

    private static ProductEntry productEntry(String name, ItemCategory category, String description, String price, boolean active) {
        return ProductEntry.builder()
                .productName(name)
                .category(category)
                .description(description)
                .price(new BigDecimal(price))
                .active(active)
                .build();
    }

    private static ProductEntity product(UUID id, String name, ItemCategory category, String description, String price, boolean active, Long version) {
        return ProductEntity.builder()
                .id(id)
                .productName(name)
                .category(category)
                .description(description)
                .price(new BigDecimal(price))
                .active(active)
                .version(version)
                .build();
    }
}
