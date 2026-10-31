package com.pricetracker.service.impl;

import com.pricetracker.dto.StoreDto;
import com.pricetracker.entity.Store;
import com.pricetracker.exception.ResourceNotFoundException;
import com.pricetracker.repository.StoreRepository;
import com.pricetracker.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreServiceImpl implements StoreService {

    private final StoreRepository storeRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<StoreDto> getAll() {
        return storeRepository.findAll(Sort.by("name")).stream()
                .map(store -> modelMapper.map(store, StoreDto.class))
                .toList();
    }

    @Override
    public StoreDto getByCode(String code) {
        Store store = storeRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + code));
        return modelMapper.map(store, StoreDto.class);
    }
}
