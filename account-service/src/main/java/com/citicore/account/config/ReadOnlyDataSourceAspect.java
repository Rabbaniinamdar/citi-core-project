package com.citicore.account.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1)
public class ReadOnlyDataSourceAspect {

    @Around("@annotation(com.citicore.account.config.ReadOnly)")
    public Object routeToReplica(
            ProceedingJoinPoint joinPoint) throws Throwable {

        DataSourceType previous =
                DataSourceContextHolder.getCurrentDataSourceType();

        try {
            DataSourceContextHolder.setReplica();

            System.out.println(
                    "📖 [READ REPLICA] method="
                            + joinPoint.getSignature().getName()
            );

            return joinPoint.proceed();

        } finally {
            restore(previous);
        }
    }

    @Around("@annotation(com.citicore.account.config.PrimaryRead)")
    public Object routeToPrimary(
            ProceedingJoinPoint joinPoint) throws Throwable {

        DataSourceType previous =
                DataSourceContextHolder.getCurrentDataSourceType();

        try {
            DataSourceContextHolder.setPrimary();

            System.out.println(
                    "📖 [STRONG READ] method="
                            + joinPoint.getSignature().getName()
            );

            return joinPoint.proceed();

        } finally {
            restore(previous);
        }
    }

    private void restore(DataSourceType previous) {

        if (previous == null) {
            DataSourceContextHolder.clear();
        } else {
            DataSourceContextHolder.setDataSourceType(previous);
        }
    }
}