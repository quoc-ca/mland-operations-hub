/**
 * FE-06 Loyalty &amp; Promotion: product/workshop vouchers, automatic product discounts and the shared
 * points wallet.
 *
 * <p>Closed module. Other features (retail checkout, workshop booking) may only use the
 * {@code facade} named interface. Loyalty never imports order, booking, catalogue or payment code:
 * source owners pass server-verified facts and authoritative outcomes through the facade. The
 * dependency on the identity module of 001 is added only when its facade exists (task T007).
 */
@ApplicationModule(
        displayName = "Loyalty and Promotion (FE-06)",
        allowedDependencies = {})
package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import org.springframework.modulith.ApplicationModule;
