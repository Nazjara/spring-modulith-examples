package com.nazjara.brewery.inventory.web;

import com.nazjara.brewery.inventory.InventoryService;
import com.nazjara.brewery.inventory.StockLevel;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints of the inventory module.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory")
class InventoryController {

    private final InventoryService inventory;

    /**
     * Returns the stock of one beer.
     *
     * @param upc universal product code
     * @return 200 with the stock level, or 404 if inventory has no record for the UPC
     */
    @GetMapping("/{upc}")
    ResponseEntity<StockLevel> stockOf(@PathVariable String upc) {
        return ResponseEntity.of(inventory.stockOf(upc));
    }
}
