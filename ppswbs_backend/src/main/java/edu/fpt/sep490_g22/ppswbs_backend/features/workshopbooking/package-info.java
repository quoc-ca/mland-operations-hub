@org.springframework.modulith.ApplicationModule(
    id = "workshopbooking",
    displayName = "Workshop Booking",
    allowedDependencies = { "workshopoperations :: facade", "accountmanagement :: facade" }
)
package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking;
