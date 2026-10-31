@org.springframework.modulith.ApplicationModule(
        id = "workshopsettlement",
        displayName = "Workshop Settlement and Changes",
        allowedDependencies = {
                "accountmanagement :: facade",
                "workshopbooking :: facade",
                "workshopoperations :: facade"
        }
)
package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement;
