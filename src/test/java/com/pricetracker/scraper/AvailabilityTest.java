package com.pricetracker.scraper;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// schema.org availability as stores write it in their product data
class AvailabilityTest {

    @ParameterizedTest
    @CsvSource(nullValues = "-", textBlock = """
            https://schema.org/InStock,             true,   false
            http://schema.org/InStock,              true,   false
            InStock,                                true,   false
            https://schema.org/LimitedAvailability, true,   false
            https://schema.org/OutOfStock,          false,  false
            https://schema.org/Discontinued,        false,  false
            https://schema.org/PreOrder,            true,   true
            https://schema.org/BackOrder,           true,   true
            -,                                      -,      false
            """)
    void orderableProductsAreInStockAndMarkedAsOnOrder(String availability, Boolean inStock, boolean onOrder) {
        assertThat(Availability.isInStock(availability)).isEqualTo(inStock);
        assertThat(Availability.isOnOrder(availability)).isEqualTo(onOrder);
    }
}
