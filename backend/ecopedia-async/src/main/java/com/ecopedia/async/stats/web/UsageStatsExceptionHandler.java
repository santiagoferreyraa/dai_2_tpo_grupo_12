package com.ecopedia.async.stats.web;

import com.ecopedia.async.stats.domain.InvalidDateRangeException;
import com.ecopedia.async.web.ApiProblems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Traduce los errores del componente Estadísticas a Problem Details RFC 9457. */
@RestControllerAdvice(basePackageClasses = UsageStatsExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UsageStatsExceptionHandler {

    @ExceptionHandler(InvalidDateRangeException.class)
    public ProblemDetail handleInvalidDateRange(InvalidDateRangeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setProperty("code", InvalidDateRangeException.CODE);
        return detail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleInvalidParameter(MethodArgumentTypeMismatchException exception) {
        ProblemDetail detail = ApiProblems.invalidRequest();
        detail.setProperty("code", "INVALID_PARAMETER");
        return detail;
    }
}
