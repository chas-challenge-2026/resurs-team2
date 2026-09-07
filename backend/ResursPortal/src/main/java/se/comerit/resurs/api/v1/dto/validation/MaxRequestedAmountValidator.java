package se.comerit.resurs.api.v1.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;

public class MaxRequestedAmountValidator implements ConstraintValidator<MaxRequestedAmount, BigDecimal> {

    @Value("${resurs.application.requested-amount.max:10000000}")
    private BigDecimal max;

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.compareTo(max) <= 0;
    }
}
