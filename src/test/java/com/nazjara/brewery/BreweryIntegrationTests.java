package com.nazjara.brewery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BreweryIntegrationTests {

    private static final String MANGO_BOBS_UPC = "0631234200036";

    @Autowired
    private MockMvcTester mvc;

    @Test
    void catalogListsMigratedBeers() {
        var response = mvc.get().uri("/api/v1/beers");

        assertThat(response).hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(6);
    }

    @Test
    void inventoryReturnsMigratedStock() {
        var response = mvc.get().uri("/api/v1/inventory/{upc}", MANGO_BOBS_UPC);

        assertThat(response).hasStatusOk().bodyJson().extractingPath("$.quantityOnHand").isEqualTo(50);
    }

    @Test
    void placingOrderWithKnownBeersStoresItAsNew() {
        var body = """
                {"customerRef": "tasting-room", "lines": [{"upc": "%s", "quantity": 3}]}
                """.formatted(MANGO_BOBS_UPC);

        var placed = mvc.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        var location = placed.getResponse().getHeader(HttpHeaders.LOCATION);

        assertThat(placed).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.status").isEqualTo("NEW");
        assertThat(mvc.get().uri(location)).hasStatusOk().bodyJson()
                .extractingPath("$.lines[0].quantity").isEqualTo(3);
    }

    @Test
    void placingOrderWithUnknownBeerIsRejectedWithTheUnknownUpcs() {
        var body = """
                {"customerRef": "tasting-room", "lines": [{"upc": "%s", "quantity": 1}, {"upc": "nope", "quantity": 1}]}
                """.formatted(MANGO_BOBS_UPC);

        var response = mvc.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body);

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.unknownUpcs").asArray().containsExactly("nope");
    }
}
