package com.nazjara.brewery.catalog.web;

import com.nazjara.brewery.catalog.BeerInfo;
import com.nazjara.brewery.catalog.CatalogService;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints of the catalog module. Goes through {@link CatalogService} like any other client, so the HTTP layer
 * and other modules see the same API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/beers")
class BeerController {

    private final CatalogService catalog;

    /**
     * Lists all beers.
     *
     * @return every beer in the catalog
     */
    @GetMapping
    List<BeerInfo> findAll() {
        return catalog.findAll();
    }

    /**
     * Finds a beer by its UPC.
     *
     * @param upc universal product code
     * @return 200 with the beer, or 404 if the UPC is unknown
     */
    @GetMapping("/{upc}")
    ResponseEntity<BeerInfo> findByUpc(@PathVariable String upc) {
        return ResponseEntity.of(catalog.findByUpc(upc));
    }
}
