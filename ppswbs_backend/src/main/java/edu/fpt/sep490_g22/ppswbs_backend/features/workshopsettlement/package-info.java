@org.springframework.modulith.ApplicationModule(
        id = "workshopsettlement",
        displayName = "Workshop Settlement and Changes",
        allowedDependencies = {
                "accountmanagement :: facade",
                "workshopbooking :: facade",
                "workshopoperations :: facade",
                "loyaltypromotion :: facade",
                "common :: payment"
        }
)
package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement;
