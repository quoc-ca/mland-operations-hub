## 1. Context Diagram

The Personalized Product Sales and Workshop Booking System is an operations platform for a ring atelier. It covers public discovery, workshop booking and settlement, ring design, custom design requests, retail sales with store pickup or GHTK delivery, and shop-side fulfilment. It is not a warehouse-management, ERP or accounting system.

The platform interacts with two groups of roles. The primary roles use the platform directly: Guest, Member, Staff, Manager, Admin and the Carrier (GHTK Partner). The secondary (supporting) roles are external services the platform calls: Mail Gateway (SMTP), SSO Provider (Firebase Authentication), Cloud Storage Service (Cloudinary), Payment Gateway (VNPay), Delivery Provider (GHTK) and AI API (Gemini). These services support the platform but never decide a business outcome.

{{r3-context-diagram width=100% align=center}}
