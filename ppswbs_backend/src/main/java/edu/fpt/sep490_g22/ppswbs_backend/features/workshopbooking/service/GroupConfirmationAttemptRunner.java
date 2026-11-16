package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One confirmation attempt of a group with verified payment evidence, in its own transaction (003.1 T145). A
 * dependency failure (003.2 hold commit, 006 benefit settlement) rolls back only this attempt, so the caller can still
 * commit the ingress receipt, the attempt {@code SUCCESS} and the durable retry record instead of losing the money.
 * A rollback of an inner {@code @Transactional} call would otherwise mark the caller's transaction rollback-only.
 */
@Service
@Slf4j
public class GroupConfirmationAttemptRunner {

    public enum Kind { CONFIRMED, HOLD_EXPIRED, DEPENDENCY_UNAVAILABLE }

    /** {@code failedDependency} is OPERATIONS or LOYALTY for {@code DEPENDENCY_UNAVAILABLE}, otherwise null. */
    public record Result(Kind kind, String failedDependency) {
        public String failureCode() {
            return failedDependency == null ? null : "DEPENDENCY_UNAVAILABLE:" + failedDependency;
        }
    }

    private final GroupConfirmationService confirmation;
    private final TransactionTemplate isolated;

    public GroupConfirmationAttemptRunner(GroupConfirmationService confirmation, PlatformTransactionManager manager) {
        this.confirmation = confirmation;
        this.isolated = new TransactionTemplate(manager);
        this.isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Never throws for a dependency failure or an expired hold; any other failure propagates unchanged. */
    public Result tryConfirm(String groupCode, long amountVnd, String evidenceReference, String operationReference,
                             String correlationReference) {
        try {
            isolated.executeWithoutResult(status -> confirmation.confirmPaidGroup(groupCode, amountVnd,
                    evidenceReference, operationReference, correlationReference));
            return new Result(Kind.CONFIRMED, null);
        } catch (BookingCreationRejectedException ex) {
            if ("GROUP_HOLD_EXPIRED".equals(ex.getCode())) {
                return new Result(Kind.HOLD_EXPIRED, null);
            }
            throw ex;
        } catch (LoyaltyException ex) {
            if (ex.code() == LoyaltyErrorCode.DEPENDENCY_UNAVAILABLE) {
                log.warn("Confirmation of group {} waits: Loyalty is unavailable", groupCode);
                return new Result(Kind.DEPENDENCY_UNAVAILABLE, "LOYALTY");
            }
            throw ex;
        } catch (BookingConflictException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Workshop Operations")) {
                log.warn("Confirmation of group {} waits: Workshop Operations is unavailable", groupCode);
                return new Result(Kind.DEPENDENCY_UNAVAILABLE, "OPERATIONS");
            }
            throw ex;
        }
    }
}
