package com.pricetracker.scraper;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// All StoreAdapter beans (Ozone, PIC...) by store code
@Component
public class StoreAdapters {

    private final Map<String, StoreAdapter> byStoreCode;

    public StoreAdapters(List<StoreAdapter> adapters) {
        this.byStoreCode = adapters.stream()
                .collect(Collectors.toMap(StoreAdapter::storeCode, Function.identity()));
    }

    public Optional<StoreAdapter> find(String storeCode) {
        return Optional.ofNullable(byStoreCode.get(storeCode));
    }
}
