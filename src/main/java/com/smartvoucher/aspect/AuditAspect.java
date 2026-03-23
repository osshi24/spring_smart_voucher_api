package com.smartvoucher.aspect;

import com.smartvoucher.annotation.Auditable;
import com.smartvoucher.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogService auditLogService;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint jp, Auditable auditable) throws Throwable {
        Object result = jp.proceed();

        try {
            Long entityId = resolveEntityId(jp, auditable, result);
            auditLogService.log(auditable.action(), auditable.entityType(), entityId, null, result);
        } catch (Exception e) {
            log.warn("AuditAspect failed to log audit entry: {}", e.getMessage());
        }

        return result;
    }

    private Long resolveEntityId(ProceedingJoinPoint jp, Auditable auditable, Object result) {
        String spel = auditable.entityIdSpel();
        if (spel == null || spel.isBlank()) {
            return null;
        }
        try {
            MethodSignature sig = (MethodSignature) jp.getSignature();
            String[] paramNames = sig.getParameterNames();
            Object[] args = jp.getArgs();

            StandardEvaluationContext ctx = new StandardEvaluationContext();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    ctx.setVariable(paramNames[i], args[i]);
                }
            }
            ctx.setVariable("result", result);

            Object val = parser.parseExpression(spel).getValue(ctx);
            if (val instanceof Number n) {
                return n.longValue();
            }
        } catch (Exception e) {
            log.warn("AuditAspect: failed to evaluate entityIdSpel '{}': {}", spel, e.getMessage());
        }
        return null;
    }
}
