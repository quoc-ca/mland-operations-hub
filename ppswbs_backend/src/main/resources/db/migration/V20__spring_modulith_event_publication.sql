-- Spring Modulith JPA event publication store.
-- This table is required when Hibernate schema generation is disabled and
-- transactional event publication is persisted by the JPA event registry.
CREATE TABLE event_publication (
    id BINARY(16) NOT NULL,
    publication_date DATETIME(6) NOT NULL,
    completion_date DATETIME(6) NULL,
    event_type VARCHAR(255) NOT NULL,
    listener_id VARCHAR(255) NOT NULL,
    serialized_event LONGTEXT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_event_publication_incomplete (completion_date),
    KEY idx_event_publication_listener (listener_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
