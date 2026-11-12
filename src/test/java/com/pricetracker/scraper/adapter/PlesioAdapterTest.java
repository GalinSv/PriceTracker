package com.pricetracker.scraper.adapter;

import com.pricetracker.scraper.ScrapedProduct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Small copies of Plesio's pages, with only the parts the adapter reads
class PlesioAdapterTest {

    private static final String CATEGORY_URL = "https://plesio.bg/true-wireless-slushalki-c-69978020.html";

    private final PlesioAdapter adapter = new PlesioAdapter();

    @Test
    void onlyTheLowestCategoriesOfTheMenuAreRead() {
        Document home = Jsoup.parse("""
                <ul id="mainCategories">
                  <li id="catal20" class="menuItem">
                    <a href="/apgreyd-c-20.html">ъпгрейд</a>
                    <ul class="subMenu"><li><ul>
                      <li id="catal1" class="subMenuCategory level2">
                        <div class="categoryTitle" data-url="/kompyutri-c-1.html">компютри</div>
                        <ul>
                          <li class="mobile-only"><a href="/kompyutri-c-1.html">Виж всички</a></li>
                          <li id="catal11"><a class="subMenuLink" href="/gotovi-konfiguratsii-c-11.html">Готови</a></li>
                          <li id="catal12"><a class="subMenuLink" href="/all-in-one-c-12.html">All in one</a></li>
                        </ul>
                      </li>
                      <li id="catal2" class="subMenuCategory level2">
                        <div class="categoryTitle" data-url="/monitori-c-2.html">монитори</div>
                      </li>
                    </ul></li></ul>
                  </li>
                </ul>
                <a href="/promo.html">Промоции</a>
                """, "https://plesio.bg/");

        assertThat(adapter.categoryUrls(home)).containsExactly(
                "https://plesio.bg/gotovi-konfiguratsii-c-11.html",
                "https://plesio.bg/all-in-one-c-12.html",
                // Has no categories below it, so it is read itself
                "https://plesio.bg/monitori-c-2.html");
    }

    // A page shows 20 products; the others come with "?ajax=true&page=N"
    @Test
    void theOtherPagesOfACategoryComeFromItsProductCount() {
        assertThat(adapter.morePages(CATEGORY_URL, categoryPage(45))).containsExactly(
                CATEGORY_URL + "?ajax=true&page=2",
                CATEGORY_URL + "?ajax=true&page=3");
        assertThat(adapter.morePages(CATEGORY_URL, categoryPage(40))).containsExactly(CATEGORY_URL + "?ajax=true&page=2");
        assertThat(adapter.morePages(CATEGORY_URL, categoryPage(20))).isEmpty();
        assertThat(adapter.morePages(CATEGORY_URL, Jsoup.parse("<p>no products</p>", CATEGORY_URL))).isEmpty();
    }

    @Test
    void productLinksComeWithTheirNames() {
        Map<String, String> links = adapter.productLinks(categoryPage(45));

        assertThat(links).containsExactly(Map.entry(
                "https://plesio.bg/true-wireless-slushalki-c-69978020/apple-airpods-5-p-394969.html", "Apple Airpods 5"));
    }

    @Test
    void aProductInTwoCategoriesIsOneProduct() {
        assertThat(adapter.productKey("https://plesio.bg/true-wireless-slushalki-c-69978020/apple-airpods-5-p-394969.html"))
                .isEqualTo(adapter.productKey("https://plesio.bg/slushalki-c-69978219/apple-airpods-5-p-394969.html"))
                .isNotEqualTo(adapter.productKey("https://plesio.bg/slushalki-c-69978219/apple-airpods-4-p-394968.html"));
    }

    @Test
    void theProductPageGivesPriceStockAndPicture() {
        ScrapedProduct product = adapter.parseProduct(productPage("149.99&nbsp;€", "1"));

        assertThat(product.getTitle()).isEqualTo("Apple Airpods 5");
        assertThat(product.getBrand()).isEqualTo("Apple");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("149.99"));
        assertThat(product.getCurrency()).isEqualTo("EUR");
        assertThat(product.getInStock()).isTrue();
        assertThat(product.getStoreSku()).isEqualTo("5348978");
        assertThat(product.getImageUrl()).isEqualTo("https://plesioimages.example/600x600/5348978_1.jpg");
    }

    @Test
    void aDiscountedProductHasItsNewPrice() {
        Document page = Jsoup.parse("""
                <h1 class="productTitle">Cellularline Music Sound</h1>
                <div class="productPriceElementOld"><span class="old-value">19.94</span>&nbsp;€</div>
                <div class="productPriceElement">12.98&nbsp;€</div>
                <div class="ptto-availability">1</div>
                """, "https://plesio.bg/");

        assertThat(adapter.parseProduct(page).getPrice()).isEqualByComparingTo(new BigDecimal("12.98"));
    }

    @Test
    void aSoldOutProductIsNotInStock() {
        assertThat(adapter.parseProduct(productPage("149.99&nbsp;€", "0")).getInStock()).isFalse();
    }

    private Document categoryPage(int products) {
        return Jsoup.parse("""
                <script>var allCount = %d; var page = 1;</script>
                <div class="productListItem">
                  <div class="productImage">
                    <a href="/true-wireless-slushalki-c-69978020/apple-airpods-5-p-394969.html"><img src="a.jpg"></a>
                  </div>
                  <div class="productTitle">
                    <a href="/true-wireless-slushalki-c-69978020/apple-airpods-5-p-394969.html"><h2>Apple Airpods 5</h2></a>
                  </div>
                </div>
                <div class="productListItem"><div class="productTitle">no link here</div></div>
                """.formatted(products), CATEGORY_URL);
    }

    private Document productPage(String price, String availability) {
        return Jsoup.parse("""
                <div class="ptto-item-details" style="display:none">
                  <div class="ptto-listprice">149.99 €</div>
                  <div class="ptto-currency">BGN</div>
                  <div class="ptto-availability">%s</div>
                  <div class="ptto-brand">Apple</div>
                </div>
                <h1 class="productTitle">Apple Airpods 5</h1>
                <div class="productSku"> Каталожен номер: 5348978 </div>
                <div class="productPriceElement">%s</div>
                <img src="https://plesioimages.example/600x600/5348978_1.jpg" class="productMainLargeImage" alt="">
                """.formatted(availability, price), "https://plesio.bg/");
    }
}
