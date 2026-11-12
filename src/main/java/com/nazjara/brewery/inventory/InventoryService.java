package com.nazjara.brewery.inventory;

import com.nazjara.brewery.inventory.internal.StockItemRepository;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventory API: the only entry point other modules use to read stock.
 * <p>
 * A plain Spring bean called in-process. In the microservices version stock was read through
 * {@code beer-inventory-service}'s REST endpoint, with a failover service behind the gateway. The module boundary is
 * enforced by {@code ApplicationModules.verify()}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final StockItemRepository stock;

    /**
     * Returns the stock of one beer.
     *
     * @param upc universal product code
     * @return the stock level, or empty if inventory has no record for the UPC
     */
    public Optional<StockLevel> stockOf(String upc) {
        return stock.findByUpc(upc).map(item -> new StockLevel(item.upc(), item.quantityOnHand()));
    }
}
