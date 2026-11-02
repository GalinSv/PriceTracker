package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogStatusDto {
    private boolean updating;
    private List<StoreCatalog> stores;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreCatalog {
        private String storeCode;
        private String storeName;
        private long links;
        private LocalDateTime updatedAt;
    }
}
