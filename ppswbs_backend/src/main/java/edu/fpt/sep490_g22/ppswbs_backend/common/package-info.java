/**
 * Technical kernel shared by all features: error shape and request correlation only.
 * It must not own business entities, business DTOs or workflows (constitution I).
 */
@ApplicationModule(displayName = "Common technical kernel", type = ApplicationModule.Type.OPEN)
package edu.fpt.sep490_g22.ppswbs_backend.common;

import org.springframework.modulith.ApplicationModule;
