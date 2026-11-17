package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.CreateBookingGroupRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.ContactPhones;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 003.1 FR-030 / FR-017a (T112): international contact phone validation and one canonical form for matching. */
class ContactPhonesTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static boolean phoneRejected(String phone) {
        return VALIDATOR.validate(new CreateBookingGroupRequest("quote-1", "a@b.test", phone)).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("contactPhone"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"+84338905173", "+14155552671", "+819012345678", "+821012345678", "+8613812345678",
            "+6591234567", "+66812345678", "+61412345678", "+447911123456", "+33612345678", "+4915112345678"})
    void aSupportedCountryNumberIsAccepted(String phone) {
        assertThat(ContactPhones.isValidInternational(phone)).isTrue();
        assertThat(phoneRejected(phone)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0338905173", "84338905173", "+99338905173", "+0338905173", "+", "abc", "+84 338905173",
            "+84138905173", "+8433890517", "+843389051731", "+11155552671", "+1415555267", "+8612345678901",
            "+6511234567", "+84338905173" + "0000000000000000000000"})
    void aWrongCodeShapeOrLengthIsRejected(String phone) {
        assertThat(ContactPhones.isValidInternational(phone)).isFalse();
        assertThat(phoneRejected(phone)).isTrue();
    }

    @Test
    void aBlankPhoneIsRejectedByNotBlank() {
        assertThat(phoneRejected("")).isTrue();
        assertThat(phoneRejected(null)).isTrue();
    }

    @Test
    void everyWritingOfTheSameVietnameseNumberMatchesTheStoredForm() {
        String[] writings = {"0338905173", "+84338905173", "+84 338 905 173", "84338905173", "+84 0338905173",
                "0338-905-173", "(033) 890.5173"};
        for (String stored : new String[] {"0338905173", "+84338905173", "84338905173"}) { // old and new stored forms
            for (String typed : writings) {
                assertThat(ContactPhones.sameContact(typed, stored)).as(typed + " vs " + stored).isTrue();
            }
        }
        assertThat(ContactPhones.canonical("+84338905173")).isEqualTo("0338905173");
    }

    @Test
    void aDifferentNumberOrCountryDoesNotMatch() {
        assertThat(ContactPhones.sameContact("0338905174", "+84338905173")).isFalse();
        assertThat(ContactPhones.sameContact("+14155552671", "+84338905173")).isFalse();
        assertThat(ContactPhones.sameContact("+1 415 555 2671", "+14155552671")).isTrue();
        assertThat(ContactPhones.sameContact(null, "+84338905173")).isFalse();
    }
}
