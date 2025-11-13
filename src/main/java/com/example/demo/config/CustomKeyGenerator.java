package com.example.demo.config;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Example custom KeyGenerator implementation.
 * 
 * This generates cache keys in the format: "ClassName.methodName(arg1,arg2,...)"
 */
@Component("customKeyGenerator")
public class CustomKeyGenerator implements KeyGenerator {
    
    @Override
    public Object generate(Object target, Method method, Object... params) {
        String className = target.getClass().getSimpleName();
        String methodName = method.getName();
        String paramsString = Arrays.stream(params)
            .map(param -> param != null ? param.toString() : "null")
            .collect(Collectors.joining(","));
        
        return className + "." + methodName + "(" + paramsString + ")";
    }
}

