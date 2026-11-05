@org.springframework.modulith.ApplicationModule(
    id = "workshopoperations",
    displayName = "Workshop Operations",
    allowedDependencies = { "accountmanagement :: facade", "loyaltypromotion :: facade", "productmanagement :: facade" }
)
package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations;
