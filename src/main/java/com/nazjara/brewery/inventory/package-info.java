/**
 * Inventory module: stock on hand per beer.
 * <p>
 * {@code allowedDependencies = {}} declares that this module depends on no other module. A call to
 * {@code CatalogService} from here fails {@code ApplicationModules.verify()} with "Module 'inventory' depends on module
 * 'catalog' … Allowed targets: none" until this declaration is changed deliberately.
 * <p>
 * Replaces the old {@code beer-inventory-service}.
 */
@ApplicationModule(displayName = "Inventory", allowedDependencies = {})
package com.nazjara.brewery.inventory;

import org.springframework.modulith.ApplicationModule;
