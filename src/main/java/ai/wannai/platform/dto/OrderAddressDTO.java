package ai.wannai.platform.dto;

import lombok.Data;

@Data
public class OrderAddressDTO {
    private String type;
    private String firstName;
    private String lastName;
    private String company;
    private String address1;
    private String address2;
    private String city;
    private String state;
    private String postcode;
    private String country;
    private String phone;
    private String email;
}
