package se.comerit.resurs.api.v1.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;

public class MinRequestedAmountValidator implements ConstraintValidator<MinRequestedAmount, BigDecimal> {

    @Value("${resurs.application.requested-amount.min:50000}")
    private BigDecimal min;

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.compareTo(min) >= 0;
    }
}
