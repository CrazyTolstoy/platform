package ai.wannai.platform;

import ai.wannai.platform.dto.OrderRequest;
import ai.wannai.platform.entities.Order;
import ai.wannai.platform.entities.OrderAddress;
import ai.wannai.platform.entities.OrderItem;
import ai.wannai.platform.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderRepository orderRepository;
    private final WooCommerceService wooCommerceService;


    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<Order>> getAllOrders() {
        // Load orders (JPA -> Supabase)
        List<Order> orders = orderRepository.findAll();

        // Collect ALL product IDs (even if an item already has a name)
        Set<Long> productIds = orders.stream()
                .flatMap(o -> o.getItems().stream())
                .map(OrderItem::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!productIds.isEmpty()) {
            try {
                // Always fetch (and overwrite) names from Woo
                Map<Long, String> idToName = wooCommerceService.fetchNamesByIds(productIds);
                orders.forEach(o -> o.getItems().forEach(i -> {
                    Long pid = i.getProductId();
                    if (pid != null) {
                        String freshName = idToName.get(pid);
                        if (freshName != null && !freshName.isBlank()) {
                            i.setName(freshName); // overwrite whatever was there
                        }
                    }
                }));
            } catch (Exception ex) {
                log.warn("Failed to fetch product names from WooCommerce", ex);
            }
        }

        return ResponseEntity.ok(orders);
    }



        @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody OrderRequest request) {
        Order order = new Order();
        order.setSource(request.getSource());
        order.setCustomerName(request.getCustomerName());
        order.setCustomerEmail(request.getCustomerEmail());
        order.setCustomerPhone(request.getCustomerPhone());
        order.setTotal(request.getTotal());
        order.setCurrency(request.getCurrency());

        // Items
        List<OrderItem> items = request.getItems().stream().map(dto -> {
            OrderItem item = new OrderItem();
            item.setProductId(dto.getProductId());
            item.setQuantity(dto.getQuantity());
            item.setPrice(dto.getPrice());
            item.setOrder(order);
            return item;
        }).toList();

        // Addresses
        List<OrderAddress> addresses = request.getAddresses().stream().map(dto -> {
            OrderAddress addr = new OrderAddress();
            addr.setType(dto.getType());
            addr.setFirstName(dto.getFirstName());
            addr.setLastName(dto.getLastName());
            addr.setCompany(dto.getCompany());
            addr.setAddress1(dto.getAddress1());
            addr.setAddress2(dto.getAddress2());
            addr.setCity(dto.getCity());
            addr.setState(dto.getState());
            addr.setPostcode(dto.getPostcode());
            addr.setCountry(dto.getCountry());
            addr.setPhone(dto.getPhone());
            addr.setEmail(dto.getEmail());
            addr.setOrder(order);
            return addr;
        }).toList();

        order.setItems(items);
        order.setAddresses(addresses);

        Order savedOrder = orderRepository.save(order);
        return ResponseEntity.ok(savedOrder);
    }



    @PostMapping("/{id}/send")
    public ResponseEntity<Order> sendOrderToWoo(@PathVariable Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));

        try {
            Long wooId = wooCommerceService.sendOrderToWooCommerce(order);
            order.setWoocommerceOrderId(wooId);
            order.setStatus("sent");
            order = orderRepository.save(order);
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            order.setStatus("failed");
            orderRepository.save(order);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(order);
        }
    }
}
