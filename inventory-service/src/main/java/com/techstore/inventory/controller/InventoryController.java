package com.techstore.inventory.controller;

import com.techstore.inventory.dto.*;
import com.techstore.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    public void createInventory(@RequestBody List<CreateInventoryRequest> request){
        inventoryService.createInventory(request);
    }

    @PatchMapping("/{id}/update")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void updateSpecificProduct(@PathVariable Long id, @RequestBody UpdateProductRequest request){
        inventoryService.updateSpecificProduct(id, request);
    }



//    @GetMapping("/{id}")
//    public InventoryResponse getInventoryById(@PathVariable Long id){
//        return inventoryService.getInventoryById(id);
//    }

    @GetMapping()
    public List<InventoryItemResponse> getAllInventory(){
        return inventoryService.getAllInventory();
    }


}
