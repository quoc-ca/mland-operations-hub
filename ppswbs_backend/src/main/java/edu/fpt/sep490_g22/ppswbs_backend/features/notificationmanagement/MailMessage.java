package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement;

/** One email ready to send. */
record MailMessage(String to, String subject, String text, String html) { }
