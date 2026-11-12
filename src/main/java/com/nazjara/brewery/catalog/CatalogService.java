package com.nazjara.brewery.catalog;

import com.nazjara.brewery.catalog.internal.Beer;
import com.nazjara.brewery.catalog.internal.BeerRepository;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalog API: the only entry point other modules use to read beers.
 * <p>
 * Other modules call it as a plain Spring bean, in their own thread and transaction. In the microservices version
 * this was {@code beer-service}'s REST endpoint plus the JMS listener that validated orders; here a call is a method
 * invocation, and the module boundary is enforced by {@code ApplicationModules.verify()}, which forbids callers from
 * reaching {@code catalog.internal} directly.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private final BeerRepository beers;

    /**
     * Lists all beers.
     *
     * @return every beer in the catalog
     */
    public List<BeerInfo> findAll() {
        return beers.findAll().stream().map(CatalogService::toInfo).toList();
    }

    /**
     * Finds a beer by its UPC.
     *
     * @param upc universal product code
     * @return the beer, or empty if the catalog does not know the UPC
     */
    public Optional<BeerInfo> findByUpc(String upc) {
        return beers.findByUpc(upc).map(CatalogService::toInfo);
    }

    private static BeerInfo toInfo(Beer beer) {
        return new BeerInfo(beer.upc(), beer.beerName(), beer.beerStyle(), beer.price());
    }
}
