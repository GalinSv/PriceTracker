package com.pricetracker.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// The filters are read from a link's search words: Latin letters, lower case, as TextUtils.searchWords gives them
class FacetExtractorTest {

    private final FacetExtractor facets = FacetExtractor.load(List.of());

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            samsung galaxy s26 ultra 5g 256gb                                   | Samsung Galaxy S26 Ultra
            apple iphone 17 256gb black                                         | Apple Iphone 17
            apple iphone 17 pro max 512 gb                                      | Apple Iphone 17 Pro Max
            google pixel 10 pro xl 256gb                                        | Google Pixel 10 Pro XL
            jbl flip 7 black                                                    | JBL Flip 7
            logitech g502 hero                                                  | Logitech G502
            logitech g502 x plus                                                | Logitech G502 X Plus
            """)
    void readsTheSeriesAfterTheBrand(String searchText, String series) {
        assertThat(facets.extract(searchText).series()).isEqualTo(series);
    }

    // Stores write the same phone in many ways; all of them are one series in the filter
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            mobilen telefon samsung sm s948b galaxy s26 ultra 5g 256gb 12gb white | Samsung Galaxy S26 Ultra
            samsung galaxy s26 ultra s948 5g dual sim 12gb ram 256gb              | Samsung Galaxy S26 Ultra
            smartfon samsung galaxy s26 ultra 12 256 gb 5g byal                   | Samsung Galaxy S26 Ultra
            samsung galaxy s26 ultra 12 256gb white                               | Samsung Galaxy S26 Ultra
            samsung galaxy a56 5g 8 256gb                                         | Samsung Galaxy A56
            xiaomi 15 12 512gb                                                    | Xiaomi 15
            acer aspire 5 a515 57 i5 16gb                                         | Acer Aspire 5
            """)
    void leavesModelCodesAndRamOutOfTheSeries(String searchText, String series) {
        assertThat(facets.extract(searchText).series()).isEqualTo(series);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            samsung galaxy z fold 7 512gb               | Samsung Galaxy Z Fold 7
            samsung galaxy z flip 7 fe 256gb            | Samsung Galaxy Z Flip 7 FE
            samsung galaxy tab s10 fe 5g                | Samsung Galaxy Tab S10 FE
            samsung galaxy buds 4 pro white             | Samsung Galaxy Buds 4 Pro
            xiaomi redmi note 14 pro plus 5g 8 256gb    | Xiaomi Redmi Note 14 Pro Plus
            """)
    void keepsTheLineInsideALine(String searchText, String series) {
        assertThat(facets.extract(searchText).series()).isEqualTo(series);
    }

    // The 11 and the 13 inch iPad Air are different products, and "iphone 17 256gb" is no "iphone" with 17 GB RAM
    @Test
    void aNumberOfTheNameIsNotTakenForRam() {
        assertThat(facets.extract("apple ipad air m4 11 256gb").series()).isEqualTo("Apple Ipad Air M4 11");
        assertThat(facets.extract("apple ipad air m4 13 256gb").series()).isEqualTo("Apple Ipad Air M4 13");
        assertThat(facets.extract("apple iphone 17 256gb").series()).isEqualTo("Apple Iphone 17");
    }

    @Test
    void anAccessoryHasNoBrandOfTheDeviceItIsFor() {
        FacetExtractor.Facets found = facets.extract("kalaf za samsung galaxy s26 ultra");

        assertThat(found.brand()).isNull();
        assertThat(found.series()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            apple iphone 17 256gb black                         | 256 GB
            apple iphone 17 pro max 512 gb                      | 512 GB
            samsung galaxy s26 ultra 16gb 1tb                   | 1 TB
            samsung galaxy s26 ultra 12gb ram 256gb             | 256 GB
            samsung 990 pro ssd 2000gb                          | 2000 GB
            jbl flip 7 black                                    | -
            """)
    void takesTheBiggestSizeAsTheMemory(String searchText, String memory) {
        assertThat(facets.extract(searchText).memory()).isEqualTo(memory);
    }

    // JAR Computers writes "8/256GB" in its links as "8256gb"
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            samsung galaxy s26 fe 8256gb green sm s741blggeue   | 256 GB
            samsung galaxy s26 fe 8128gb sm s741bzkdeue         | 128 GB
            samsung galaxy s26 ultra 16512gb                    | 512 GB
            samsung galaxy s26 ultra 161tb                      | 1 TB
            """)
    void splitsRamGluedToTheStorage(String searchText, String memory) {
        assertThat(facets.extract(searchText).memory()).isEqualTo(memory);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            jbl flip 7 black                                    | Черен
            smartfon samsung galaxy s26 ultra 256 gb byal       | Бял
            samsung galaxy s26 ultra sky blue                   | Син
            apple iphone 17 256gb lilav                         | Лилав
            apple iphone 17 256gb                               | -
            """)
    void readsTheColorInBulgarianAndEnglish(String searchText, String color) {
        assertThat(facets.extract(searchText).color()).isEqualTo(color);
    }

    @Test
    void nothingIsFoundInAnEmptyText() {
        assertThat(facets.extract("")).isEqualTo(new FacetExtractor.Facets(null, null, null, null));
        assertThat(facets.extract(null)).isEqualTo(new FacetExtractor.Facets(null, null, null, null));
    }

    // Product pages fix the spelling of a brand from the list, unless they shout it
    @Test
    void aBrandIsSpelledAsProductPagesWriteIt() {
        assertThat(FacetExtractor.load(List.of("JBL")).extract("jbl flip 7").brand()).isEqualTo("JBL");
        assertThat(FacetExtractor.load(List.of("LOGITECH")).extract("logitech g502").brand()).isEqualTo("Logitech");
        assertThat(FacetExtractor.load(List.of("logiTech")).extract("logitech g502").brand()).isEqualTo("logiTech");
    }
}
