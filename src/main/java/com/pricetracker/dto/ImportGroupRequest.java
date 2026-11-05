package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Store links that the search showed as one product
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportGroupRequest {
    private List<String> urls;
}
