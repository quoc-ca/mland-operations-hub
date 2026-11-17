package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.common.payment.PaymentGatewayCheckoutPort;
import edu.fpt.sep490_g22.ppswbs_backend.common.payment.VnpayMerchantOperationsPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketFolioContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCashDayCloseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFolioCollectionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.AbsorbedUnderchargeDtos.AbsorbedUnderchargeDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.AbsorbedUnderchargeDtos.AbsorbedUnderchargeListDto;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementCharge;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementFinalBill;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

/**
 * 003.3 T135 and T156 (FR-057, SC-014): the Manager's absorbed-undercharge records on the Flyway-built MySQL schema.
 * Documentation only: the folio, its balance and the cash close never move.
 */
@SpringBootTest
@ActiveProfiles({"test", "workshopoperations-mysql-test"})
@TestPropertySource(properties = {
        "vnpay.merchant-api-ip-address=127.0.0.1",
        "vnpay.merchant-api-created-by=it-merchant"})
class AbsorbedUnderchargeMysqlIT {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.now(VIETNAM);
    private static final CurrentAccountActor MANAGER =
            new CurrentAccountActor(300L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of());
    private static final CurrentAccountActor STAFF =
            new CurrentAccountActor(301L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of());

    @MockitoBean PaymentGatewayCheckoutPort checkout;
    @MockitoBean VnpayMerchantOperationsPort merchantOperations;
    @MockitoBean WorkshopOperationsFacade operationsFacade;
    @MockitoBean LoyaltyFacade loyalty;
    @MockitoBean WorkshopBookingFacade bookingFacade;
    @MockitoBean edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.notificationfacade.WorkshopBookingNotificationFacade bookingNotifications;

    @Autowired SettlementFinalBillRepository bills;
    @Autowired SettlementChargeRepository charges;
    @Autowired SettlementFolioCollectionRepository collections;
    @Autowired SettlementCashDayCloseRepository closes;
    @Autowired AbsorbedUnderchargeService undercharges;
    @Autowired FolioBalanceService balances;
    @Autowired JdbcTemplate jdbc;

    private static String unique(String prefix) {
        return prefix + SEQ.incrementAndGet();
    }

    private static long newBranch() {
        return 9_500_000L + SEQ.incrementAndGet();
    }

    private static String key() {
        return "it-idempotency-key-" + UUID.randomUUID();
    }

    private SettlementFinalBill folio(String ticket, long total, String status, long location) {
        doReturn(Optional.of(new TicketFolioContext(ticket, "G-" + ticket, "G-" + ticket + "-L1", "7", location,
                List.of(), null, null, null, 0L, "L1-" + ticket, 0L)))
                .when(bookingFacade).getTicketFolioContext(ticket);
        return bills.save(SettlementFinalBill.builder().billReference("BILL-" + ticket).bookingReference(ticket)
                .groupReference("G-" + ticket).postPromotionAmountVnd(total).acceptedDepositAmountVnd(total)
                .remainingAmountVnd(0L).currencyCode("VND").lifecycleStatus(status).build());
    }

    private AbsorbedUnderchargeDto record(String ticket, long amount) {
        return undercharges.record(ticket, key(), "CORR", amount, "Counter price was lower", MANAGER);
    }

    @Test
    @DisplayName("record, replay, conflict, void once, content immutable, folio untouched")
    void recordReplayVoidAndImmutability() {
        long branch = newBranch();
        String ticket = unique("T-UC-");
        SettlementFinalBill folio = folio(ticket, 500_000, "FINALIZED", branch);
        long foliosBefore = bills.count();
        long collectionsBefore = collections.count();
        long closesBefore = closes.count();
        long dueBefore = balances.balanceOf(folio).balanceDue();

        String key = key();
        AbsorbedUnderchargeDto first = undercharges.record(ticket, key, "CORR", 120_000, "Wrong price shown", MANAGER);
        AbsorbedUnderchargeDto replay = undercharges.record(ticket, key, "CORR", 120_000, "Wrong price shown", MANAGER);

        assertThat(first.status()).isEqualTo("ACTIVE");
        assertThat(first.amountVnd()).isEqualTo("120000");
        assertThat(first.locationId()).isEqualTo(branch);
        assertThat(first.groupCode()).isEqualTo("G-" + ticket);
        assertThat(first.recordedBy()).isEqualTo("300");
        assertThat(replay.underchargeReference()).isEqualTo(first.underchargeReference());
        assertThatThrownBy(() -> undercharges.record(ticket, key, "CORR", 130_000, "Wrong price shown", MANAGER))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("IDEMPOTENCY_CONFLICT");

        String voidKey = key();
        AbsorbedUnderchargeDto voided = undercharges.voidRecord(ticket, first.underchargeReference(), voidKey,
                "CORR", "Typed twice", MANAGER);
        AbsorbedUnderchargeDto voidReplay = undercharges.voidRecord(ticket, first.underchargeReference(), voidKey,
                "CORR", "Typed twice", MANAGER);
        assertThat(voided.status()).isEqualTo("VOIDED");
        assertThat(voided.amountVnd()).isEqualTo("120000");
        assertThat(voided.reason()).isEqualTo("Wrong price shown");
        assertThat(voided.voidReason()).isEqualTo("Typed twice");
        assertThat(voidReplay.status()).isEqualTo("VOIDED");
        assertThatThrownBy(() -> undercharges.voidRecord(ticket, first.underchargeReference(), key(), "CORR",
                "again", MANAGER)).isInstanceOf(WorkshopSettlementConflictException.class)
                .hasMessage("UNDERCHARGE_ALREADY_VOIDED");

        assertThat(bills.count()).isEqualTo(foliosBefore);
        assertThat(collections.count()).isEqualTo(collectionsBefore);
        assertThat(closes.count()).isEqualTo(closesBefore);
        SettlementFinalBill after = bills.findByBookingReference(ticket).orElseThrow();
        assertThat(after.getLifecycleStatus()).isEqualTo("FINALIZED");
        assertThat(after.getRemainingAmountVnd()).isEqualTo(folio.getRemainingAmountVnd());
        assertThat(balances.balanceOf(after).balanceDue()).isEqualTo(dueBefore);
    }

    @Test
    @DisplayName("refusals: Staff 403, OPEN folio 409 FOLIO_NOT_FINALIZED, unknown ticket and record not found")
    void refusals() {
        long branch = newBranch();
        String open = unique("T-UCO-");
        folio(open, 100_000, "OPEN", branch);
        String ticket = unique("T-UCR-");
        folio(ticket, 100_000, "FINALIZED", branch);

        assertThatThrownBy(() -> undercharges.record(ticket, key(), "C", 1_000, "r", STAFF))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        assertThatThrownBy(() -> undercharges.record(open, key(), "C", 1_000, "r", MANAGER))
                .isInstanceOf(WorkshopSettlementConflictException.class).hasMessage("FOLIO_NOT_FINALIZED");
        assertThatThrownBy(() -> undercharges.record(unique("T-NONE-"), key(), "C", 1_000, "r", MANAGER))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
        assertThatThrownBy(() -> undercharges.voidRecord(ticket, "UNDERCHARGE-NOPE", key(), "C", "r", MANAGER))
                .isInstanceOf(WorkshopSettlementNotFoundException.class);
        assertThatThrownBy(() -> undercharges.record(ticket, "short", "C", 1_000, "r", MANAGER))
                .isInstanceOf(WorkshopSettlementValidationException.class);
    }

    @Test
    @DisplayName("T156 cap under the folio lock: equal allowed, +1 refused 422, two records over the total, a void frees it")
    void capAtTheFolioTotal() {
        long branch = newBranch();
        String ticket = unique("T-UCC-");
        SettlementFinalBill folio = folio(ticket, 400_000, "FINALIZED", branch);
        // a confirmed variable charge raises the total, a voided one does not
        charges.save(SettlementCharge.builder().chargeReference("CH-" + ticket).billReference(folio.getBillReference())
                .sourceCategory("VARIABLE_CHARGE").description("Extra").amountVnd(100_000L).currencyCode("VND")
                .lifecycleStatus("CONFIRMED").reasonReference("r").consentReference("c-" + UUID.randomUUID()).consentRecordedBy(300L)
                .consentRecordedAt(Instant.now()).createdAt(Instant.now()).build());
        charges.save(SettlementCharge.builder().chargeReference("CHV-" + ticket).billReference(folio.getBillReference())
                .sourceCategory("VARIABLE_CHARGE").description("Voided").amountVnd(999_000L).currencyCode("VND")
                .lifecycleStatus("VOIDED").reasonReference("r").consentReference("c-" + UUID.randomUUID()).consentRecordedBy(300L)
                .consentRecordedAt(Instant.now()).createdAt(Instant.now()).build());
        long total = 500_000;

        assertThatThrownBy(() -> record(ticket, total + 1))
                .isInstanceOf(SettlementUnprocessableException.class).hasMessage("UNDERCHARGE_EXCEEDS_FOLIO_TOTAL");
        AbsorbedUnderchargeDto first = record(ticket, 300_000);
        assertThatThrownBy(() -> record(ticket, 200_001))
                .isInstanceOf(SettlementUnprocessableException.class).hasMessage("UNDERCHARGE_EXCEEDS_FOLIO_TOTAL");
        AbsorbedUnderchargeDto second = record(ticket, 200_000); // 300,000 + 200,000 = the total, allowed
        assertThat(second.amountVnd()).isEqualTo("200000");
        assertThatThrownBy(() -> record(ticket, 1)).isInstanceOf(SettlementUnprocessableException.class);

        undercharges.voidRecord(ticket, first.underchargeReference(), key(), "CORR", "Mistake", MANAGER);
        assertThat(record(ticket, 300_000).status()).isEqualTo("ACTIVE"); // the voided amount is free again
        assertThatThrownBy(() -> record(ticket, 1)).isInstanceOf(SettlementUnprocessableException.class);
    }

    @Test
    @DisplayName("branch and period list: ACTIVE and VOIDED within from..to, total of ACTIVE only, Manager only")
    void listByBranchAndPeriod() {
        long branch = newBranch();
        long otherBranch = newBranch();
        String ticket = unique("T-UCL-");
        folio(ticket, 900_000, "FINALIZED", branch);
        String other = unique("T-UCLO-");
        folio(other, 900_000, "FINALIZED", otherBranch);

        AbsorbedUnderchargeDto active = record(ticket, 100_000);
        AbsorbedUnderchargeDto toVoid = record(ticket, 50_000);
        undercharges.voidRecord(ticket, toVoid.underchargeReference(), key(), "CORR", "Mistake", MANAGER);
        AbsorbedUnderchargeDto yesterday = record(ticket, 30_000);
        jdbc.update("update settlement_absorbed_undercharges set recorded_at = ? where undercharge_reference = ?",
                java.sql.Timestamp.from(TODAY.minusDays(1).atTime(12, 0).atZone(VIETNAM).toInstant()),
                yesterday.underchargeReference());
        record(other, 70_000);

        AbsorbedUnderchargeListDto today = undercharges.list(branch, TODAY, TODAY, MANAGER);
        assertThat(today.records()).extracting(AbsorbedUnderchargeDto::underchargeReference)
                .containsExactlyInAnyOrder(active.underchargeReference(), toVoid.underchargeReference());
        assertThat(today.totalVnd()).isEqualTo("100000");
        assertThat(undercharges.list(branch, TODAY.minusDays(1), TODAY, MANAGER).totalVnd()).isEqualTo("130000");
        assertThat(undercharges.list(branch, TODAY.minusDays(1), TODAY.minusDays(1), MANAGER).records())
                .extracting(AbsorbedUnderchargeDto::underchargeReference).containsExactly(yesterday.underchargeReference());
        AbsorbedUnderchargeListDto none = undercharges.list(branch, TODAY.plusDays(1), TODAY.plusDays(2), MANAGER);
        assertThat(none.records()).isEmpty();
        assertThat(none.totalVnd()).isEqualTo("0");
        assertThat(undercharges.list(otherBranch, TODAY, TODAY, MANAGER).totalVnd()).isEqualTo("70000");
        assertThatThrownBy(() -> undercharges.list(branch, TODAY, TODAY, STAFF))
                .isInstanceOf(WorkshopSettlementAuthorizationException.class);
        assertThatThrownBy(() -> undercharges.list(branch, TODAY, TODAY.minusDays(1), MANAGER))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    }
}
