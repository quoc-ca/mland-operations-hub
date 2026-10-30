@org.springframework.modulith.ApplicationModule(
    id = "readyringsales",
    displayName = "Ready Ring Sales",
    allowedDependencies = {"accountmanagement :: facade", "productmanagement :: facade", "loyaltypromotion :: facade"}
)
package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales;
