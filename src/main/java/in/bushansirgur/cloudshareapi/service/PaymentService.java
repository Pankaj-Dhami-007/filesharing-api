package in.bushansirgur.cloudshareapi.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import in.bushansirgur.cloudshareapi.document.PaymentTransaction;
import in.bushansirgur.cloudshareapi.document.ProfileDocument;
import in.bushansirgur.cloudshareapi.dto.PaymentDTO;
import in.bushansirgur.cloudshareapi.dto.PaymentVerificationDTO;
import in.bushansirgur.cloudshareapi.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Formatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final ProfileService profileService;
    private final UserCreditsService userCreditsService;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;
    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    public PaymentDTO createOrder(PaymentDTO paymentDTO) {
        try {
            log.info("Starting order creation for paymentDTO: {}", paymentDTO);
            ProfileDocument currentProfile = profileService.getCurrentProfile();
            String clerkId = currentProfile.getClerkId();
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", paymentDTO.getAmount());
            orderRequest.put("currency", paymentDTO.getCurrency());
            orderRequest.put("receipt", "order_"+System.currentTimeMillis());

            Order order = razorpayClient.orders.create(orderRequest);
            String orderId = order.get("id");
            log.info("Razorpay order created successfully with id: {}", orderId);

            //create pending transaction record
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .clerkId(clerkId)
                    .orderId(orderId)
                    .planId(paymentDTO.getPlanId())
                    .amount(paymentDTO.getAmount())
                    .currency(paymentDTO.getCurrency())
                    .status("PENDING")
                    .transactionDate(LocalDateTime.now())
                    .userEmail(currentProfile.getEmail())
                    .userName(currentProfile.getFirstName()+" "+currentProfile.getLastName())
                    .build();

            paymentTransactionRepository.save(transaction);
            log.info("Payment transaction saved with orderId: {}", orderId);

            return PaymentDTO.builder()
                    .orderId(orderId)
                    .success(true)
                    .message("Order created successfully")
                    .build();

        }catch (Exception e) {
            log.error("Error creating order: {}", e.getMessage(), e);
            return PaymentDTO.builder()
                    .success(false)
                    .message("Error creating order: "+e.getMessage())
                    .build();
        }
    }

    public PaymentDTO verifyPayment(PaymentVerificationDTO request) {
        try {
            log.info("Starting payment verification for orderId: {}, paymentId: {}", request.getRazorpay_order_id(), request.getRazorpay_payment_id());
            ProfileDocument currentProfile = profileService.getCurrentProfile();
            String clerkId = currentProfile.getClerkId();

            String data = request.getRazorpay_order_id()+ "|" +request.getRazorpay_payment_id();
            String generatedSignature = generateHmacSha256Signature(data, razorpayKeySecret);
            if (!generatedSignature.equals(request.getRazorpay_signature())) {
                log.warn("Payment signature verification failed for orderId: {}", request.getRazorpay_order_id());
                updateTransactionStatus(request.getRazorpay_order_id(), "FAILED", request.getRazorpay_payment_id(), null);
                return PaymentDTO.builder()
                        .success(false)
                        .message("Payment signature verification failed")
                        .build();
            }

            //Add credits based on plan
            int creditsToAdd = 0;
            String plan = "BASIC";

            switch (request.getPlanId()) {
                case "premium":
                    creditsToAdd = 500;
                    plan = "PREMIUM";
                    break;
                case "ultimate":
                    creditsToAdd = 5000;
                    plan = "ULTIMATE";
                    break;
                default:
                    log.warn("Invalid plan selected: {}", request.getPlanId());
                    updateTransactionStatus(request.getRazorpay_order_id(), "FAILED", request.getRazorpay_payment_id(), null);
                    return PaymentDTO.builder()
                            .success(false)
                            .message("Invalid plan selected")
                            .build();
            }

            if (creditsToAdd > 0) {
                userCreditsService.addCredits(clerkId, creditsToAdd, plan);
                updateTransactionStatus(request.getRazorpay_order_id(), "SUCCESS", request.getRazorpay_payment_id(), creditsToAdd);
                log.info("Payment verified and {} credits added for clerkId: {}", creditsToAdd, clerkId);
                return PaymentDTO.builder()
                        .success(true)
                        .message("Payment verified and credits added successfully")
                        .credits(userCreditsService.getUserCredits(clerkId).getCredits())
                        .build();
            } else {
                updateTransactionStatus(request.getRazorpay_order_id(), "FAILED", request.getRazorpay_payment_id(), null);
                return PaymentDTO.builder()
                        .success(false)
                        .message("Invalid plan selected")
                        .build();
            }
        }catch (Exception e) {
            log.error("Error verifying payment for orderId: {}, paymentId: {}. Error: {}", request.getRazorpay_order_id(), request.getRazorpay_payment_id(), e.getMessage(), e);
            try {

                updateTransactionStatus(request.getRazorpay_order_id(), "ERROR", request.getRazorpay_payment_id(), null);
            } catch (Exception ex) {
                log.error("Error updating transaction status to ERROR: {}", ex.getMessage(), ex);
                throw new RuntimeException(ex);
            }
            return PaymentDTO.builder()
                    .success(false)
                    .message("Error verifying payment:"+e.getMessage())
                    .build();
        }
    }

    private void updateTransactionStatus(String razorpayOrderId, String status, String razorpayPaymentId, Integer creditsToAdd) {
        log.info("Updating transaction status to {} for orderId: {}", status, razorpayOrderId);
        paymentTransactionRepository.findAll().stream()
                .filter(t -> t.getOrderId() != null && t.getOrderId().equals(razorpayOrderId))
                .findFirst()
                .map(transaction -> {
                    transaction.setStatus(status);
                    transaction.setPaymentId(razorpayPaymentId);
                    if (creditsToAdd != null) {
                        transaction.setCreditsAdded(creditsToAdd);
                    }
                    PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);
                    log.info("Transaction updated: {}", savedTransaction);
                    return savedTransaction;
                })
                .orElseGet(() -> {
                    log.warn("No transaction found for orderId: {}", razorpayOrderId);
                    return null;
                });
    }

    /**
     * Generate HMAC SHA256 signature for payment verification
     */
    private String generateHmacSha256Signature(String data, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKey);

        byte[] hmacData = mac.doFinal(data.getBytes());

        return toHexString(hmacData);
    }

    private String toHexString(byte[] bytes) {
        Formatter formatter = new Formatter();
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }
}
