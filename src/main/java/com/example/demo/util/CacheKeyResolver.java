package com.example.demo.util;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * Utility class for resolving cache keys from SpEL expressions.
 * 
 * Evaluates SpEL expressions like "#isbn", "#user.id", etc. using method
 * parameters to generate cache keys.
 */
@Component
public class CacheKeyResolver {
    
    private final ExpressionParser parser = new SpelExpressionParser();
    
    /**
     * Resolves a SpEL expression to a cache key string using the method's parameters.
     * 
     * @param joinPoint the intercepted method call
     * @param keyExpression the SpEL expression (e.g., "#isbn")
     * @return the resolved cache key as a string
     */
    public String resolve(JoinPoint joinPoint, String keyExpression) {
        // Get method signature and parameters
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        
        // Create evaluation context and populate with method parameters
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }
        
        // Parse and evaluate the SpEL expression
        Object value = parser.parseExpression(keyExpression).getValue(context);
        
        return value != null ? value.toString() : "null";
    }
}

