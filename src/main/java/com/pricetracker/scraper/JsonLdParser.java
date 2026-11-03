package com.pricetracker.scraper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

// Reads schema.org "Product" data from <script type="application/ld+json"> blocks.
// Many stores add it for Google, so it is the most stable source of name, price and stock.
@Component
public class JsonLdParser {

    // Some stores put raw new lines inside JSON strings, which strict JSON does not allow
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    public Optional<JsonNode> findProduct(Document page) {
        for (Element script : page.select("script[type=application/ld+json]")) {
            JsonNode root;
            try {
                root = jsonMapper.readTree(script.data());
            } catch (JacksonException e) {
                continue;   // broken JSON in one block should not stop us from reading the others
            }
            Optional<JsonNode> product = findProductNode(root);
            if (product.isPresent()) {
                return product;
            }
        }
        return Optional.empty();
    }

    public ScrapedProduct toScrapedProduct(JsonNode product) {
        ScrapedProduct result = new ScrapedProduct();
        result.setTitle(text(product, "name"));
        result.setBrand(nameOf(product.path("brand")));
        result.setModelCode(text(product, "mpn"));
        result.setStoreSku(text(product, "sku"));
        result.setEan(firstText(product, "gtin13", "gtin", "gtin12", "gtin14", "gtin8"));
        result.setImageUrl(imageOf(product.path("image")));

        JsonNode offer = product.path("offers");
        if (offer.isArray()) {
            offer = offer.path(0);
        }
        result.setPrice(PriceParser.parse(firstText(offer, "price", "lowPrice")));
        result.setCurrency(text(offer, "priceCurrency"));
        result.setInStock(isInStock(text(offer, "availability")));
        return result;
    }

    private Optional<JsonNode> findProductNode(JsonNode node) {
        if (node.isArray()) {
            for (JsonNode item : node) {
                Optional<JsonNode> product = findProductNode(item);
                if (product.isPresent()) {
                    return product;
                }
            }
            return Optional.empty();
        }
        if (!node.isObject()) {
            return Optional.empty();
        }
        if (hasType(node, "Product")) {
            return Optional.of(node);
        }
        return node.has("@graph") ? findProductNode(node.get("@graph")) : Optional.empty();
    }

    // "@type" can be "Product" or ["Product", "Thing"]
    private boolean hasType(JsonNode node, String type) {
        JsonNode typeNode = node.path("@type");
        if (typeNode.isArray()) {
            for (JsonNode item : typeNode) {
                if (type.equals(item.asString())) {
                    return true;
                }
            }
            return false;
        }
        return type.equals(typeNode.asString());
    }


    private String nameOf(JsonNode node) {
        return node.isObject() ? text(node, "name") : clean(node);
    }

    // "image" can be a URL, a list of URLs or {"url": ...}
    private String imageOf(JsonNode node) {
        if (node.isArray()) {
            return imageOf(node.path(0));
        }
        if (node.isObject()) {
            return firstText(node, "url", "contentUrl");
        }
        return clean(node);
    }

    private Boolean isInStock(String availability) {
        if (availability == null) {
            return null;
        }
        return availability.endsWith("InStock")
                || availability.endsWith("LimitedAvailability")
                || availability.endsWith("OnlineOnly")
                || availability.endsWith("InStoreOnly");
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        return clean(node.path(field));
    }

    private String clean(JsonNode value) {
        if (value == null || !value.isValueNode() || value.isNull()) {
            return null;
        }
        // Some stores HTML-escape the JSON, e.g. "&amp;" in image URLs or 2.5&quot; in names
        String text = Parser.unescapeEntities(value.asString(), false).trim();
        return text.isEmpty() ? null : text;
    }
}
