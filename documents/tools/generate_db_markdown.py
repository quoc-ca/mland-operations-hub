import os
import sys
import subprocess
import re

# Add current directory to path
script_dir = os.path.dirname(os.path.abspath(__file__))
if script_dir not in sys.path:
    sys.path.insert(0, script_dir)

from inspect_db_schema import get_db_metadata
from table_descriptions import TABLE_DESCRIPTIONS

DOMAINS = [
    {
        'title': 'Account & Identity Management',
        'description': 'Handles user credentials, RBAC roles (Member, Staff, Manager, Admin), branch staff location assignments, session revocation, and administrative provisioning.',
        'tables': [
            'accounts',
            'account_profiles',
            'account_location_assignments',
            'account_audit_events',
            'internal_account_provisionings'
        ]
    },
    {
        'title': 'AI Chatbox & Custom Design Review',
        'description': 'Supports AI-assisted ring design consultation, image upload and consent verification, automated CAD/material analysis, price suggestions, and goldsmith review decisions.',
        'tables': [
            'ai_text_interactions',
            'booking_design_requests',
            'design_requests',
            'temporary_image_references',
            'image_consents',
            'design_analysis_results',
            'price_suggestions',
            'human_review_decisions'
        ]
    },
    {
        'title': 'Workshop Operations & Scheduling',
        'description': 'Manages physical branch workshops, recurring slot templates, daily session schedules, operational leads, instructor assignments, bench capacity holds, add-on catalogs, attendance tickets, and check-in actions.',
        'tables': [
            'workshop_locations',
            'workshop_slots',
            'workshop_packages',
            'workshop_sessions',
            'session_operational_leads',
            'session_staff_assignments',
            'workshop_addons',
            'capacity_holds',
            'attendance_tickets',
            'ticket_session_entitlements',
            'attendance_actions',
            'ticket_resend_throttles',
            'operations_audit_events'
        ]
    },
    {
        'title': 'Workshop Booking & Reservations',
        'description': 'Provides customer booking aggregate management, seat commitments, quote calculations, deposit and balance payment tracking, VAT invoices, guest magic link delivery, late payment handling, and deposit reconciliation.',
        'tables': [
            'workshop_bookings',
            'booking_capacity_holds',
            'booking_session_commitments',
            'booking_tickets',
            'booking_ticket_session_entitlements',
            'member_workshop_booking_quotes',
            'booking_payment_attempts',
            'booking_payment_receipts',
            'booking_invoices',
            'booking_late_payment_evidence',
            'booking_magic_links',
            'booking_notification_intents',
            'booking_deposit_reconciliations',
            'booking_audit_evidence',
            'booking_idempotency_records',
            'workshop_booking_package_policy_snapshots'
        ]
    },
    {
        'title': 'Workshop Cancellation & Settlement',
        'description': 'Executes multi-phase cancellation workflows, fee assessments, consumption billing for in-workshop addons, final checkout balance collection, refund disbursements, policy snapshot enforcement, and financial audit logging.',
        'tables': [
            'cancellation_handoffs',
            'workshop_cancellation_outbox',
            'settlement_cases',
            'settlement_cancellation_cases',
            'settlement_charges',
            'settlement_addon_bills',
            'settlement_final_bills',
            'settlement_final_payment_attempts',
            'settlement_refund_allocations',
            'settlement_refund_policy_revisions',
            'settlement_refund_policy_snapshots',
            'settlement_booking_mland_refund_policy_snapshots',
            'settlement_customer_cancellation_snapshots',
            'settlement_mland_remedy_snapshots',
            'settlement_evidence_conclusions',
            'settlement_business_audits',
            'settlement_notification_handoffs',
            'settlement_handoff_idempotency',
            'settlement_idempotency_outcomes'
        ]
    },
    {
        'title': 'Loyalty Program & Promotions',
        'description': 'Operates double-entry reward points ledgers, singleton registry concurrency gates, marketing campaign programs, target audience scoping, member vouchers, and applied booking discounts.',
        'tables': [
            'loyalty_registry',
            'loyalty_policy',
            'loyalty_wallet',
            'loyalty_point_ledger',
            'loyalty_accepted_benefit',
            'loyalty_audit',
            'promotion_program',
            'promotion_revision',
            'promotion_revision_target',
            'promotion_member_program'
        ]
    },
    {
        'title': 'Product Catalogue Management',
        'description': 'Maintains retail categories, product specifications, physical variant SKUs (size, metal, gemstone), digital media assets, proposed price adjustments, customer reviews, and bulk import batches.',
        'tables': [
            'catalogue_categories',
            'catalogue_products',
            'catalogue_variants',
            'catalogue_media_objects',
            'catalogue_media_references',
            'catalogue_product_images',
            'catalogue_price_proposals',
            'catalogue_price_applications',
            'catalogue_product_reviews',
            'catalogue_import_batches',
            'catalogue_command_receipts',
            'catalogue_audit_events'
        ]
    },
    {
        'title': 'Ready-Ring Retail Sales & Cart',
        'description': 'Supports member and guest shopping carts, checkout inventory reservation holds, multi-branch quote aggregation, retail order management, stock availability guards, and in-store pickup photo evidence.',
        'tables': [
            'retail_carts',
            'retail_cart_lines',
            'retail_checkout_holds',
            'retail_checkout_quotes',
            'retail_checkout_quote_branches',
            'retail_checkout_quote_items',
            'retail_grouped_checkouts',
            'retail_orders',
            'retail_order_items',
            'retail_variant_availability_guards',
            'retail_variant_holds',
            'retail_pickup_evidence'
        ]
    },
    {
        'title': 'Payment Integration & Merchant Operations',
        'description': 'Manages payment gateway configurations, merchant credentials, raw ingress webhook receipts, and VNPay merchant verification queries and signature auditing.',
        'tables': [
            'payment_owner_registrations',
            'payment_ingress_receipts',
            'vnpay_merchant_operations'
        ]
    },
    {
        'title': 'OTA Connectivity & Multi-channel Management',
        'description': 'Coordinates Online Travel Agency (Klook, KKday) API connections, listing mappings, syndicated pricing, inbound bookings, external voucher reconciliation, channel payout ledgers, and health readiness assessments.',
        'tables': [
            'ota_channel_connections',
            'ota_channel_mappings',
            'ota_channel_price_snapshots',
            'ota_channel_bookings',
            'ota_booking_ticket_links',
            'ota_addon_bill_links',
            'ota_customer_payment_evidence',
            'ota_delivery_attempts',
            'ota_inbound_events',
            'ota_outbound_changes',
            'ota_control_outbox',
            'ota_payout_ledger_entries',
            'ota_payout_positions',
            'ota_readiness_assessments',
            'ota_readiness_check_evidence',
            'ota_reconciliation_decisions',
            'ota_reconciliation_work_items',
            'workshop_session_ota_snapshots'
        ]
    },
    {
        'title': 'System Integration & Infrastructure',
        'description': 'Provides architectural event publishing guarantees via Spring Modulith and boundary idempotency ledgers across API facades.',
        'tables': [
            'event_publication',
            'facade_command_idempotency_records'
        ]
    }
]

def format_constraint(col, fks, uqs):
    tbl = col['TABLE_NAME'].lower()
    col_name = col['COLUMN_NAME'].lower()
    
    constraints = []
    
    # PK
    if col['COLUMN_KEY'] == 'PRI':
        if 'auto_increment' in col['EXTRA'].lower():
            constraints.append('PK, AUTO_INCREMENT')
        else:
            constraints.append('PK, NOT NULL')
    
    # FK
    if (tbl, col_name) in fks:
        ref = fks[(tbl, col_name)]
        constraints.append(f'FK ({ref})')
    
    # UNIQUE (if not PK)
    if (col['COLUMN_KEY'] == 'UNI' or (tbl, col_name) in uqs) and col['COLUMN_KEY'] != 'PRI':
        constraints.append('UNIQUE')
            
    # NOT NULL / NULL
    if col['IS_NULLABLE'] == 'NO':
        if col['COLUMN_KEY'] != 'PRI':
            constraints.append('NOT NULL')
    else:
        if not constraints:
            constraints.append('NULL')
            
    # Default value
    default_val = col['COLUMN_DEFAULT']
    if default_val is not None and default_val != 'NULL':
        if 'CURRENT_TIMESTAMP' in default_val:
            constraints.append('DEFAULT CURRENT_TIMESTAMP')
        else:
            constraints.append(f'DEFAULT {default_val}')
            
    return ', '.join(constraints)

def generate_column_note(col, fks):
    tbl = col['TABLE_NAME'].lower()
    col_name = col['COLUMN_NAME'].lower()
    col_type = col['COLUMN_TYPE'].lower()
    
    # Foreign key note
    if (tbl, col_name) in fks:
        ref = fks[(tbl, col_name)]
        ref_tbl, ref_col = ref.split('.')
        return f"Foreign key referencing {ref_tbl}({ref_col})."
        
    # Standard column patterns
    if col_name == 'id':
        if 'auto_increment' in col['EXTRA'].lower():
            return "Primary key, auto-increment identifier."
        return "Primary key unique identifier."
    if col_name in ['reference', 'public_reference', 'booking_reference', 'order_reference']:
        return "Unique external public reference identifier."
    if col_name == 'created_at':
        return "Record creation timestamp (UTC)."
    if col_name == 'updated_at':
        return "Record last update timestamp (UTC)."
    if col_name == 'version':
        return "Optimistic concurrency locking version counter."
    if col_name in ['status', 'state']:
        return f"Operational lifecycle state ({col_type})."
    if col_name == 'active':
        return "Active status indicator (1=active, 0=inactive)."
    if col_name == 'idempotency_key':
        return "Unique idempotency key for safe retry execution."
    if col_name in ['created_by', 'created_by_admin_id', 'created_by_account_id']:
        return "Identifier of the actor or administrator who created the record."
    if col_name in ['notes', 'description', 'remark', 'remarks']:
        return "Descriptive details, business remarks, or explanatory notes."
    if col_name in ['code']:
        return "Unique human-readable business identifier code."
    if col_name in ['firebase_uid']:
        return "Unique Firebase Authentication user identifier."
    if col_name in ['login_email', 'email']:
        return "Contact or authentication email address."
    if col_name in ['full_name', 'name', 'display_name']:
        return "Human-readable name or title."
    if col_name in ['phone']:
        return "Contact telephone number."
    if col_name in ['role']:
        return "Assigned access control role in RBAC hierarchy."
    if col_name in ['currency', 'currency_code']:
        return "ISO-4217 standard currency code (e.g., VND)."
    if col_name.startswith('is_') or col_name.startswith('has_'):
        return f"Boolean flag indicating whether {col_name.replace('_', ' ')}."
    if 'amount' in col_name or 'price' in col_name or 'fee' in col_name or 'cost' in col_name or 'total' in col_name or 'deposit' in col_name:
        return f"Monetary value or calculated fee ({col_name.replace('_', ' ')})."
    if 'quantity' in col_name or 'count' in col_name or 'capacity' in col_name or 'seats' in col_name or 'quota' in col_name or 'points' in col_name:
        return f"Numerical count, quota, or capacity ({col_name.replace('_', ' ')})."
    if 'date' in col_name:
        return f"Calendar date for {col_name.replace('_', ' ')}."
    if col_name.endswith('_at') or 'timestamp' in col_name or 'deadline' in col_name or 'expiry' in col_name or 'expires' in col_name:
        return f"Timestamp recording when {col_name.replace('_', ' ')}."
    if 'json' in col_name or 'payload' in col_name or 'snapshot' in col_name or 'metadata' in col_name or 'data' in col_name:
        return f"Serialized JSON or snapshot data for {col_name.replace('_', ' ')}."
    if 'url' in col_name or 'uri' in col_name or 'path' in col_name or 'link' in col_name:
        return f"Network URL, endpoint path, or storage link for {col_name.replace('_', ' ')}."
    if 'hash' in col_name or 'digest' in col_name or 'signature' in col_name or 'fingerprint' in col_name:
        return f"Cryptographic signature or hash digest for {col_name.replace('_', ' ')}."
    if 'reason' in col_name or 'cause' in col_name:
        return f"Documented justification or reason for {col_name.replace('_', ' ')}."

    return f"Stores {col_name.replace('_', ' ')} value."

def build_markdown():
    tables, fks, uqs, _ = get_db_metadata()
    
    output = []
    output.append("## 3. Database Design\n")
    output.append("(*DB Diagrams Images*)\n")
    output.append("The database architecture for the Personalized Product Sales and Workshop Booking System is implemented on MySQL 8.0+ / 9.x utilizing the InnoDB storage engine with `utf8mb4` encoding for comprehensive Unicode support. Database schema versions and automated migrations are versioned via Flyway across migrations V4 through V88. The schema comprises 118 normalized tables structured across 11 functional business domains:\n")
    
    # Table of domains
    output.append("1. **Account & Identity Management** (5 tables): User authentication, RBAC authorization, location permissions, and provisioning.")
    output.append("2. **AI Chatbox & Custom Design Review** (8 tables): AI prompt tracing, design analysis, gemstone pricing models, and goldsmith review.")
    output.append("3. **Workshop Operations & Scheduling** (13 tables): Branches, time slots, tiered packages, scheduled sessions, instructor allocations, and attendee check-ins.")
    output.append("4. **Workshop Booking & Reservations** (16 tables): Customer bookings, seat commitments, quotes, invoices, payment attempts, and guest magic links.")
    output.append("5. **Workshop Cancellation & Settlement** (19 tables): Cancellation cases, consumption billing for add-ons, balance adjustments, and refunds.")
    output.append("6. **Loyalty Program & Promotions** (10 tables): Concurrency-controlled loyalty wallet, immutable reward point ledgers, and marketing campaign vouchers.")
    output.append("7. **Product Catalogue Management** (12 tables): Ring merchandise catalog, multi-attribute variants (size, metal), media assets, and price proposals.")
    output.append("8. **Ready-Ring Retail Sales & Cart** (12 tables): Member and guest shopping carts, inventory hold locks, branch checkout quotes, and pickup evidence.")
    output.append("9. **Payment Integration & Merchant Operations** (3 tables): Payment gateway merchant accounts, raw webhook ingress receipts, and VNPay audit operations.")
    output.append("10. **OTA Connectivity & Multi-channel Management** (18 tables): Klook/KKday OTA channel connections, listing mappings, syndicated pricing, and partner reconciliation.")
    output.append("11. **System Integration & Infrastructure** (2 tables): Spring Modulith persistent event outbox and API boundary idempotency ledgers.\n")
    
    table_index = 1
    
    for domain in DOMAINS:
        for tbl_name in domain['tables']:
            if tbl_name not in tables:
                print(f"Warning: Table {tbl_name} not found in DB!")
                continue
                
            cols = tables[tbl_name]
            desc = TABLE_DESCRIPTIONS.get(tbl_name, f"Manages {tbl_name.replace('_', ' ')} data.")
            
            output.append(f"### 3.{table_index} {tbl_name}\n")
            output.append(f"{desc}\n")
            output.append('::: {.docx-table width="100%" columns="20%,30%,15%,35%" repeat-header="false"}\n')
            output.append("|Column|Type|Constraints|Notes|")
            output.append("|---|---|---|---|")
            
            for col in cols:
                c_name = col['COLUMN_NAME']
                c_type = col['COLUMN_TYPE']
                c_constr = format_constraint(col, fks, uqs)
                c_note = generate_column_note(col, fks)
                
                # Escape pipes if any in enum types or notes
                c_type_esc = c_type.replace('|', '/')
                c_constr_esc = c_constr.replace('|', '/')
                c_note_esc = c_note.replace('|', '/')
                
                output.append(f"|{c_name}|{c_type_esc}|{c_constr_esc}|{c_note_esc}|")
                
            output.append("\n:::\n")
            table_index += 1

    return "\n".join(output)

if __name__ == '__main__':
    md = build_markdown()
    target_path = os.path.join(
        os.path.dirname(script_dir),
        'docs', 'report-4-software-design-specification', 'sections',
        '02-i-high-level-design', '03-database-design.md'
    )
    with open(target_path, 'w', encoding='utf-8') as f:
        f.write(md)
    print(f"Wrote complete database design documentation to {target_path}")
