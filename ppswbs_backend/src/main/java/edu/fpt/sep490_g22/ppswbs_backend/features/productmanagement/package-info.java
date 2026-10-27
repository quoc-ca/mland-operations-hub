/**
 * FE-02 Product Management: ready-made ring catalogue administration and public browsing.
 *
 * <p>Closed module. Other features may only use the {@code facade} named interface. Allowed
 * outgoing dependencies: {@code loyaltypromotion::facade} (T091, the product target provider); the
 * identity facade of 001 is added with T007. Retail ({@code readyringsales})
 * depends on this module's facade, never the reverse.
 */
@ApplicationModule(
        displayName = "Product Management (FE-02)",
        allowedDependencies = {"common", "features.loyaltypromotion :: facade"})
package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import org.springframework.modulith.ApplicationModule;
