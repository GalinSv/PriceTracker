package com.pricetracker.dto;

import com.pricetracker.enums.StoreStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreDto {
    private Long id;
    private String code;
    private String name;
    private String baseUrl;
    private String logoUrl;
    private StoreStatus status;
}
