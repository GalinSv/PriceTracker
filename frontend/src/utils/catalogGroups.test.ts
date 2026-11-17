import { describe, expect, it } from "vitest";

import type { CatalogEntry } from "../types/api";
import { filterAndSort, groupEntries, modelGroups, modelOffers, NO_FILTERS, offerColors } from "./catalogGroups";

let links = 0;

// A checked search result; every test names only what matters to it
function entry(fields: Partial<CatalogEntry>): CatalogEntry {
    links += 1;
    return {
        storeCode: "ozone",
        storeName: "Ozone",
        url: `https://store.example/product-${links}`,
        name: "Product",
        productId: null,
        price: 100,
        currency: "EUR",
        inStock: true,
        onOrder: false,
        checked: true,
        eans: [],
        brand: null,
        modelKey: null,
        imageUrl: null,
        series: null,
        color: null,
        memory: null,
        joinedToUrl: null,
        gone: false,
        accessory: false,
        ...fields,
    };
}

// One color and size of a model in one store. The barcode is the same in every store, so the stores' links
// of one color and size become one product.
function variant(store: string, series: string, color: string | null, memory: string | null, price: number | null) {
    return entry({
        storeCode: store,
        storeName: store,
        name: [series, memory, color].filter(Boolean).join(" "),
        series,
        color,
        memory,
        price,
        eans: [`${series}/${color}/${memory}`],
    });
}

const storesOf = (entries: CatalogEntry[]) => entries.map((item) => item.storeCode);

describe("groupEntries", () => {
    it("puts the links with a shared barcode in one product, in stock and cheapest first", () => {
        const groups = groupEntries([
            entry({ storeCode: "pic", eans: ["111"], price: 120 }),
            entry({ storeCode: "ozone", eans: ["111", "999"], price: 100 }),
            entry({ storeCode: "jar", eans: ["111"], price: 90, inStock: false }),
            entry({ storeCode: "ultra", eans: ["222"], price: 50 }),
        ]);

        expect(groups).toHaveLength(2);
        expect(storesOf(groups[0].entries)).toEqual(["ozone", "pic", "jar"]);
        expect(groups[0].lowestPrice).toBe(100);
        expect(groups[0].inStockCount).toBe(2);
    });

    it("joins a store with only a barcode and a store with only a model code through a store with both", () => {
        const groups = groupEntries([
            entry({ storeCode: "pic", eans: ["111"] }),
            entry({ storeCode: "jar", modelKey: "logitech:910005470" }),
            entry({ storeCode: "ozone", eans: ["111"], modelKey: "logitech:910005470" }),
        ]);

        expect(groups).toHaveLength(1);
    });

    it("keeps links that share nothing apart", () => {
        const groups = groupEntries([
            entry({ eans: ["111"], modelKey: "logitech:910005470" }),
            entry({ eans: ["222"], modelKey: "logitech:910005471" }),
            entry({}),
        ]);

        expect(groups).toHaveLength(3);
    });

    it("shows a link an admin moved with the product it was put with, under that product's name", () => {
        const target = entry({ name: "Lamp", storeCode: "pic" });
        const moved = entry({ name: "LAMPA 42", storeCode: "mallbg", joinedToUrl: target.url });

        const groups = groupEntries([moved, target]);

        expect(groups).toHaveLength(1);
        expect(groups[0].name).toBe("Lamp");
    });

    it("takes the tracked product's id for the whole product", () => {
        const groups = groupEntries([entry({ eans: ["111"] }), entry({ eans: ["111"], productId: 7 })]);

        expect(groups[0].productId).toBe(7);
    });

    it("prefers a name that is not all capitals", () => {
        const groups = groupEntries([
            entry({ eans: ["111"], name: "SAMSUNG GALAXY S26 ULTRA" }),
            entry({ eans: ["111"], name: "Samsung Galaxy S26 Ultra" }),
        ]);

        expect(groups[0].name).toBe("Samsung Galaxy S26 Ultra");
    });
});

describe("regional versions of one device", () => {
    it("joins Samsung codes that differ only in the region", () => {
        const groups = groupEntries([
            entry({ storeCode: "bestpc", eans: ["8806097826798"], modelKey: "samsung:SMS948BLBDEUB" }),
            entry({ storeCode: "ozone", eans: ["8806097826781"], modelKey: "samsung:SMS948BLBDEUE" }),
            // Another color: ZK is black, LB is blue
            entry({ storeCode: "ozone", eans: ["8806097827221"], modelKey: "samsung:SMS948BZKDEUE" }),
            // Another size: D is 256 GB, G is 512 GB
            entry({ storeCode: "ozone", eans: ["8806097826705"], modelKey: "samsung:SMS948BLBGEUE" }),
        ]);

        expect(groups.map((group) => storesOf(group.entries))).toEqual([["bestpc", "ozone"], ["ozone"], ["ozone"]]);
    });

    it("joins Apple codes that differ only in the region", () => {
        const groups = groupEntries([
            entry({ storeCode: "pic", modelKey: "apple:MFYM4HXA" }),
            entry({ storeCode: "ozone", modelKey: "apple:MFYM4ZDA" }),
            entry({ storeCode: "ozone", modelKey: "apple:MFYN4ZDA" }),
        ]);

        expect(groups.map((group) => storesOf(group.entries))).toEqual([["pic", "ozone"], ["ozone"]]);
    });

    it("leaves other makers alone: the end of a JBL code is the color", () => {
        const groups = groupEntries([
            entry({ modelKey: "jbl:JBLFLIP7BLK" }),
            entry({ modelKey: "jbl:JBLFLIP7BLU" }),
        ]);

        expect(groups).toHaveLength(2);
    });

    it("reads Samsung's code from the name when the store gives its own number as the code", () => {
        const groups = groupEntries([
            entry({
                storeCode: "bestpc",
                brand: "Samsung",
                modelKey: "samsung:1788789",
                name: "Смартфон Samsung Galaxy S26 Ultra SM-S948BLBHEUB, 16GB RAM, 1TB",
            }),
            entry({ storeCode: "ozone", modelKey: "samsung:SMS948BLBHEUE" }),
            // "SM-S948B/DS" is the model without a color, a size and a region
            entry({ storeCode: "bestpc", brand: "Samsung", name: "Смартфон SAMSUNG Galaxy S26 Ultra SM-S948B/DS, 12GB" }),
        ]);

        expect(groups.map((group) => storesOf(group.entries))).toEqual([["bestpc", "ozone"], ["bestpc"]]);
    });

    it("keeps two tracked products apart, each has its own price history", () => {
        const groups = groupEntries([
            entry({ modelKey: "samsung:SMS948BLBDEUB", productId: 1 }),
            entry({ modelKey: "samsung:SMS948BLBDEUE", productId: 2 }),
            entry({ modelKey: "samsung:SMS948BLBDEEB" }),
        ]);

        expect(groups).toHaveLength(2);
        expect(groups.map((group) => group.entries.length).sort()).toEqual([1, 2]);
    });
});

describe("model codes cut by a store", () => {
    it("joins a cut code with the only whole code it starts, when the color is the same", () => {
        const groups = groupEntries([
            entry({ storeCode: "jar", modelKey: "sony:WF1000XM5BC", color: "Черен" }),
            entry({ storeCode: "ozone", modelKey: "sony:WF1000XM5BCE7", color: "Черен", eans: ["4548736133839"] }),
        ]);

        expect(groups).toHaveLength(1);
    });

    it("doesn't join when the colors differ or the code is a short name", () => {
        const colors = groupEntries([
            entry({ storeCode: "jar", modelKey: "sony:WF1000XM5BC", color: "Черен" }),
            entry({ storeCode: "ozone", modelKey: "sony:WF1000XM5BCE7", color: "Сребрист" }),
        ]);
        const shortCodes = groupEntries([
            entry({ storeCode: "jar", modelKey: "logitech:G502", color: "Черен" }),
            entry({ storeCode: "ozone", modelKey: "logitech:G502X", color: "Черен" }),
        ]);

        expect(colors).toHaveLength(2);
        expect(shortCodes).toHaveLength(2);
    });
});

describe("modelGroups", () => {
    const ULTRA = "Samsung Galaxy S26 Ultra";

    it("puts the colors of a model together, one block per memory size, the smallest first", () => {
        const groups = groupEntries([
            variant("ozone", ULTRA, "Син", "512 GB", 1300),
            variant("pic", ULTRA, "Черен", "512 GB", 1290),
            variant("ozone", ULTRA, "Син", "256 GB", 1129),
            variant("pic", ULTRA, "Син", "256 GB", 1259),
            variant("pic", ULTRA, "Черен", "256 GB", 1188),
        ]);

        const models = modelGroups(groups);

        expect(models.map((model) => model.name)).toEqual([`${ULTRA} 256 GB`, `${ULTRA} 512 GB`]);
        expect(models[0].groups).toHaveLength(2);
        expect(models[0].lowestPrice).toBe(1129);
        expect(models[0].key).not.toBe(models[1].key);
    });

    it("leaves out accessories, and cheap products that only carry the model's name", () => {
        const pods = "Apple Airpods Pro 3";
        const groups = groupEntries([
            variant("ozone", pods, "Бял", null, 234),
            variant("pic", pods, "Бял", null, 249),
            variant("jar", pods, null, null, 229),
            { ...variant("ozone", pods, "Черен", null, 25), accessory: true },
            // A case a store named like the earphones, without any accessory word
            variant("mallbg", pods, "Син", null, 19),
        ]);

        const [model] = modelGroups(groups);

        expect(model.name).toBe(pods);
        expect(model.groups.map((group) => group.lowestPrice)).toEqual([234, 229]);
    });

    it("leaves out products whose stores don't say the size, when the model comes in several", () => {
        const groups = groupEntries([
            variant("ozone", ULTRA, "Син", "256 GB", 1129),
            variant("pic", ULTRA, "Черен", "256 GB", 1188),
            variant("jar", ULTRA, "Бял", null, 1150),
            // Only the RAM is in this store's name
            variant("ultra", ULTRA, "Лилав", "12 GB", 1160),
        ]);

        const models = modelGroups(groups);

        expect(models.map((model) => model.name)).toEqual([`${ULTRA} 256 GB`]);
        expect(models[0].groups).toHaveLength(2);
    });

    it("takes the size a product's stores agree on, not the RAM one of them names", () => {
        const blue = { eans: ["blue"] };
        const groups = groupEntries([
            { ...variant("ozone", ULTRA, "Син", "256 GB", 1129), ...blue },
            { ...variant("bestpc", ULTRA, "Син", "12 GB", 1159), ...blue },
            variant("pic", ULTRA, "Черен", "256 GB", 1188),
        ]);

        expect(modelGroups(groups).map((model) => model.name)).toEqual([`${ULTRA} 256 GB`]);
    });

    it("needs two products in stock, from at least two stores", () => {
        const oneColor = groupEntries([
            variant("ozone", ULTRA, "Син", "256 GB", 1129),
            variant("pic", ULTRA, "Син", "256 GB", 1259),
        ]);
        const oneStore = groupEntries([
            variant("ozone", ULTRA, "Син", "256 GB", 1129),
            variant("ozone", ULTRA, "Черен", "256 GB", 1188),
        ]);
        const oneInStock = groupEntries([
            variant("ozone", ULTRA, "Син", "256 GB", 1129),
            { ...variant("pic", ULTRA, "Черен", "256 GB", 1188), inStock: false },
        ]);

        expect(modelGroups(oneColor)).toEqual([]);
        expect(modelGroups(oneStore)).toEqual([]);
        expect(modelGroups(oneInStock)).toEqual([]);
    });

    it("takes a name without a number for a whole line, not a model", () => {
        const groups = groupEntries([
            variant("ozone", "JBL Tune", "Черен", null, 40),
            variant("pic", "JBL Tune", "Син", null, 80),
        ]);

        expect(modelGroups(groups)).toEqual([]);
    });

    it("counts a store's extra words with a digit as the same model, and extra words without one as another", () => {
        const groups = groupEntries([
            variant("ozone", "Apple Iphone 17", "Черен", "256 GB", 900),
            variant("pic", "Apple Iphone 17 A3520", "Бял", "256 GB", 910),
            variant("jar", "Apple Iphone 17 Pro", "Син", "256 GB", 1200),
            variant("ultra", "Apple Iphone 17 Pro", "Черен", "256 GB", 1210),
        ]);

        const models = modelGroups(groups);

        expect(models.map((model) => model.name)).toEqual(["Apple Iphone 17 256 GB", "Apple Iphone 17 Pro 256 GB"]);
        expect(models[0].groups).toHaveLength(2);
    });

    it("shows up to three models of a wider search, the plainest name first", () => {
        const model = (series: string, price: number) => [
            variant("ozone", series, "Син", "256 GB", price),
            variant("pic", series, "Черен", "256 GB", price + 10),
        ];
        const groups = groupEntries([
            ...model("Samsung Galaxy S26 Ultra", 1129),
            ...model("Samsung Galaxy S26 FE", 740),
            ...model("Samsung Galaxy S26", 759),
            ...model("Samsung Galaxy S26 Plus", 990),
        ]);

        expect(modelGroups(groups).map((item) => item.name)).toEqual([
            "Samsung Galaxy S26 256 GB",
            "Samsung Galaxy S26 Ultra 256 GB",
            "Samsung Galaxy S26 FE 256 GB",
        ]);
    });
});

describe("modelOffers", () => {
    it("gives every store's best link for every color, in stock and cheapest first", () => {
        const series = "Samsung Galaxy S26 Ultra";
        const groups = groupEntries([
            variant("ozone", series, "Син", "256 GB", 1129),
            variant("ozone", series, "Син", "256 GB", 1200),
            variant("pic", series, "Син", "256 GB", null),
            variant("pic", series, "Черен", "256 GB", 1100),
            { ...variant("jar", series, "Черен", "256 GB", 1050), inStock: false },
        ]);

        const offers = modelOffers(modelGroups(groups)[0]);

        expect(offers.map((offer) => [offer.entry.storeCode, offer.color, offer.entry.price])).toEqual([
            ["pic", "Черен", 1100],
            ["ozone", "Син", 1129],
            ["pic", "Син", null],
            ["jar", "Черен", 1050],
        ]);
        expect(offerColors(offers)).toEqual(["Черен", "Син"]);
    });
});

describe("filterAndSort", () => {
    const groups = () =>
        groupEntries([
            entry({ name: "middle", price: 200 }),
            entry({ name: "cheap", price: 50 }),
            entry({ name: "no price", price: null }),
            entry({ name: "dear", price: 900 }),
        ]);
    const names = (filters: Partial<typeof NO_FILTERS>) =>
        filterAndSort(groups(), { ...NO_FILTERS, ...filters }).map((group) => group.name);

    it("keeps the search order by default", () => {
        expect(names({})).toEqual(["middle", "cheap", "no price", "dear"]);
    });

    it("sorts by price with the products without a price last either way", () => {
        expect(names({ sort: "priceAsc" })).toEqual(["cheap", "middle", "dear", "no price"]);
        expect(names({ sort: "priceDesc" })).toEqual(["dear", "middle", "cheap", "no price"]);
    });

    it("leaves out what is outside the price range, and what has no price", () => {
        expect(names({ minPrice: 100, maxPrice: 500 })).toEqual(["middle"]);
    });
});
