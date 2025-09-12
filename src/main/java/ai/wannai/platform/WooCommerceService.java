package ai.wannai.platform;

import ai.wannai.platform.entities.Order;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WooCommerceService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${woocommerce.api-url}")
    private String apiUrl;

    @Value("${woocommerce.basic-username}")
    private String consumerKey;

    @Value("${woocommerce.basic-password}")
    private String consumerSecret;

    /* -------------------- ORDERS -------------------- */

    public Long sendOrderToWooCommerce(Order order) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("payment_method", "bacs");
        payload.put("payment_method_title", "Bank Transfer");
        payload.put("set_paid", false);
        payload.put("currency", order.getCurrency());

        // Billing
        order.getAddresses().stream()
                .filter(a -> "billing".equalsIgnoreCase(a.getType()))
                .findFirst()
                .ifPresent(a -> payload.put("billing", Map.of(
                        "first_name", a.getFirstName(),
                        "last_name",  a.getLastName(),
                        "address_1",  a.getAddress1(),
                        "city",       a.getCity(),
                        "postcode",   a.getPostcode(),
                        "country",    a.getCountry(),
                        "email",      a.getEmail(),
                        "phone",      a.getPhone()
                )));

        // Shipping
        order.getAddresses().stream()
                .filter(a -> "shipping".equalsIgnoreCase(a.getType()))
                .findFirst()
                .ifPresent(a -> payload.put("shipping", Map.of(
                        "first_name", a.getFirstName(),
                        "last_name",  a.getLastName(),
                        "address_1",  a.getAddress1(),
                        "city",       a.getCity(),
                        "postcode",   a.getPostcode(),
                        "country",    a.getCountry()
                )));

        // Line items
        List<Map<String, Object>> lineItems = order.getItems().stream()
                .map(i -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("product_id", i.getProductId());
                    m.put("quantity",   i.getQuantity());
                    // Woo ignores "price" on create; price comes from product.
                    // If you use custom totals, you can send "total" as string.
                    if (i.getPrice() != null) {
                        m.put("subtotal", String.valueOf(i.getPrice() * i.getQuantity()));
                        m.put("total",    String.valueOf(i.getPrice() * i.getQuantity()));
                    }
                    return m;
                })
                .toList();
        payload.put("line_items", lineItems);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, authJsonHeaders());
        // ✅ POST to /orders (not the base URL)
        String url = UriComponentsBuilder.fromHttpUrl(apiUrl).path("/orders").toUriString();

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Object id = response.getBody().get("id");
            if (id instanceof Number n) return n.longValue();
        }
        throw new RuntimeException("Failed to send order to WooCommerce: " + response.getStatusCode());
    }

    /* -------------------- PRODUCTS (names) -------------------- */

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProductLite(Long id, String name) {}

    /** Fetch a single product name by ID via GET /products/{id}. */
    public Optional<String> fetchProductNameById(Long productId) {
        if (productId == null) return Optional.empty();
        String url = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .path("/products/{id}")
                .buildAndExpand(productId)
                .toUriString();

        try {
            ResponseEntity<ProductLite> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authJsonHeaders()), ProductLite.class);
            ProductLite p = resp.getBody();
            return Optional.ofNullable(p == null ? null : p.name());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty(); // product doesn’t exist
        }
    }

    /**
     * Fetch names for a set of IDs via GET /products?include={ids}&per_page=… .
     * Woo supports up to 100 per request; we chunk accordingly.
     */
    public Map<Long, String> fetchNamesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();

        final int chunkSize = 100;
        List<Long> all = new ArrayList<>(new HashSet<>(ids)); // uniq
        Map<Long, String> out = new HashMap<>();

        for (int i = 0; i < all.size(); i += chunkSize) {
            List<Long> chunk = all.subList(i, Math.min(i + chunkSize, all.size()));

            String includeCsv = chunk.stream().map(String::valueOf).collect(Collectors.joining(","));
            String url = UriComponentsBuilder.fromHttpUrl(apiUrl)
                    .path("/products")
                    .queryParam("include", includeCsv) // keep commas
                    .queryParam("per_page", chunk.size())
                    .queryParam("status", "any")        // include drafts/hidden if needed
                    .build(true)                        // true = don’t encode commas
                    .toUriString();

            ResponseEntity<ProductLite[]> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authJsonHeaders()), ProductLite[].class);

            ProductLite[] arr = resp.getBody();
            if (arr != null) {
                for (ProductLite p : arr) {
                    if (p != null && p.id() != null && p.name() != null) {
                        out.put(p.id(), p.name());
                    }
                }
            }
        }
        return out;
    }

    /* -------------------- helpers -------------------- */

    private HttpHeaders authJsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String auth = consumerKey + ":" + consumerSecret;
        String encoded = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        h.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        return h;
    }
}
