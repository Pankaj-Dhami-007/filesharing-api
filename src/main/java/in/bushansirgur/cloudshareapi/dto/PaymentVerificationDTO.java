package in.bushansirgur.cloudshareapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentVerificationDTO {

    private String razorpay_order_id;
    private String razorpay_payment_id;
    private String razorpay_signature;
    private String planId;
}
