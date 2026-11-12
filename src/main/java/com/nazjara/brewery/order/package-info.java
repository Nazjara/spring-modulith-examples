/**
 * Order module: customer orders and their lifecycle.
 * <p>
 * {@code allowedDependencies = "catalog"} allows calls to the catalog API (UPC validation) and nothing else. The
 * declaration documents a design decision: placing an order does not read stock. If {@code OrderService} started
 * calling {@code InventoryService}, {@code ApplicationModules.verify()} would fail with "Module 'order' depends on
 * module 'inventory' … Allowed targets: catalog", so the change has to be made deliberately here first.
 * <p>
 * Replaces the old {@code beer-order-service}.
 */
@ApplicationModule(displayName = "Order", allowedDependencies = "catalog")
package com.nazjara.brewery.order;

import org.springframework.modulith.ApplicationModule;
