@org.springframework.modulith.ApplicationModule(
    id = "otaconnectivity",
    displayName = "Workshop OTA Connectivity",
    allowedDependencies = {
        "accountmanagement :: facade",
        "workshopbooking :: facade",
        "workshopoperations :: facade",
        "workshopsettlement :: facade"
    }
)
package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity;
