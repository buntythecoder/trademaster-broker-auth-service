package com.trademaster.brokerauth.util;

import com.trademaster.brokerauth.security.SecurityContext;
import com.trademaster.brokerauth.security.SecurityError;
import com.trademaster.brokerauth.security.SecurityResult;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Safe Executor Utility - Functional Error Handling
 *
 * MANDATORY: Rule #3 - Functional Programming (no try-catch)
 * MANDATORY: Rule #11 - Railway Programming (error wrapping with Result types)
 * MANDATORY: Rule #12 - Virtual Threads (CompletableFuture support)
 *
 * Provides functional error handling patterns to eliminate try-catch blocks
 * throughout the codebase while maintaining proper error reporting.
 *
 * @author TradeMaster Development Team
 * @version 1.0.0
 */
@Slf4j
public final class SafeExecutor {

    private SafeExecutor() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Execute async operation safely with functional error handling
     *
     * MANDATORY: Rule #3 - No try-catch, use CompletableFuture.exceptionally
     * MANDATORY: Rule #12 - Virtual Threads compatible
     *
     * @param operation Async operation to execute
     * @param context Security context for correlation
     * @param <T> Result type
     * @return CompletableFuture with SecurityResult wrapping success or failure
     */
    public static <T> CompletableFuture<SecurityResult<T>> executeAsync(
            Supplier<CompletableFuture<T>> operation,
            SecurityContext context) {

        return CompletableFuture
            .supplyAsync(operation, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
            .thenCompose(future -> future
                .thenApply(result -> SecurityResult.success(result, context))
                .exceptionally(throwable -> {
                    log.error("Async operation failed: correlation={}, error={}",
                        context.correlationId(), throwable.getMessage());
                    return SecurityResult.failure(
                        SecurityError.OPERATION_FAILED,
                        throwable.getMessage()
                    );
                }));
    }

    /**
     * Execute sync operation safely with functional error handling
     *
     * MANDATORY: Rule #3 - No try-catch, use CompletableFuture.handle
     * MANDATORY: Rule #11 - Railway Programming with Result types
     *
     * @param operation Sync operation to execute
     * @param context Security context for correlation
     * @param <T> Result type
     * @return SecurityResult wrapping success or failure
     */
    public static <T> SecurityResult<T> executeSync(
            Function<SecurityContext, T> operation,
            SecurityContext context) {

        return CompletableFuture
            .supplyAsync(
                () -> operation.apply(context),
                java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
            )
            .handle((result, throwable) -> {
                if (throwable != null) {
                    log.error("Sync operation failed: correlation={}, error={}",
                        context.correlationId(), throwable.getMessage());
                    return SecurityResult.<T>failure(
                        SecurityError.OPERATION_FAILED,
                        throwable.getMessage()
                    );
                }
                return SecurityResult.success(result, context);
            })
            .join();
    }

    /**
     * Execute simple supplier safely with functional error handling
     *
     * MANDATORY: Rule #3 - No try-catch, use CompletableFuture.handle
     *
     * @param operation Supplier operation to execute
     * @param context Security context for correlation
     * @param <T> Result type
     * @return SecurityResult wrapping success or failure
     */
    public static <T> SecurityResult<T> executeSupplier(
            Supplier<T> operation,
            SecurityContext context) {

        return CompletableFuture
            .supplyAsync(operation, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
            .handle((result, throwable) -> {
                if (throwable != null) {
                    log.error("Supplier operation failed: correlation={}, error={}",
                        context.correlationId(), throwable.getMessage());
                    return SecurityResult.<T>failure(
                        SecurityError.OPERATION_FAILED,
                        throwable.getMessage()
                    );
                }
                return SecurityResult.success(result, context);
            })
            .join();
    }

    /**
     * Execute async operation with custom error mapping
     *
     * MANDATORY: Rule #3 - Functional error transformation
     * MANDATORY: Rule #11 - Railway Programming with error mapping
     *
     * @param operation Async operation to execute
     * @param context Security context for correlation
     * @param errorMapper Function to map throwable to SecurityError
     * @param <T> Result type
     * @return CompletableFuture with SecurityResult
     */
    public static <T> CompletableFuture<SecurityResult<T>> executeAsyncWithMapping(
            Supplier<CompletableFuture<T>> operation,
            SecurityContext context,
            Function<Throwable, SecurityError> errorMapper) {

        return CompletableFuture
            .supplyAsync(operation, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
            .thenCompose(future -> future
                .thenApply(result -> SecurityResult.success(result, context))
                .exceptionally(throwable -> {
                    SecurityError error = errorMapper.apply(throwable);
                    log.error("Async operation failed: correlation={}, error={}, type={}",
                        context.correlationId(), throwable.getMessage(), error);
                    return SecurityResult.failure(error, throwable.getMessage());
                }));
    }

    /**
     * Execute sync operation with custom error mapping
     *
     * MANDATORY: Rule #3 - Functional error transformation
     *
     * @param operation Sync operation to execute
     * @param context Security context for correlation
     * @param errorMapper Function to map throwable to SecurityError
     * @param <T> Result type
     * @return SecurityResult
     */
    public static <T> SecurityResult<T> executeSyncWithMapping(
            Function<SecurityContext, T> operation,
            SecurityContext context,
            Function<Throwable, SecurityError> errorMapper) {

        return CompletableFuture
            .supplyAsync(
                () -> operation.apply(context),
                java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
            )
            .handle((result, throwable) -> {
                if (throwable != null) {
                    SecurityError error = errorMapper.apply(throwable);
                    log.error("Sync operation failed: correlation={}, error={}, type={}",
                        context.correlationId(), throwable.getMessage(), error);
                    return SecurityResult.<T>failure(error, throwable.getMessage());
                }
                return SecurityResult.success(result, context);
            })
            .join();
    }
}
