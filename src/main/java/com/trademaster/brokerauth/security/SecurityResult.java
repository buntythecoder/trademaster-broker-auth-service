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
     * MANDATORY: Rule #11 - Railway Programming
     */
    <U> SecurityResult<U> map(Function<T, U> mapper);

    /**
     * Chain operations (Railway Programming)
     * MANDATORY: Rule #11 - Monadic composition
     */
    <U> SecurityResult<U> flatMap(Function<T, SecurityResult<U>> mapper);

    /**
     * Recover from failure by providing default value
     * MANDATORY: Rule #11 - Error recovery patterns
     */
    SecurityResult<T> recover(Function<SecurityError, T> recovery);

    /**
     * Recover from failure by providing alternative SecurityResult
     * MANDATORY: Rule #11 - Railway Programming recovery
     */
    SecurityResult<T> recoverWith(Function<SecurityError, SecurityResult<T>> recovery);

    /**
     * Execute side effect on success
     * MANDATORY: Rule #3 - Functional side effects
     */
    SecurityResult<T> peek(java.util.function.Consumer<T> action);

    /**
     * Pattern matching for functional programming
     * MANDATORY: Rule #14 - Pattern matching support
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
        
        /**
         * MANDATORY: Rule #3 - No try-catch, use CompletableFuture.handle
         */
        @Override
        public <U> SecurityResult<U> map(Function<T, U> mapper) {
            return java.util.concurrent.CompletableFuture
                .supplyAsync(
                    () -> mapper.apply(value),
                    java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
                )
                .handle((result, throwable) -> throwable != null
                    ? new Failure<U>(SecurityError.MAPPING_ERROR, throwable.getMessage())
                    : new Success<>(result, context))
                .join();
        }

        /**
         * MANDATORY: Rule #3 - No try-catch, use CompletableFuture.handle
         */
        @Override
        public <U> SecurityResult<U> flatMap(Function<T, SecurityResult<U>> mapper) {
            return java.util.concurrent.CompletableFuture
                .supplyAsync(
                    () -> mapper.apply(value),
                    java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
                )
                .handle((result, throwable) -> throwable != null
                    ? new Failure<U>(SecurityError.MAPPING_ERROR, throwable.getMessage())
                    : result)
                .join();
        }

        /**
         * MANDATORY: Rule #11 - Recover from failure (no-op for Success)
         */
        @Override
        public SecurityResult<T> recover(Function<SecurityError, T> recovery) {
            return this; // Already successful, no recovery needed
        }

        /**
         * MANDATORY: Rule #11 - Recover with alternative result (no-op for Success)
         */
        @Override
        public SecurityResult<T> recoverWith(Function<SecurityError, SecurityResult<T>> recovery) {
            return this; // Already successful, no recovery needed
        }

        /**
         * MANDATORY: Rule #3 - No try-catch, use CompletableFuture.handle
         */
        @Override
        public SecurityResult<T> peek(java.util.function.Consumer<T> action) {
            return java.util.concurrent.CompletableFuture
                .runAsync(
                    () -> action.accept(value),
                    java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
                )
                .handle((voidResult, throwable) -> throwable != null
                    ? new Failure<T>(SecurityError.SIDE_EFFECT_ERROR, throwable.getMessage())
                    : this)
                .join();
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

        /**
         * MANDATORY: Rule #11 - Recover from failure by providing default value
         */
        @Override
        public SecurityResult<T> recover(Function<SecurityError, T> recovery) {
            return java.util.concurrent.CompletableFuture
                .supplyAsync(
                    () -> recovery.apply(error),
                    java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
                )
                .handle((result, throwable) -> throwable != null
                    ? new Failure<T>(SecurityError.RECOVERY_FAILED, throwable.getMessage())
                    : new Success<>(result, null)) // No context for recovered value
                .join();
        }

        /**
         * MANDATORY: Rule #11 - Recover with alternative SecurityResult
         */
        @Override
        public SecurityResult<T> recoverWith(Function<SecurityError, SecurityResult<T>> recovery) {
            return java.util.concurrent.CompletableFuture
                .supplyAsync(
                    () -> recovery.apply(error),
                    java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
                )
                .handle((result, throwable) -> throwable != null
                    ? new Failure<T>(SecurityError.RECOVERY_FAILED, throwable.getMessage())
                    : result)
                .join();
        }

        @Override
        public SecurityResult<T> peek(java.util.function.Consumer<T> action) {
            return this; // No value to peek, pass through failure
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