import os
import subprocess
import json

TABLE_DESCRIPTIONS = {
    # 1. Account & Security
    'accounts': "Stores core user accounts and credentials, bridging Firebase Authentication with system roles and status lifecycles.",
    'account_profiles': "Stores personal profile and contact information for user accounts.",
    'account_location_assignments': "Associates staff and manager accounts with physical workshop locations for branch-scoped permissions.",
    'account_audit_events': "Audits administrative account lifecycle actions such as role changes, suspensions, and password resets.",
    'internal_account_provisionings': "Tracks administrative account invitations, setup token links, and initial onboarding state.",

    # 2. AI Chatbox & Custom Design Review
    'ai_text_interactions': "Logs customer and staff AI chat interactions for prompt tracing and recommendation context.",
    'booking_design_requests': "Captures customer-submitted custom ring design requests associated with workshop booking sessions.",
    'design_requests': "Stores independent custom ring design requests submitted by members outside standard bookings.",
    'temporary_image_references': "Maintains transient image upload references and storage keys before final design confirmation.",
    'image_consents': "Records customer privacy and usage consents for design reference images and CAD analysis.",
    'design_analysis_results': "Stores AI/LLM gemstone, ring dimension, and manufacturing feasibility analysis results.",
    'price_suggestions': "Stores algorithmic component-based pricing estimates for analyzed custom ring designs.",
    'human_review_decisions': "Records staff goldsmith and manager approval or rejection decisions on custom design requests.",

    # 3. Workshop Operations & Scheduling
    'workshop_locations': "Defines physical workshop branch locations, addresses, and operational availability flags.",
    'workshop_slots': "Defines recurring daily time slots and baseline capacity for workshop session scheduling.",
    'workshop_packages': "Maintains workshop tier packages (e.g. Standard, Premium) and base pricing definitions.",
    'workshop_sessions': "Represents specific calendar date and time instances of scheduled workshop sessions.",
    'session_operational_leads': "Designates the primary lead staff member responsible for an operational workshop session.",
    'session_staff_assignments': "Tracks instructors and support staff assigned to assist in a workshop session.",
    'workshop_addons': "Catalogs available workshop add-ons (extra gemstone, engraving, plating) selectable during booking.",
    'capacity_holds': "Temporarily reserves workshop bench seats during checkout to prevent overbooking.",
    'attendance_tickets': "Stores issued workshop admittance tickets and access passes for participants.",
    'ticket_session_entitlements': "Links individual workshop tickets to specific session seat allocations.",
    'attendance_actions': "Records real-time attendee check-in and completion actions taken by workshop staff.",
    'ticket_resend_throttles': "Rate-limits ticket email and SMS re-delivery requests to prevent notification abuse.",
    'workshop_operations_idempotency_ledger': "Ensures idempotency for workshop operational commands and bench reallocations.",
    'operations_audit_events': "Logs operational interventions, schedule shifts, and emergency capacity adjustments.",

    # 4. Workshop Booking & Reservations
    'workshop_bookings': "Core aggregate storing workshop reservation header records, customer references, and lifecycle states.",
    'booking_capacity_holds': "Links active booking sessions to capacity hold reservations during payment pending windows.",
    'booking_session_commitments': "Formalizes confirmed bench commitments for a customer booking within a session.",
    'booking_tickets': "Associates generated customer attendance tickets with the parent workshop booking.",
    'booking_ticket_session_entitlements': "Maps booking tickets to session entitlements and attendee identity credentials.",
    'member_workshop_booking_quotes': "Maintains time-limited price quotes and discount calculations for member bookings.",
    'booking_payment_attempts': "Records individual payment transactions and gateway checkout attempts for a booking.",
    'booking_payment_receipts': "Stores immutable proof of payment receipts issued for booking deposits and balances.",
    'booking_invoices': "Stores formal financial invoice metadata and VAT records for workshop bookings.",
    'booking_late_payment_evidence': "Captures audit evidence and staff notes when late payments are conditionally accepted.",
    'booking_magic_links': "Generates cryptographically signed magic links for guest booking lookup and ticket retrieval.",
    'booking_notification_intents': "Queues automated confirmation, reminder, and schedule change notifications.",
    'booking_deposit_reconciliations': "Reconciles incoming bank/VNPay deposit transactions against pending bookings.",
    'booking_audit_evidence': "Stores historical event logs and evidence snapshots for booking lifecycle transitions.",
    'booking_idempotency_records': "Guarantees at-most-once processing for booking creation and modification requests.",
    'workshop_booking_package_policy_snapshots': "Snapshots package terms and cancellation policies at the exact moment of booking.",

    # 5. Workshop Cancellation & Settlement
    'cancellation_handoffs': "Coordinates cancellation requests transitioning from booking/operations into settlement.",
    'workshop_cancellation_outbox': "Transactional outbox for reliable asynchronous delivery of workshop cancellation events.",
    'settlement_cases': "Master settlement dispute and financial reconciliation case management aggregate.",
    'settlement_cancellation_cases': "Specialized settlement records handling booking cancellations and customer refunds.",
    'settlement_charges': "Records itemized service, material, and late cancellation charges assessed during settlement.",
    'settlement_addon_bills': "Itemizes workshop add-on materials and tools consumed during a workshop session.",
    'settlement_shop_addon_bills': "Tracks additional in-shop merchandise purchased by attendees during the workshop.",
    'settlement_final_bills': "Summarizes final outstanding balance calculations required before customer release.",
    'settlement_final_payment_attempts': "Logs payment attempts for final workshop checkout balances and consumed addons.",
    'settlement_refund_allocations': "Tracks monetary refund disbursement breakdown between customer and shop accounts.",
    'settlement_refund_policy_revisions': "Maintains versioned business rules and percentage tiers for workshop refunds.",
    'settlement_refund_policy_snapshots': "Snapshots applicable refund policy terms at the time of cancellation evaluation.",
    'settlement_booking_mland_refund_policy_snapshots': "Stores Mland-specific refund terms snapshot bound to the booking.",
    'settlement_customer_cancellation_snapshots': "Preserves customer-stated cancellation reason, timestamps, and request context.",
    'settlement_mland_remedy_snapshots': "Captures remedy offers (reschedule voucher, partial refund) presented to customer.",
    'settlement_evidence_conclusions': "Records staff investigative conclusions and evidence documentation for disputed claims.",
    'settlement_business_audits': "Audits financial adjustments, fee waivers, and manual override decisions in settlement.",
    'settlement_notification_handoffs': "Queues customer and accounting notification handoffs resulting from settlement.",
    'settlement_handoff_idempotency': "Prevents duplicate handoff execution between operations and settlement aggregates.",
    'settlement_idempotency_outcomes': "Persists cached results for repeated settlement transaction requests.",

    # 6. Loyalty & Promotion
    'loyalty_registry': "Singleton system gate controlling global loyalty reward policies and concurrent modifications.",
    'loyalty_policy': "Defines versioned point accrual ratios, redemption rates, and qualification rules.",
    'loyalty_wallet': "Maintains member reward point balances, tier qualifications, and active point reserves.",
    'loyalty_point_ledger': "Append-only double-entry ledger recording all loyalty point earned, spent, and expired transactions.",
    'loyalty_accepted_benefit': "Tracks specific discount benefits and loyalty rewards applied to orders and bookings.",
    'loyalty_audit': "Audits point adjustments, fraud investigations, and manual wallet credits.",
    'promotion_program': "Defines marketing promotional campaigns, valid date windows, and budget caps.",
    'promotion_revision': "Stores immutable revisions of promotional rules and discount percentage configurations.",
    'promotion_revision_target': "Specifies target audience criteria, product categories, and branch filters for promotions.",
    'promotion_member_program': "Tracks individual member voucher claims, redemption counters, and usage limits.",

    # 7. Product Catalogue Management
    'catalogue_categories': "Maintains hierarchical merchandise and jewelry product categories.",
    'catalogue_products': "Stores primary product catalog definitions, brand metadata, and master specifications.",
    'catalogue_variants': "Maintains specific purchasable SKU variants (ring size, metal alloy, gemstone color).",
    'catalogue_media_objects': "Catalogs digital media files, high-resolution imagery, and CAD render assets.",
    'catalogue_media_references': "Links media objects to products and variant presentations.",
    'catalogue_product_images': "Stores product gallery images, display sequences, and thumbnail URLs.",
    'catalogue_price_proposals': "Drafts proposed retail price changes submitted for managerial approval.",
    'catalogue_price_applications': "Records historical activations and application timestamps of approved price proposals.",
    'catalogue_product_reviews': "Stores customer product ratings, written reviews, and verified buyer badges.",
    'catalogue_import_batches': "Tracks bulk catalog import jobs, parsed spreadsheet rows, and execution status.",
    'catalogue_command_receipts': "Provides idempotency tracking for product catalog mutation commands.",
    'catalogue_audit_events': "Audits product creation, SKU decommissioning, and critical catalog modifications.",

    # 8. Ready-Ring Retail Sales & Cart
    'retail_carts': "Maintains active online shopping carts for guest and member retail sessions.",
    'retail_cart_lines': "Stores line items, variant references, and quantities within a shopping cart.",
    'retail_checkout_holds': "Temporarily holds inventory for items in checkout prior to payment completion.",
    'retail_checkout_quotes': "Generates finalized purchase quotes including shipping, discounts, and taxes.",
    'retail_checkout_quote_branches': "Tracks branch fulfillment assignments for multi-location retail orders.",
    'retail_checkout_quote_items': "Itemizes products and applied discounts within a finalized checkout quote.",
    'retail_grouped_checkouts': "Coordinates checkout grouping for orders containing multiple fulfillment streams.",
    'retail_orders': "Master aggregate storing confirmed retail orders, customer details, and payment status.",
    'retail_order_items': "Itemizes purchased product variants, prices, and fulfillment states in a retail order.",
    'retail_variant_availability_guards': "Provides optimistic and pessimistic locking guards for high-demand variant stock.",
    'retail_variant_holds': "Tracks active physical stock holds on variants across store branches.",
    'retail_pickup_evidence': "Stores digital signatures and photo evidence when items are picked up in-store.",

    # 9. Payment Integration & Merchant Operations
    'payment_owner_registrations': "Registers authorized merchant accounts and payment gateway API credentials.",
    'payment_ingress_receipts': "Logs raw incoming IPN and webhook callbacks from payment gateways for audit.",
    'vnpay_merchant_operations': "Maintains transaction logs, signature digests, and queryDR logs for VNPay operations.",

    # 10. OTA Connectivity & Multi-channel Management
    'ota_channel_connections': "Maintains API integration configurations for Online Travel Agencies (Klook, KKday, etc.).",
    'ota_channel_mappings': "Maps internal workshop packages and slots to external OTA listing IDs.",
    'ota_channel_price_snapshots': "Tracks syndicated pricing and commission schedules agreed with OTA channels.",
    'ota_channel_bookings': "Stores booking reservations ingested from external OTA partners.",
    'ota_booking_ticket_links': "Links ingested OTA bookings with internally issued admittance tickets.",
    'ota_addon_bill_links': "Associates in-workshop add-on charges with external OTA booking references.",
    'ota_customer_payment_evidence': "Records payment confirmation vouchers and settlement tokens provided by OTAs.",
    'ota_delivery_attempts': "Logs outgoing webhook transmissions and API sync calls to OTA partner endpoints.",
    'ota_inbound_events': "Ingests raw webhook events and payload messages received from OTA gateways.",
    'ota_outbound_changes': "Queues schedule, capacity, and pricing changes to be published to OTA channels.",
    'ota_control_outbox': "Transactional outbox for reliable dispatch of OTA management commands.",
    'ota_payout_ledger_entries': "Tracks net payout receivables, partner commissions, and platform deductions.",
    'ota_payout_positions': "Maintains aggregate financial balances receivable from each OTA channel.",
    'ota_readiness_assessments': "Evaluates channel readiness and automated health checks before enabling bookings.",
    'ota_readiness_check_evidence': "Stores diagnostic test logs and verification responses for OTA readiness checks.",
    'ota_reconciliation_decisions': "Records automated and manual reconciliation resolutions for booking mismatches.",
    'ota_reconciliation_work_items': "Tracks unresolved discrepancies between internal attendance and OTA reports.",
    'workshop_session_ota_snapshots': "Maintains synchronized seat availability and lock state sent to OTA channels.",

    # 11. System Infrastructure & Event Publication
    'event_publication': "Spring Modulith persistent event publication log guaranteeing at-least-once event delivery.",
    'facade_command_idempotency_records': "Global idempotency ledger for boundary API commands across architectural facades."
}

