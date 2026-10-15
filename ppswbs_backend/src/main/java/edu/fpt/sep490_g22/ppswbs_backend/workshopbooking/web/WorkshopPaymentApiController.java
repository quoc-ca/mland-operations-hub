package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.web;

import edu.fpt.sep490_g22.ppswbs_backend.common.InvalidRequestException;
import edu.fpt.sep490_g22.ppswbs_backend.payments.facade.PaymentGatewayPort;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.CallbackApplicationResult;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.WorkshopBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/workshop-bookings")
@RequiredArgsConstructor
public class WorkshopPaymentApiController {

    private final WorkshopBookingService bookingService;

    @PostMapping("/{bookingCode}/payments")
    public ResponseEntity<PaymentGatewayPort.PaymentInitiationResult> startPayment(
            @PathVariable("bookingCode") String bookingCode,
            @RequestParam(value = "contactPhone", required = false) String contactPhone,
            @RequestParam(value = "contactEmail", required = false) String contactEmail) {

        if ((contactPhone == null || contactPhone.isBlank()) && (contactEmail == null || contactEmail.isBlank())) {
            throw new InvalidRequestException("Either contactPhone or contactEmail must be provided for Guest");
        }
        if (contactPhone != null && !contactPhone.isBlank() && contactEmail != null && !contactEmail.isBlank()) {
            throw new InvalidRequestException("Provide exactly one of contactPhone or contactEmail for Guest");
        }

        // Member ID is null for Guest
        var result = bookingService.startPayment(bookingCode, null, contactPhone, contactEmail);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{bookingCode}/payment-callbacks")
    public ResponseEntity<Map<String, Object>> paymentCallback(
            @PathVariable("bookingCode") String bookingCode,
            @RequestHeader("X-Payment-Signature") String signature,
            @RequestBody PaymentGatewayPort.RawProviderCallback callback) {

        callback.setBookingCode(bookingCode);
        callback.setSignature(signature);

        CallbackApplicationResult result = bookingService.applyPaymentCallback(callback);

        return ResponseEntity.ok(Map.of(
                "accepted", true,
                "bookingStatus", result.getBooking().getBookingStatus().name(),
                "paymentStatus", result.getBooking().getPaymentStatus().name()
        ));
    }
}
