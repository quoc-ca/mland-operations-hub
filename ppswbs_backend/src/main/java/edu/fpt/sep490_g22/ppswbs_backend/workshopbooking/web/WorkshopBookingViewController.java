package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.web;

import edu.fpt.sep490_g22.ppswbs_backend.common.ResourceNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.BookingLookupResult;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.WorkshopBookingService;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopPackage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import edu.fpt.sep490_g22.ppswbs_backend.payments.facade.PaymentGatewayPort;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.PaymentAttemptRepository;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.WorkshopBookingRepository;

@Controller
@RequestMapping("/workshop-bookings")
@RequiredArgsConstructor
public class WorkshopBookingViewController {

    private final WorkshopBookingService bookingService;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final WorkshopBookingRepository bookingRepository;

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/workshop-bookings/packages";
    }

    @GetMapping("/packages")
    public String listPackages(Model model) {
        model.addAttribute("packages", bookingService.listPublishedPackages());
        return "workshop-bookings/packages";
    }

    @GetMapping("/packages/detail")
    public String packageDetail(@RequestParam("id") Long id, Model model) {
        WorkshopPackage pkg = bookingService.getPublishedPackage(id);
        model.addAttribute("package", pkg);
        model.addAttribute("sessions", bookingService.getOpenSessionsForPackage(id));
        return "workshop-bookings/package-detail";
    }

    @GetMapping("/create")
    public String showCreateForm(
            @RequestParam("packageId") Long packageId, 
            @RequestParam(value = "sessionId", required = false) Long sessionId, 
            Model model) {
        model.addAttribute("package", bookingService.getPublishedPackage(packageId));
        model.addAttribute("sessions", bookingService.getOpenSessionsForPackage(packageId));
        if (sessionId != null) {
            model.addAttribute("selectedSessionId", sessionId);
        }
        return "workshop-bookings/create";
    }

    @GetMapping("/lookup")
    public String lookupForm(
            @RequestParam(value = "vnp_ResponseCode", required = false) String vnpResponseCode,
            @RequestParam(value = "vnp_TxnRef", required = false) String vnpTxnRef,
            @RequestParam(value = "vnp_TransactionNo", required = false) String vnpTransactionNo,
            @RequestParam(value = "vnp_Amount", required = false) String vnpAmount,
            @RequestParam(value = "vnp_SecureHash", required = false) String vnpSecureHash,
            Model model) {

        if (vnpResponseCode != null && vnpTxnRef != null) {
            try {
                paymentAttemptRepository.findByProviderReference(vnpTxnRef).ifPresent(attempt -> {
                    bookingRepository.findById(attempt.getBookingId()).ifPresent(booking -> {
                        long amount = vnpAmount != null ? Long.parseLong(vnpAmount) / 100 : attempt.getAmount();
                        PaymentGatewayPort.RawProviderCallback callback = PaymentGatewayPort.RawProviderCallback.builder()
                                .bookingCode(booking.getBookingCode())
                                .providerEventId(vnpTransactionNo != null ? vnpTransactionNo : vnpTxnRef)
                                .providerReference(vnpTxnRef)
                                .rawOutcome(vnpResponseCode)
                                .amount(amount)
                                .currency("VND")
                                .signature(vnpSecureHash != null ? vnpSecureHash : "RETURN_URL")
                                .build();
                        bookingService.applyPaymentCallback(callback);
                        model.addAttribute("autoLookupCode", booking.getBookingCode());
                        model.addAttribute("autoLookupContact", booking.getContactEmail());
                    });
                });
            } catch (Exception e) {
                // Log exception if any
            }
        }
        return "workshop-bookings/lookup";
    }

    @GetMapping("/view")
    public String viewMagicLink(@RequestParam("code") String code, @RequestParam("token") String token, Model model) {
        try {
            BookingLookupResult result = bookingService.lookupByMagicLink(code, token);
            model.addAttribute("booking", result.getBooking());
            model.addAttribute("ticket", result.getTicket());
            return "workshop-bookings/magic-link-view";
        } catch (ResourceNotFoundException e) {
            model.addAttribute("error", "Link không hợp lệ hoặc đã hết hạn.");
            return "workshop-bookings/error";
        }
    }

    @GetMapping("/mock-payment")
    public String mockPayment(
            @RequestParam("bookingCode") String bookingCode,
            @RequestParam("amount") long amount,
            @RequestParam("ref") String ref,
            Model model) {
        model.addAttribute("bookingCode", bookingCode);
        model.addAttribute("amount", amount);
        model.addAttribute("ref", ref);
        return "workshop-bookings/mock-payment";
    }
}
