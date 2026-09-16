package com.techstore.product.service;

import com.techstore.kafka.order.ProductCheckRequested;
import com.techstore.product.dto.*;
import com.techstore.product.entity.ProductEntity;
import com.techstore.product.exception.ProductNotFoundException;
import com.techstore.product.repository.ProcessedEventRepository;
import com.techstore.product.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final OutboxEventService outboxEventService;
    private final ProcessedEventRepository processedEventRepository;

    public void createProduct(CreateProductRequest request){
        for(ProductEntry entry : request.getItems()){
            ProductEntity product = ProductEntity.builder()
                    .productName(entry.getProductName())
                    .category(entry.getCategory())
                    .description(entry.getDescription())
                    .price(entry.getPrice())
                    .active(entry.getActive())
                    .build();

            productRepository.save(product);

            ProductItemCompletedWrapper(product.getId());
        }

    }

    public void ProductItemCompletedWrapper(Long id){
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

        ProductItemResponse response = ProductItemResponse.builder()
                .productId(product.getId())
                .productName(product.getProductName())
                .description(product.getDescription())
                .category(product.getCategory())
                .price(product.getPrice())
                .active(product.isActive())
                .version(product.getVersion())
                .build();

        outboxEventService.saveEvent(
                UUID.randomUUID(),
                "PRODUCT_ITEM_COMPLETED",
                "product-item-update",
                response
        );
    }

    public List<ProductItemResponse> getAllProducts(){
        return productRepository.findAll().stream()
                .map(product -> ProductItemResponse.builder()
                        .productName(product.getProductName())
                        .productId(product.getId())
                        .description(product.getDescription())
                        .category(product.getCategory())
                        .price(product.getPrice())
                        .active(product.isActive())
                        .version(product.getVersion())
                        .build()
                ).toList();
    }

    @Transactional
    public UpdateStatus updateProduct(Long id, UpdateProductRequest request){
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException("Product not found with id: " + id)
                );

        if (request.getProductName() != null) {
            product.setProductName(request.getProductName());
        }

        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }

        if (request.getCategory() != null) {
            product.setCategory(request.getCategory());
        }

        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }

        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }

        ProductItemCompletedWrapper(product.getId());

        return new UpdateStatus("success");
    }

    @KafkaListener(topics = "query-product")
    public void checkProduct(ProductCheckRequested request){
        System.out.println("hello from kafka listener query-product");

        List<ProductEntry> items = new ArrayList<>();
        for(com.techstore.kafka.order.ProductEntry entry : request.getProducts()){
            ProductEntity product = productRepository.findById(entry.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException("unable to find product"));

            ProductEntry item = ProductEntry.builder()
                    .productId(product.getId())
                    .active(product.isActive())
                    .price(product.getPrice())
                    .productName(product.getProductName())
                    .build();
            items.add(item);
        }

        QueryProductsResponse payload = QueryProductsResponse.builder()
                .productList(items)
                .orderId(UUID.fromString(request.getOrderId().toString()))
                .build();

        outboxEventService.saveEvent(
                UUID.fromString(request.getOrderId().toString()),
                "PRODUCT_CHECK_COMPLETED",
                "product-check",
                payload
        );

    }
}
