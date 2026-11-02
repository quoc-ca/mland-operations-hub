/**
 * FE-02 Product Management: ready-made ring catalogue administration and public browsing.
 *
 * <p>Closed module. Other features may only use the {@code facade} named interface. Allowed
 * outgoing dependencies: {@code accountmanagement::facade} (T007, current actor) and
 * {@code loyaltypromotion::facade} (T091, the product target provider).
 * Retail ({@code readyringsales})
 * depends on this module's facade, never the reverse.
 */
@ApplicationModule(
        displayName = "Product Management (FE-02)",
        allowedDependencies = {"accountmanagement :: facade", "loyaltypromotion :: facade"})
package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import org.springframework.modulith.ApplicationModule;
