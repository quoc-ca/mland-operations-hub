package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.ContactPhones;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** Booking contact phone in international form {@code +<supported country code><number>} (003.1 FR-030). */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = InternationalPhone.Validator.class)
@Documented
public @interface InternationalPhone {
    String message() default "contactPhone must be an international number such as +84338905173";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Blank values are left to {@code @NotBlank}. */
    class Validator implements ConstraintValidator<InternationalPhone, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || ContactPhones.isValidInternational(value);
        }
    }
}
