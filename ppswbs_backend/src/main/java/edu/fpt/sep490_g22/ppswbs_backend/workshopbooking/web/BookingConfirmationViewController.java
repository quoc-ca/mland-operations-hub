package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.web;

import edu.fpt.sep490_g22.ppswbs_backend.common.ApiException;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.application.WorkshopBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/workshop-bookings")
@RequiredArgsConstructor
public class BookingConfirmationViewController {

    private final WorkshopBookingService bookingService;

    @GetMapping("/{bookingCode}/confirm-email")
    public String confirmEmailPage(
            @PathVariable("bookingCode") String bookingCode,
            @RequestParam("token") String token,
            Model model) {
        try {
            bookingService.confirmBookingEmail(bookingCode, token);
            model.addAttribute("success", true);
            model.addAttribute("bookingCode", bookingCode);
            model.addAttribute("message", "Email của bạn đã được xác nhận thành công. Bạn hiện có thể tiến hành đặt cọc.");
        } catch (ApiException e) {
            model.addAttribute("success", false);
            model.addAttribute("bookingCode", bookingCode);
            model.addAttribute("message", e.getMessage());
        }
        return "workshop-bookings/confirm-email-result";
    }
}
