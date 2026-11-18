package com.pricetracker.service.impl;

import com.pricetracker.util.TextUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The rule is SQL, so it runs against a real PostgreSQL, the same version as in docker-compose.yml.
// Without Docker the tests are skipped.
@Testcontainers(disabledWithoutDocker = true)
class AccessoryRuleTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-alpine");

    private static Connection connection;

    @BeforeAll
    static void connect() throws SQLException {
        connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @AfterAll
    static void disconnect() throws SQLException {
        connection.close();
    }

    @Test
    void aWordBeforeTheProductMakesAnAccessory() throws SQLException {
        assertThat(isAccessory("galaxy buds 4 pro", "kalaf za samsung galaxy buds 4 pro", null, "Samsung")).isTrue();
        assertThat(isAccessory("airpods pro 3", "native union active case airpods pro 3", null, null)).isTrue();
        assertThat(isAccessory("galaxy s26 ultra", "puro kalyf 36120 flip samsung galaxy s26 ultra", null, null)).isTrue();
        assertThat(isAccessory("galaxy s26 ultra", "kaluf samsung slim magnet galaxy s26 ultra cheren", null, "Samsung"))
                .isTrue();
    }

    @Test
    void theProductItselfIsNoAccessory() throws SQLException {
        assertThat(isAccessory("iphone 17 pro max", "apple iphone 17 pro max 256gb silver", null, "Apple")).isFalse();
        assertThat(isAccessory("galaxy s26 ultra", "smartfon samsung galaxy s26 ultra 512 gb 5g cheren",
                "Смартфон Samsung Galaxy S26 Ultra, 512 GB, 5G, черен", "Samsung")).isFalse();
        assertThat(isAccessory("jbl flip 7", "jbl flip 7 black", "JBL Flip 7", "JBL")).isFalse();
    }

    // AirPods come with a case and a watch has one, so "case" after the name is part of the product there
    @Test
    void theCaseAProductComesWithIsNoAccessory() throws SQLException {
        assertThat(isAccessory("airpods pro 3", "apple airpods pro 3 with magsafe case usb c", null, "Apple")).isFalse();
        assertThat(isAccessory("airpods pro 3", "bezzhichni slushalki apple airpods pro 3 magsafe kalaf usb c", null,
                "Apple")).isFalse();
        assertThat(isAccessory("sony wf 1000xm5", "slushalki sony wf 1000xm5 s kalaf za zarezhdane", null, "Sony"))
                .isFalse();
        assertThat(isAccessory("apple watch series 11",
                "apple watch series 11 46mm jet black aluminium case with black sport band", null, "Apple")).isFalse();
    }

    @Test
    void aCaseNamedAfterTheProductIsAnAccessory() throws SQLException {
        assertThat(isAccessory("iphone 17 pro max", "apple iphone 17 pro max clear case with magsafe", null, "Apple"))
                .isTrue();
        assertThat(isAccessory("galaxy s26 ultra", "samsung galaxy s26 ultra clear case transparent ef qs948cte", null,
                "Samsung")).isTrue();
        assertThat(isAccessory("airpods pro 3", "dekoni audio airpods pro 3 tips 1 pack extra large", null, null))
                .isTrue();
    }

    // The link of this BestPC case doesn't say what it is, only the name on its page does
    @Test
    void theNameFromTheProductPageCounts() throws SQLException {
        assertThat(isAccessory("galaxy s26 ultra", "samsung galaxy s26 ultra prozrachen 482462",
                "Калъф за смартфон Samsung Galaxy S26 Ultra Прозрачен", "Samsung")).isTrue();
        assertThat(isAccessory("galaxy s26 ultra", "samsung galaxy s26 ultra prozrachen 482462", null, "Samsung"))
                .isFalse();
    }

    @Test
    void samsungsOwnAccessoryCodesCount() throws SQLException {
        assertThat(isAccessory("galaxy s26 ultra", "samsung ef us948ctegww anti ref film galaxy s26 ultra trans", null,
                "Samsung")).isTrue();
        assertThat(isAccessory("galaxy s26 ultra", "samsung ej ps948bbegww s pen galaxy s26 ultra black", null,
                "Samsung")).isTrue();
    }

    @Test
    void everythingFromAMakerOfCasesIsAnAccessory() throws SQLException {
        String link = "spigen vault apple airpods pro 3 midnight green";

        assertThat(isAccessory("airpods pro 3", link, null, "Spigen")).isTrue();
        assertThat(isAccessory("airpods pro 3", "ringke onyx magnetic magsafe apple airpods pro 3 black", null, "Ringke"))
                .isTrue();
        assertThat(isAccessory("galaxy s26 ultra", "uag plyo samsung galaxy s26 ultra ice", null, "Urban-Armor"))
                .isTrue();
        // ... unless the search is for that maker
        assertThat(isAccessory("spigen airpods pro 3", link, null, "Spigen")).isFalse();
    }

    // "kalaf iphone 17" looks for cases, so "kalaf" doesn't make a link an accessory of what is searched
    @Test
    void aWordOfTheSearchItselfDoesNotCount() throws SQLException {
        String link = "apple kalaf iphone 17 clear";

        assertThat(isAccessory("iphone 17", link, null, "Apple")).isTrue();
        assertThat(isAccessory("калъф iphone 17", link, null, "Apple")).isFalse();
    }

    // Runs the rule for one link, the way the search runs it for every link it finds
    private boolean isAccessory(String search, String searchText, String name, String brand) throws SQLException {
        List<String> words = TextUtils.searchWords(search);
        List<Object> params = new ArrayList<>(Arrays.asList(searchText, name, brand));
        String sql = """
                WITH e AS (SELECT ?::text AS search_text, ?::text AS title, ?::text AS facet_brand),
                     o AS (SELECT NULL::text AS title)
                SELECT\s""" + AccessoryRule.sql(words, params) + " FROM e, o";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }
}
