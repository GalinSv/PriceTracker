package com.pricetracker.config;

import com.pricetracker.entity.Store;
import com.pricetracker.enums.StoreStatus;
import com.pricetracker.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Adds the stores that are not in the table yet. Existing stores are never changed or deleted.
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final StoreRepository storeRepository;

    @Override
    public void run(String... args) {
        Set<String> existingCodes = storeRepository.findAll().stream()
                .map(Store::getCode)
                .collect(Collectors.toSet());

        List<Store> missing = stores().stream()
                .filter(store -> !existingCodes.contains(store.getCode()))
                .toList();
        storeRepository.saveAll(missing);
    }

    private List<Store> stores() {
        return List.of(
                store("ozone", "Ozone", "https://www.ozone.bg", StoreStatus.ACTIVE, null),
                store("pic", "PIC.bg", "https://www.pic.bg", StoreStatus.ACTIVE, null),
                store("ioncomputers", "ION Computers", "https://www.ioncomputers.bg", StoreStatus.ACTIVE, null),
                store("techmart", "Techmart", "https://techmart.bg", StoreStatus.ACTIVE, null),
                store("dice", "Dice.bg", "https://dice.bg", StoreStatus.ACTIVE, null),
                store("muziker", "Muziker", "https://www.muziker.bg", StoreStatus.ACTIVE, null),
                store("gplay", "GPlay", "https://gplay.bg", StoreStatus.ACTIVE, null),
                store("jarcomputers", "JAR Computers", "https://www.jarcomputers.com", StoreStatus.ACTIVE, null),
                store("technomarket", "Technomarket", "https://www.technomarket.bg", StoreStatus.ACTIVE, null),
                store("allstore", "Allstore", "https://allstore.bg", StoreStatus.ACTIVE, null),
                store("ultra", "Ultra.bg", "https://www.ultra.bg", StoreStatus.ACTIVE, null),
                store("desktop", "Desktop.bg", "https://desktop.bg", StoreStatus.ACTIVE, null),
                store("laptop", "Laptop.bg", "https://laptop.bg", StoreStatus.ACTIVE, null),
                store("plesio", "Plesio", "https://plesio.bg", StoreStatus.ACTIVE, null),
                store("technopolis", "Technopolis", "https://www.technopolis.bg", StoreStatus.DISABLED,
                        "robots.txt disallows all crawlers except a whitelist"),
                store("ardes", "Ardes", "https://ardes.bg", StoreStatus.BLOCKED, "Cloudflare bot challenge"),
                store("zora", "Zora", "https://zora.bg", StoreStatus.BLOCKED, "Cloudflare bot challenge")
        );
    }

    private Store store(String code, String name, String baseUrl, StoreStatus status, String lastError) {
        Store store = new Store();
        store.setCode(code);
        store.setName(name);
        store.setBaseUrl(baseUrl);
        store.setStatus(status);
        store.setLastError(lastError);
        return store;
    }
}
