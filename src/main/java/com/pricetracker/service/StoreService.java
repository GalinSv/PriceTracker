package com.pricetracker.service;

import com.pricetracker.dto.StoreDto;

import java.util.List;

public interface StoreService {
    List<StoreDto> getAll();

    StoreDto getByCode(String code);
}
