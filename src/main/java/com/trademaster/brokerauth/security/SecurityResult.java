package com.trademaster.brokerauth.security;

import java.util.Optional;
import java.util.function.Function;

/**
 * Security Result - Functional Result type for security operations
 * 
 * MANDATORY: Functional Programming - Rule #3
 * MANDATORY: Railway Programming - Rule #11
 * MANDATORY: No null returns - Rule #11
 */
public sealed interface SecurityResult<T> permits SecurityResult.Success, SecurityResult.Failure {
    
    boolean isSuccess();
    boolean isFailure();
    
    Optional<T> getValue();
    Optional<SecurityError> getError();
    Optional<String> getMessage();
    
    /**
     * Transform successful result
     */
    <U> SecurityResult<U> map(Function<T, U> mapper);
    
    /**
     * Chain operations (Railway Programming)
     */
    <U> SecurityResult<U> flatMap(Function<T, SecurityResult<U>> mapper);
    
    /**
     * Execute side effect on success
     */
    SecurityResult<T> peek(java.util.function.Consumer<T> action);

    /**
     * Pattern matching for functional programming
     */
    <U> U match(Function<T, U> onSuccess, Function<SecurityError, U> onFailure);
    
    record Success<T>(T value, SecurityContext context) implements SecurityResult<T> {
        
        @Override
        public boolean isSuccess() { return true; }
        
        @Override
        public boolean isFailure() { return false; }
        
        @Override
        public Optional<T> getValue() { return Optional.of(value); }
        
        @Override
        public Optional<SecurityError> getError() { return Optional.empty(); }
        
        @Override
        public Optional<String> getMessage() { return Optional.empty(); }
        
        @Override
        public <U> SecurityResult<U> map(Function<T, U> mapper) {
            try {
                return new Success<>(mapper.apply(value), context);
            } catch (Exception e) {
                return new Failure<>(SecurityError.MAPPING_ERROR, e.getMessage());
            }
        }
        
        @Override
        public <U> SecurityResult<U> flatMap(Function<T, SecurityResult<U>> mapper) {
            try {
                return mapper.apply(value);
            } catch (Exception e) {
                return new Failure<>(SecurityError.MAPPING_ERROR, e.getMessage());
            }
        }
        
        @Override
        public SecurityResult<T> peek(java.util.function.Consumer<T> action) {
            try {
                action.accept(value);
                return this;
            } catch (Exception e) {
                return new Failure<>(SecurityError.SIDE_EFFECT_ERROR, e.getMessage());
            }
        }

        @Override
        public <U> U match(Function<T, U> onSuccess, Function<SecurityError, U> onFailure) {
            return onSuccess.apply(value);
        }
    }
    
    record Failure<T>(SecurityError error, String message) implements SecurityResult<T> {
        
        @Override
        public boolean isSuccess() { return false; }
        
        @Override
        public boolean isFailure() { return true; }
        
        @Override
        public Optional<T> getValue() { return Optional.empty(); }
        
        @Override
        public Optional<SecurityError> getError() { return Optional.of(error); }
        
        @Override
        public Optional<String> getMessage() { return Optional.of(message); }
        
        @Override
        public <U> SecurityResult<U> map(Function<T, U> mapper) {
            return new Failure<>(error, message);
        }
        
        @Override
        public <U> SecurityResult<U> flatMap(Function<T, SecurityResult<U>> mapper) {
            return new Failure<>(error, message);
        }
        
        @Override
        public SecurityResult<T> peek(java.util.function.Consumer<T> action) {
            return this;
        }

        @Override
        public <U> U match(Function<T, U> onSuccess, Function<SecurityError, U> onFailure) {
            return onFailure.apply(error);
        }
    }
    
    /**
     * Factory methods
     */
    static <T> SecurityResult<T> success(T value, SecurityContext context) {
        return new Success<>(value, context);
    }
    
    static <T> SecurityResult<T> failure(SecurityError error, String message) {
        return new Failure<>(error, message);
    }
}