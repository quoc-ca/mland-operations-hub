package edu.fpt.sep490_g22.ppswbs_backend.common;

public interface AuditEventWriter {
    void writeEvent(AuditEvent event);
}
