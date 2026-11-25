## 10. Manager Dashboard (FE-10)

**Implementation status:** Read-only Manager dashboard slice exists; Admin does not receive executive revenue authority.

### 10.1 Class Diagram

{{r4-cd-fe10-manager-dashboard width=100% align=center}}

Dashboard queries use reporting projections/facades and do not mutate catalogue, order, payment or customer state.

### 10.2 Sequence Diagram — View Executive Revenue Dashboard

{{r4-sd-fe10-executive-dashboard width=100% align=center}}

### 10.3 Sequence Diagram — View Trends and Workshop Utilization

{{r4-sd-fe10-trends width=100% align=center}}

### 10.4 Sequence Diagram — View Best Sellers and Exceptions

{{r4-sd-fe10-best-sellers width=100% align=center}}
