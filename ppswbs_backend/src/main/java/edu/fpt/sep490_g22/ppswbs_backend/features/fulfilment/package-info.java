@org.springframework.modulith.ApplicationModule(
    id = "fulfilment",
    displayName = "Fulfilment",
    allowedDependencies = {"accountmanagement :: facade", "readyringsales :: facade"}
)
package edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment;
