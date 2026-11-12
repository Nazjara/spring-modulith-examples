package com.nazjara.brewery.order;

import java.util.List;

/** Thrown when an order references UPCs the catalog does not know. */
public class UnknownBeerException extends RuntimeException {

    private final List<String> upcs;

    public UnknownBeerException(List<String> upcs) {
        super("Unknown beer UPC(s): " + upcs);
        this.upcs = List.copyOf(upcs);
    }

    /**
     * Returns the UPCs the catalog does not know.
     *
     * @return the unknown UPCs
     */
    public List<String> upcs() {
        return upcs;
    }
}
