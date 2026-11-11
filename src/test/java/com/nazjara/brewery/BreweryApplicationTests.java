package com.nazjara.brewery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BreweryApplicationTests {

    @Test
    void contextLoads() {
    }
}
