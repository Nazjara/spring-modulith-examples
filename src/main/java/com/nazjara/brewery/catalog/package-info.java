/**
 * Catalog module: the beers the brewery sells.
 * <p>
 * {@code allowedDependencies = {}} declares that this module depends on no other module. It is the bottom of the
 * module graph: {@code order} uses it, it uses nothing. If code here ever references another
 * module's API, {@code ApplicationModules.verify()} fails with "Module 'catalog' depends on module '…' via … Allowed
 * targets: none", although no cycle exists. Without the attribute the same reference would pass.
 * <p>
 * Replaces the catalog part of the old {@code beer-service}.
 */
@ApplicationModule(displayName = "Catalog", allowedDependencies = {})
package com.nazjara.brewery.catalog;

import org.springframework.modulith.ApplicationModule;
