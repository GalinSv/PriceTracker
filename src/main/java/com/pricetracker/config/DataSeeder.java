package com.pricetracker.config;

import com.pricetracker.entity.Store;
import com.pricetracker.enums.StoreStatus;
import com.pricetracker.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// Fills the stores table on the first start. Does nothing if there are already stores.
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final StoreRepository storeRepository;

    @Override
    public void run(String... args) {
        if (storeRepository.count() > 0) {
            return;
        }

        storeRepository.saveAll(List.of(
                store("emag", "eMAG", "https://www.emag.bg", StoreStatus.ACTIVE, null),
                store("ozone", "Ozone", "https://www.ozone.bg", StoreStatus.ACTIVE, null),
                store("pic", "PIC.bg", "https://www.pic.bg", StoreStatus.ACTIVE, null),
                store("gplay", "GPlay", "https://gplay.bg", StoreStatus.ACTIVE, null),
                store("technomarket", "Technomarket", "https://www.technomarket.bg", StoreStatus.ACTIVE, null),
                store("jarcomputers", "JAR Computers", "https://www.jarcomputers.com", StoreStatus.ACTIVE, null),
                store("tehnomix", "Tehnomix", "https://www.tehnomix.bg", StoreStatus.ACTIVE, null),
                store("techmart", "Techmart", "https://techmart.bg", StoreStatus.ACTIVE, null),
                store("desktop", "Desktop.bg", "https://desktop.bg", StoreStatus.ACTIVE, null),
                store("laptop", "Laptop.bg", "https://laptop.bg", StoreStatus.ACTIVE, null),
                store("plesio", "Plesio", "https://plesio.bg", StoreStatus.ACTIVE, null),
                store("allstore", "Allstore", "https://allstore.bg", StoreStatus.ACTIVE, null),
                store("technopolis", "Technopolis", "https://www.technopolis.bg", StoreStatus.DISABLED,
                        "robots.txt disallows all crawlers except a whitelist"),
                store("ardes", "Ardes", "https://ardes.bg", StoreStatus.BLOCKED, "Cloudflare bot challenge"),
                store("zora", "Zora", "https://zora.bg", StoreStatus.BLOCKED, "Cloudflare bot challenge")
        ));
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
