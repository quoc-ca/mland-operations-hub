package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos.ActorRef;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * FR-060 (Session 2026-11-16, Q-S1): display labels of the actors recorded on proposals and reviews.
 *
 * <ul>
 *   <li>An account id: the full name on the account profile, read through the 001 {@link AccountFacade} at read
 *       time (once per distinct id); no full name, an unknown account or a facade failure gives
 *       {@code "Tài khoản #<id>"}. Email, Firebase UID and the raw id field are never exposed.</li>
 *   <li>A system actor ({@code system:…}): {@code "Hệ thống"}.</li>
 * </ul>
 * Shown to internal Staff/Manager readers only (management reads); never part of public reads.
 */
@Component
public class CatalogueActorNames {

    public static final String SYSTEM_LABEL = "Hệ thống";
    private static final Pattern ACCOUNT_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final int MAX_LABEL = 200;

    private final ObjectProvider<AccountFacade> accounts;

    public CatalogueActorNames(ObjectProvider<AccountFacade> accounts) {
        this.accounts = accounts;
    }

    /** Labels of the given actor ids (nulls ignored). */
    public Map<String, ActorRef> resolve(Collection<String> actorIds) {
        Map<String, ActorRef> labels = new HashMap<>();
        AccountFacade facade = accounts == null ? null : accounts.getIfAvailable();
        actorIds.stream().filter(Objects::nonNull).distinct()
                .forEach(actorId -> labels.put(actorId, label(facade, actorId)));
        return labels;
    }

    /** Label without the identity facade (no name known). */
    public static ActorRef fallback(String actorId) {
        if (SubmitterBranchSnapshot.isSystemActor(actorId)) {
            return new ActorRef("SYSTEM", SYSTEM_LABEL);
        }
        return new ActorRef("ACCOUNT", truncate("Tài khoản #" + actorId));
    }

    private static ActorRef label(AccountFacade facade, String actorId) {
        if (facade == null || SubmitterBranchSnapshot.isSystemActor(actorId) || !ACCOUNT_ID.matcher(actorId).matches()) {
            return fallback(actorId);
        }
        Optional<String> name;
        try {
            name = facade.findAccountFullName(Long.parseLong(actorId));
        } catch (RuntimeException e) {
            return fallback(actorId);
        }
        return name.map(String::strip).filter(value -> !value.isEmpty())
                .map(value -> new ActorRef("ACCOUNT", truncate(value)))
                .orElseGet(() -> fallback(actorId));
    }

    private static String truncate(String value) {
        if (value.codePointCount(0, value.length()) <= MAX_LABEL) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, MAX_LABEL));
    }
}
