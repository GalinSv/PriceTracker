package com.pricetracker.service;

import com.pricetracker.entity.User;

public interface UserService {
    // The logged in user of this request; created on the first request after registration
    User currentUser();
}
