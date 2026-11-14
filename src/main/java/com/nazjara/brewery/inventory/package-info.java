/**
 * Inventory module: stock on hand per beer, and stock reserved for orders.
 * <p>
 * {@code allowedDependencies = "order"} permits exactly one dependency: on {@code order}'s API, whose events this
 * module listens to ({@code OrderPlaced}, {@code OrderCancelled}) and whose reply events it publishes
 * ({@code OrderAllocated}, {@code OrderAllocationFailed}). {@code order} does not depend back on this module, so the
 * two-way conversation has a one-way dependency and {@code ApplicationModules.verify()} finds no cycle. A call to
 * {@code CatalogService} from here fails {@code verify()} with "Module 'inventory' depends on module 'catalog' …
 * Allowed targets: order".
 * <p>
 * Replaces the old {@code beer-inventory-service}.
 */
@ApplicationModule(displayName = "Inventory", allowedDependencies = "order")
package com.nazjara.brewery.inventory;

import org.springframework.modulith.ApplicationModule;
