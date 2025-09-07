package ai.wannai.platform.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "external_order_addresses")
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class OrderAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;      // shipping or billing
    private String firstName;
    private String lastName;
    private String company;

    @Column(name = "address_1")   // <-- important
    private String address1;

    @Column(name = "address_2")   // <-- important
    private String address2;

    private String city;
    private String state;
    private String postcode;
    private String country;
    private String phone;
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonBackReference("order-addresses")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;
}
