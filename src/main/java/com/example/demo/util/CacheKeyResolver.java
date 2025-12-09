package com.example.demo.util;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

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
    
    /**
     * Evaluates a condition SpEL expression BEFORE method execution.
     * Returns true if caching should proceed, false if caching should be skipped.
     * 
     * @param conditionExpression the SpEL expression (e.g., "#isbn != null")
     * @param method the method being invoked
     * @param args the method arguments
     * @return true if condition is met (or empty), false otherwise
     */
    public boolean evaluateCondition(String conditionExpression, Method method, Object[] args) {
        // Empty condition means "always cache"
        if (conditionExpression == null || conditionExpression.trim().isEmpty()) {
            return true;
        }
        
        // Create evaluation context with method parameters
        StandardEvaluationContext context = createEvaluationContext(method, args);
        
        // Evaluate the condition expression
        try {
            Boolean result = parser.parseExpression(conditionExpression).getValue(context, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Failed to evaluate condition expression: " + conditionExpression, e
            );
        }
    }
    
    /**
     * Evaluates an unless SpEL expression AFTER method execution.
     * Returns true if the result should NOT be cached, false if it should be cached.
     * 
     * @param unlessExpression the SpEL expression (e.g., "#result == null")
     * @param method the method being invoked
     * @param args the method arguments
     * @param result the result of the method execution
     * @return true if result should NOT be cached, false if it should be cached
     */
    public boolean evaluateUnless(String unlessExpression, Method method, Object[] args, Object result) {
        // Empty unless means "always cache"
        if (unlessExpression == null || unlessExpression.trim().isEmpty()) {
            return false;
        }
        
        // Create evaluation context with method parameters AND result
        StandardEvaluationContext context = createEvaluationContext(method, args);
        context.setVariable("result", result);
        
        // Evaluate the unless expression
        try {
            Boolean shouldNotCache = parser.parseExpression(unlessExpression).getValue(context, Boolean.class);
            return shouldNotCache != null && shouldNotCache;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Failed to evaluate unless expression: " + unlessExpression, e
            );
        }
    }
    
    /**
     * Creates a StandardEvaluationContext populated with method parameters.
     * 
     * @param method the method being invoked
     * @param args the method arguments
     * @return evaluation context with parameters
     */
    private StandardEvaluationContext createEvaluationContext(Method method, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // Get parameter names using reflection
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            context.setVariable(paramName, args[i]);
        }
        
        return context;
    }
}

