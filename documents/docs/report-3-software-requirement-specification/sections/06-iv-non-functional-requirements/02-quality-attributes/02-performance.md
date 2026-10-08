### 2.2 Performance

The baseline defines business deadlines, recovery checks and bounded workloads. It does not supply page-load, API-latency, throughput or concurrency targets. These timings are Mland rules rather than vendor-mandated durations or uptime guarantees.

| ID | Requirement / target | Conditions and verification |
| --- | --- | --- |
| NFR-PER-01 | VNPay links shall expire 10 minutes after applicable initiation. | Retail `t0` is accepted checkout, creating the first attempt/link together; reopening/identical retry preserves it. Other links use recorded initiation. Test before/at/after expiry. See Report 2 C-02 and UC45/UC46. |
| NFR-PER-02 | Unpaid workshop, whole-retail-cart and custom-deposit queue holds shall last at most 15 minutes from recorded start. | Retail starts at checkout acceptance. At expiry, holds cease contributing to logical held availability even if expiry processing runs later. Retry/edit/browser actions grant no extension. A custom slot committed by deposit remains reserved until pickup/handoff; final-balance payment does not impose a new 15-minute manufacturing lifetime. See Report 2 Scope, GBR-04 and UC46. |
| NFR-PER-03 | Eligible retail QueryDR shall run at minutes 10, 12 and 14 after checkout acceptance if no valid IPN records a final outcome. | Original attempt/hold must remain eligible. Verify signature/reference/amount/status and finish acceptance strictly before minute 15. Stop at final outcome/expiry; delays grant no extension. This UC46 schedule is not assumed for all payment types. |
| NFR-PER-04 | Retail expiry-state processing shall complete within 60 seconds of original expiry under normal operating conditions. | Compare expiry/processing timestamps with application, database and worker available. Logical availability/payment eligibility end at minute 15; 60 seconds is not extra payment time. Race checks follow NFR-REL-03. See UC46. |
| NFR-PER-05 | Carts shall have at most 50 lines and integer quantity 1–99 per variant across all lines, subject to availability. | Test maximum-valid/over-limit input without orders/holds from invalid carts. UC43/UC44 bounds do not establish peak-user capacity. |
| NFR-PER-06 | History shall use 20 orders/page by default, at most 50, newest creation time first with stable identity tie-breaker. | Exercise paging and creation-date/payment/fulfilment filters using own-order history. No cross-Member disclosure or unbounded history. See UC47. |

Enforce deadlines using recorded application timestamps, not device clocks/countdowns. This is a derived consistency criterion. Business dates/session times use configured branch/business timezone; provider formatting cannot override acceptance boundaries.

Retention deadlines are NFR-DAT-01–07. Daily jobs do not authorize overdue disclosure. Provider/notification retries preserve committed results under NFR-REL-05 and approved settings under NFR-OPEN-04.

For latency/load acceptance, NFR-OPEN-01 must define workflows (catalogue, booking, maximum-valid-cart checkout, history, dashboard and concurrent payment/expiry), dataset, workload mix, environment, network, percentile and error threshold. Measure application processing separately from customer interaction/provider latency. No numeric latency/capacity claim is made before that baseline exists.
