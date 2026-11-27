-- 003.2 FR-073: optional cover image of a Workshop Package. The media object is owned by productmanagement,
-- so there is no cross-module foreign key; the active reference lives in catalogue_media_references.
ALTER TABLE workshop_packages
    ADD COLUMN image_media_id CHAR(36) NULL DEFAULT NULL;
