package com.techstore.product.controller;

import com.techstore.product.dto.*;
import com.techstore.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createProduct(@RequestBody CreateProductRequest request){
        productService.createProduct(request);
    }


    @PatchMapping("/{id}/update")
    public UpdateStatus createProduct(@PathVariable Long id,
                                      @RequestBody UpdateProductRequest request){
        return productService.updateProduct(id,request);
    }

    @GetMapping
    public List<ProductItemResponse> getAllProducts(){
        return productService.getAllProducts();
    }
}
