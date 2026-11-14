/**
 * Order module: customer orders and their lifecycle.
 * <p>
 * {@code allowedDependencies = "catalog"} allows calls to the catalog API (UPC validation) and nothing else. The
 * declaration documents a design decision: placing an order does not read stock. If {@code OrderService} started
 * calling {@code InventoryService}, {@code ApplicationModules.verify()} would fail with "Module 'order' depends on
 * module 'inventory' … Allowed targets: catalog", so the change has to be made deliberately here first.
 * <p>
 * Allocation still involves inventory, through events in both directions. This module declares both the requests
 * ({@code OrderPlaced}, {@code OrderCancelled}) and the replies ({@code OrderAllocated}, {@code OrderAllocationFailed}).
 * {@code verify()} detects cycles from type references, so who owns an event type decides the dependency direction:
 * the module that publishes the replies references them and depends on {@code order}, while {@code order} listens to
 * its own types and depends on nobody.
 * <p>
 * Replaces the old {@code beer-order-service}.
 */
@ApplicationModule(displayName = "Order", allowedDependencies = "catalog")
package com.nazjara.brewery.order;

import org.springframework.modulith.ApplicationModule;
