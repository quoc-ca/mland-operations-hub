@org.springframework.modulith.ApplicationModule(
        id = "paymentintegration",
        displayName = "Payment Integration",
        allowedDependencies = {
                "workshopbooking :: facade",
                "workshopsettlement :: facade"
        }
)
package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration;
