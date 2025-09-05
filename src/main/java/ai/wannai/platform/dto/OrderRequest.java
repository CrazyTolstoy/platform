package ai.wannai.platform.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest {
    private String source;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private Double total;
    private String currency;
    private List<OrderItemDTO> items;
    private List<OrderAddressDTO> addresses;
}
