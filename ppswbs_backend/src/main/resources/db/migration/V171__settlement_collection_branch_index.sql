-- 003.3 T133 (FR-056): every folio collection carries its branch (canonical workshop_location.id), not only cash.
-- New collections are stamped at creation by FolioCollectionService; existing transfer/VNPAY rows are back-filled by
-- the one-off Java job CollectionBranchBackfillService through the 003.1 facade (no SQL across modules; rows it
-- cannot resolve stay NULL and are listed in the quickstart evidence). This migration only adds the read index.
CREATE INDEX ix_sfc_branch_method_status_confirmed
    ON settlement_folio_collections (branch_id, method, status, confirmed_at);
