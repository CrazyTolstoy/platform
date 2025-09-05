package ai.wannai.platform;

import ai.wannai.platform.entities.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WooCommerceService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${woocommerce.api-url}")
    private String apiUrl;

    @Value("${woocommerce.consumer-key}")
    private String consumerKey;

    @Value("${woocommerce.consumer-secret}")
    private String consumerSecret;

    public Long sendOrderToWooCommerce(Order order) {
        // Map our Order entity to WooCommerce JSON format
        Map<String, Object> payload = new HashMap<>();
        payload.put("payment_method", "bacs");   // or your method
        payload.put("payment_method_title", "Bank Transfer");
        payload.put("set_paid", false);
        payload.put("currency", order.getCurrency());

        // Billing (first billing address)
        order.getAddresses().stream()
                .filter(addr -> "billing".equals(addr.getType()))
                .findFirst()
                .ifPresent(addr -> payload.put("billing", Map.of(
                        "first_name", addr.getFirstName(),
                        "last_name", addr.getLastName(),
                        "address_1", addr.getAddress1(),
                        "city", addr.getCity(),
                        "postcode", addr.getPostcode(),
                        "country", addr.getCountry(),
                        "email", addr.getEmail(),
                        "phone", addr.getPhone()
                )));

        // Shipping (first shipping address)
        order.getAddresses().stream()
                .filter(addr -> "shipping".equals(addr.getType()))
                .findFirst()
                .ifPresent(addr -> payload.put("shipping", Map.of(
                        "first_name", addr.getFirstName(),
                        "last_name", addr.getLastName(),
                        "address_1", addr.getAddress1(),
                        "city", addr.getCity(),
                        "postcode", addr.getPostcode(),
                        "country", addr.getCountry()
                )));

        // Line items
        List<Map<String, Object>> lineItems = order.getItems().stream()
                .map(item -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("product_id", item.getProductId());
                    map.put("quantity", item.getQuantity());
                    map.put("price", item.getPrice());
                    return map;
                })
                .toList();

        payload.put("line_items", lineItems);

        // Build request with Basic Auth
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String auth = consumerKey + ":" + consumerSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedAuth);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, request, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Number wooId = (Number) response.getBody().get("id");
            return wooId != null ? wooId.longValue() : null;
        }
        throw new RuntimeException("Failed to send order to WooCommerce: " + response.getStatusCode());
    }
}

