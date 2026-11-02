package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.CheckInOpeningSource;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkshopOperationsDtoTests {

    private Validator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("T013-1: SlotRequest enforces Asia/Ho_Chi_Minh same-day timing and defaultCapacity >= 1")
    void slotRequestValidation() {
        assertThat(SlotRequest.WORKSHOP_ZONE).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));

        // Valid SlotRequest
        SlotRequest validSlot = SlotRequest.builder()
                .label("Morning Slot")
                .startsAtLocal(LocalTime.of(9, 0))
                .endsAtLocal(LocalTime.of(12, 0))
                .defaultCapacity(30)
                .active(true)
                .build();
        Set<ConstraintViolation<SlotRequest>> violations = validator.validate(validSlot);
        assertThat(violations).isEmpty();
        assertThat(validSlot.isSameDayTimingValid()).isTrue();

        // Invalid: defaultCapacity < 1
        SlotRequest zeroCapacity = SlotRequest.builder()
                .label("Zero Capacity Slot")
                .startsAtLocal(LocalTime.of(9, 0))
                .endsAtLocal(LocalTime.of(12, 0))
                .defaultCapacity(0)
                .active(true)
                .build();
        Set<ConstraintViolation<SlotRequest>> zeroViolations = validator.validate(zeroCapacity);
        assertThat(zeroViolations).hasSize(1);
        assertThat(zeroViolations.iterator().next().getMessage()).contains("at least 1");

        // Invalid: startsAtLocal is after endsAtLocal (spans midnight or backwards)
        SlotRequest overnightSlot = SlotRequest.builder()
                .label("Overnight Slot")
                .startsAtLocal(LocalTime.of(22, 0))
                .endsAtLocal(LocalTime.of(2, 0))
                .defaultCapacity(20)
                .active(true)
                .build();
        Set<ConstraintViolation<SlotRequest>> overnightViolations = validator.validate(overnightSlot);
        assertThat(overnightViolations).isNotEmpty();
        assertThat(overnightSlot.isSameDayTimingValid()).isFalse();

        // Invalid: startsAtLocal equals endsAtLocal
        SlotRequest zeroDurationSlot = SlotRequest.builder()
                .label("Zero Duration Slot")
                .startsAtLocal(LocalTime.of(14, 0))
                .endsAtLocal(LocalTime.of(14, 0))
                .defaultCapacity(20)
                .active(true)
                .build();
        Set<ConstraintViolation<SlotRequest>> zeroDurationViolations = validator.validate(zeroDurationSlot);
        assertThat(zeroDurationViolations).isNotEmpty();
        assertThat(zeroDurationSlot.isSameDayTimingValid()).isFalse();
    }

    @Test
    @DisplayName("T013-2: SessionRequest enforces capacity >= 1 and has NO free-form session start/end fields")
    void sessionRequestValidationAndFieldIntegrity() {
        // Valid SessionRequest
        SessionRequest validSession = SessionRequest.builder()
                .locationId(10L)
                .slotId(UUID.randomUUID().toString())
                .workshopDate(LocalDate.now().plusDays(10))
                .capacity(25)
                .build();
        Set<ConstraintViolation<SessionRequest>> violations = validator.validate(validSession);
        assertThat(violations).isEmpty();

        // Invalid: capacity < 1
        SessionRequest zeroCapacity = SessionRequest.builder()
                .locationId(10L)
                .slotId(UUID.randomUUID().toString())
                .workshopDate(LocalDate.now().plusDays(10))
                .capacity(0)
                .build();
        Set<ConstraintViolation<SessionRequest>> zeroViolations = validator.validate(zeroCapacity);
        assertThat(zeroViolations).hasSize(1);
        assertThat(zeroViolations.iterator().next().getMessage()).contains("at least 1");

        // Negative capacity
        SessionRequest negativeCapacity = SessionRequest.builder()
                .locationId(10L)
                .slotId(UUID.randomUUID().toString())
                .workshopDate(LocalDate.now().plusDays(10))
                .capacity(-5)
                .build();
        assertThat(validator.validate(negativeCapacity)).isNotEmpty();

        // Missing required fields
        SessionRequest missingFields = SessionRequest.builder().build();
        assertThat(validator.validate(missingFields)).hasSize(4); // locationId, slotId, workshopDate, capacity

        // Prove via reflection that SessionRequest has NO free-form session start/end fields
        List<String> fieldNames = Arrays.stream(SessionRequest.class.getDeclaredFields())
                .map(Field::getName)
                .toList();
        assertThat(fieldNames)
                .as("SessionRequest must NOT have free-form session start or end fields")
                .doesNotContain("scheduledStartAt", "scheduledEndAt", "startTime", "endTime");
    }

    @Test
    @DisplayName("T013-3: BulkSessionRequest validates date range and initialState constraints")
    void bulkSessionRequestValidation() {
        Long locationId = 10L;
        List<String> slotIds = List.of(UUID.randomUUID().toString());
        LocalDate today = LocalDate.now();

        // Valid BulkSessionRequest
        BulkSessionRequest validBulk = BulkSessionRequest.builder()
                .locationId(locationId)
                .slotIds(slotIds)
                .from(today.plusDays(1))
                .to(today.plusDays(30))
                .weekdays(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
                .initialState(SessionState.DRAFT)
                .build();
        assertThat(validator.validate(validBulk)).isEmpty();
        assertThat(validBulk.isDateRangeValid()).isTrue();
        assertThat(validBulk.isInitialStateValid()).isTrue();

        // Invalid: from > to
        BulkSessionRequest invertedDates = BulkSessionRequest.builder()
                .locationId(locationId)
                .slotIds(slotIds)
                .from(today.plusDays(30))
                .to(today.plusDays(1))
                .weekdays(List.of(DayOfWeek.SATURDAY))
                .build();
        assertThat(validator.validate(invertedDates)).isNotEmpty();
        assertThat(invertedDates.isDateRangeValid()).isFalse();

        // Invalid initialState: CANCELLED is not allowed as initial state
        BulkSessionRequest invalidState = BulkSessionRequest.builder()
                .locationId(locationId)
                .slotIds(slotIds)
                .from(today.plusDays(1))
                .to(today.plusDays(5))
                .weekdays(List.of(DayOfWeek.SUNDAY))
                .initialState(SessionState.CANCELLED)
                .build();
        assertThat(validator.validate(invalidState)).isNotEmpty();
        assertThat(invalidState.isInitialStateValid()).isFalse();
    }

    @Test
    @DisplayName("T013-4: LocationRequest validates required code and name")
    void locationRequestValidation() {
        LocationRequest valid = LocationRequest.builder()
                .code("LOC-HCM-01")
                .name("Nguyen Hue Location")
                .address("123 Nguyen Hue")
                .active(true)
                .build();
        assertThat(validator.validate(valid)).isEmpty();

        LocationRequest missingCode = LocationRequest.builder()
                .name("No Code Location")
                .build();
        assertThat(validator.validate(missingCode)).isNotEmpty();

        LocationRequest missingName = LocationRequest.builder()
                .code("LOC-02")
                .build();
        assertThat(validator.validate(missingName)).isNotEmpty();
    }

    @Test
    @DisplayName("T013-5: DTOs do not expose JPA entities, tokens, PII, or audit internals")
    void nonDisclosureAndModularityIntegrity() {
        Class<?>[] dtoClasses = {
                LocationRequest.class,
                LocationResponse.class,
                SlotRequest.class,
                SlotResponse.class,
                SessionRequest.class,
                SessionResponse.class,
                BulkSessionRequest.class,
                BulkGenerationResponse.class,
                BulkGenerationResult.class
        };

        for (Class<?> clazz : dtoClasses) {
            // Must not be a JPA Entity
            assertThat(clazz.isAnnotationPresent(jakarta.persistence.Entity.class))
                    .as("DTO %s must not be a JPA entity", clazz.getSimpleName())
                    .isFalse();

            // Field checks: no token, raw secret, QR payload, or audit internals
            for (Field field : clazz.getDeclaredFields()) {
                String name = field.getName().toLowerCase();
                assertThat(name)
                        .as("Field %s in %s must not expose sensitive tokens or audit internals", name, clazz.getSimpleName())
                        .doesNotContain("token", "secret", "qrtoken", "auditevent", "claimversion");
            }
        }
    }

    @Test
    @DisplayName("T013-6: JSON serialization and deserialization roundtrip for DTOs")
    void jsonSerializationRoundtrip() throws Exception {
        // SlotRequest roundtrip
        SlotRequest slotReq = SlotRequest.builder()
                .label("Afternoon Slot")
                .startsAtLocal(LocalTime.of(13, 0))
                .endsAtLocal(LocalTime.of(16, 0))
                .defaultCapacity(25)
                .active(true)
                .build();
        String slotJson = objectMapper.writeValueAsString(slotReq);
        SlotRequest deserializedSlot = objectMapper.readValue(slotJson, SlotRequest.class);
        assertThat(deserializedSlot.getStartsAtLocal()).isEqualTo(LocalTime.of(13, 0));
        assertThat(deserializedSlot.getEndsAtLocal()).isEqualTo(LocalTime.of(16, 0));
        assertThat(deserializedSlot.getDefaultCapacity()).isEqualTo(25);

        // SessionResponse roundtrip
        SessionResponse sessionResp = SessionResponse.builder()
                .id(UUID.randomUUID().toString())
                .locationId(10L)
                .slotId(UUID.randomUUID().toString())
                .workshopDate(LocalDate.of(2026, 11, 15))
                .capacity(30)
                .state(SessionState.PUBLISHED)
                .scheduledStartAt(Instant.parse("2026-11-15T02:00:00Z"))
                .scheduledEndAt(Instant.parse("2026-11-15T05:00:00Z"))
                .checkInOpeningSource(CheckInOpeningSource.DEFAULT)
                .checkInOpensAt(Instant.parse("2026-11-15T01:00:00Z"))
                .build();
        String sessionJson = objectMapper.writeValueAsString(sessionResp);
        SessionResponse deserializedSession = objectMapper.readValue(sessionJson, SessionResponse.class);
        assertThat(deserializedSession.getState()).isEqualTo(SessionState.PUBLISHED);
        assertThat(deserializedSession.getCheckInOpeningSource()).isEqualTo(CheckInOpeningSource.DEFAULT);
        assertThat(deserializedSession.getWorkshopDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(deserializedSession.getLocationId()).isEqualTo(10L);

        // BulkGenerationResponse roundtrip
        BulkGenerationResponse bulkResp = BulkGenerationResponse.builder()
                .createdSessionIds(List.of("id-1", "id-2"))
                .skippedDuplicates(List.of("dup-1"))
                .build();
        String bulkJson = objectMapper.writeValueAsString(bulkResp);
        BulkGenerationResponse deserializedBulk = objectMapper.readValue(bulkJson, BulkGenerationResponse.class);
        assertThat(deserializedBulk.getCreatedSessionIds()).containsExactly("id-1", "id-2");
        assertThat(deserializedBulk.getSkippedDuplicates()).containsExactly("dup-1");

        // BulkSessionRequest roundtrip
        BulkSessionRequest bulkReq = BulkSessionRequest.builder()
                .locationId(10L)
                .slotIds(List.of("slot-1", "slot-2"))
                .from(LocalDate.of(2026, 11, 1))
                .to(LocalDate.of(2026, 11, 30))
                .weekdays(List.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
                .initialState(SessionState.PUBLISHED)
                .build();
        String bulkReqJson = objectMapper.writeValueAsString(bulkReq);
        BulkSessionRequest deserializedBulkReq = objectMapper.readValue(bulkReqJson, BulkSessionRequest.class);
        assertThat(deserializedBulkReq.getLocationId()).isEqualTo(10L);
        assertThat(deserializedBulkReq.getWeekdays()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThat(deserializedBulkReq.getInitialState()).isEqualTo(SessionState.PUBLISHED);

        // StateTransitionRequest roundtrip
        StateTransitionRequest stateReq = StateTransitionRequest.builder()
                .targetState(SessionState.IN_PROGRESS)
                .build();
        String stateJson = objectMapper.writeValueAsString(stateReq);
        StateTransitionRequest deserializedState = objectMapper.readValue(stateJson, StateTransitionRequest.class);
        assertThat(deserializedState.getTargetState()).isEqualTo(SessionState.IN_PROGRESS);

        // StaffAssignmentRequest roundtrip
        StaffAssignmentRequest staffReq = StaffAssignmentRequest.builder()
                .accountId(42L)
                .build();
        String staffJson = objectMapper.writeValueAsString(staffReq);
        StaffAssignmentRequest deserializedStaff = objectMapper.readValue(staffJson, StaffAssignmentRequest.class);
        assertThat(deserializedStaff.getAccountId()).isEqualTo(42L);

        // OperationalLeadRequest roundtrip
        OperationalLeadRequest opLeadReq = OperationalLeadRequest.builder()
                .managerAccountId(10L)
                .reasonCode("LEAD_TAKEOVER")
                .reasonNote("Manager taking over onsite operations")
                .evidenceReference("EV-2026-001")
                .build();
        String opLeadJson = objectMapper.writeValueAsString(opLeadReq);
        OperationalLeadRequest deserializedOpLead = objectMapper.readValue(opLeadJson, OperationalLeadRequest.class);
        assertThat(deserializedOpLead.getManagerAccountId()).isEqualTo(10L);
        assertThat(deserializedOpLead.getReasonCode()).isEqualTo("LEAD_TAKEOVER");
        assertThat(deserializedOpLead.getReasonNote()).isEqualTo("Manager taking over onsite operations");
        assertThat(deserializedOpLead.getEvidenceReference()).isEqualTo("EV-2026-001");
    }

    @Test
    @DisplayName("T020-1: StateTransitionRequest, StaffAssignmentRequest, and OperationalLeadRequest enforce canonical types and reject legacy fields")
    void us2DtoValidationAndAbsenceOfLegacyFields() {
        // StateTransitionRequest
        StateTransitionRequest validState = StateTransitionRequest.builder()
                .targetState(SessionState.PUBLISHED)
                .build();
        assertThat(validator.validate(validState)).isEmpty();

        StateTransitionRequest invalidState = StateTransitionRequest.builder()
                .targetState(SessionState.DRAFT)
                .build();
        assertThat(validator.validate(invalidState)).isNotEmpty();

        // StaffAssignmentRequest: requires Long accountId
        StaffAssignmentRequest validStaff = StaffAssignmentRequest.builder().accountId(100L).build();
        assertThat(validator.validate(validStaff)).isEmpty();

        StaffAssignmentRequest nullStaff = StaffAssignmentRequest.builder().build();
        assertThat(validator.validate(nullStaff)).hasSize(1);

        List<String> staffFields = Arrays.stream(StaffAssignmentRequest.class.getDeclaredFields())
                .map(Field::getName)
                .toList();
        assertThat(staffFields).contains("accountId");
        assertThat(staffFields).doesNotContain("personnelId", "branchId");

        // OperationalLeadRequest: requires Long managerAccountId and String reasonCode
        OperationalLeadRequest validOpLead = OperationalLeadRequest.builder()
                .managerAccountId(10L)
                .reasonCode("ONSITE_COVERAGE")
                .build();
        assertThat(validator.validate(validOpLead)).isEmpty();

        OperationalLeadRequest missingMgr = OperationalLeadRequest.builder()
                .reasonCode("ONSITE_COVERAGE")
                .build();
        assertThat(validator.validate(missingMgr)).hasSize(1);

        OperationalLeadRequest missingReason = OperationalLeadRequest.builder()
                .managerAccountId(10L)
                .build();
        assertThat(validator.validate(missingReason)).hasSize(1);

        List<String> opLeadFields = Arrays.stream(OperationalLeadRequest.class.getDeclaredFields())
                .map(Field::getName)
                .toList();
        assertThat(opLeadFields).contains("managerAccountId", "reasonCode", "reasonNote", "evidenceReference");
        assertThat(opLeadFields).doesNotContain("managerPersonnelId", "branchId");
    }
}
