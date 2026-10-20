package com.akamai.miniwsa.model;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/** The value must be a literal IPv4 or IPv6 address. Null is valid; combine with @NotBlank to require it. */
@Documented
@Constraint(validatedBy = ValidIp.Validator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIp {

    String message() default "must be a valid IPv4 or IPv6 address";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidIp, String> {

        private static final String OCTET = "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
        private static final Pattern IPV4 = Pattern.compile("^" + OCTET + "(\\." + OCTET + "){3}$");
        private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (IPV4.matcher(value).matches()) {
                return true;
            }
            // Only hex digits, colons and dots reach getByName, so it parses a literal and never does a DNS lookup.
            if (value.contains(":") && IPV6_CHARS.matcher(value).matches()) {
                try {
                    return InetAddress.getByName(value) != null;
                } catch (UnknownHostException e) {
                    return false;
                }
            }
            return false;
        }
    }
}
