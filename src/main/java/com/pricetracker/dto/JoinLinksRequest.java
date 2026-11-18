package com.pricetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// The links of a product in the search, and for a join the links of the product they go to
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinLinksRequest {
    private List<String> urls;
    private List<String> targetUrls;
}
