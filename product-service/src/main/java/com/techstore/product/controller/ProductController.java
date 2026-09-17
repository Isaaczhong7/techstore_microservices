package com.techstore.product.controller;

import com.techstore.product.dto.*;
import com.techstore.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<ProductItemResponse> createProduct(@RequestBody CreateProductRequest request){
        return productService.createProduct(request);
    }


    @PatchMapping("/{id}/update")
    public UpdateStatus updateProductInfo(@PathVariable UUID id,
                                      @RequestBody UpdateProductRequest request){
        return productService.updateProduct(id,request);
    }

    @GetMapping
    public List<ProductItemResponse> getAllProducts(){
        return productService.getAllProducts();
    }
}
