package com.pricetracker.enums;

public enum StoreStatus {
    ACTIVE,     // we check prices
    DISABLED,   // stopped manually or disallowed by robots.txt
    BLOCKED     // the store blocks us (Cloudflare etc.)
}
