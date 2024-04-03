/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.kroxylicious.proxy.internal;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromiseFactoryTest {

    private static final int TIMEOUT = 50;
    private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MILLISECONDS;
    private PromiseFactory promiseFactory;

    @BeforeEach
    void setUp() {
        promiseFactory = new PromiseFactory(Executors.newSingleThreadScheduledExecutor(), TIMEOUT, TIMEOUT_UNIT);
    }

    @Test
    void shouldCreateNewPromise() {
        // Given

        // When
        final CompletableFuture<Object> promise = promiseFactory.newPromise();

        // Then
        assertThat(promise).isNotNull().isNotDone();
    }

    @Test
    void shouldCreateNewPromiseOnEachInvocation() {
        // Given
        final CompletableFuture<Object> promise = promiseFactory.newPromise();

        // When
        final CompletableFuture<Object> promise2 = promiseFactory.newPromise();

        // Then
        assertThat(promise2).isNotSameAs(promise);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldCreatePromiseWithTimeout() {
        // Given
        final ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        promiseFactory = new PromiseFactory(executorService, TIMEOUT, TIMEOUT_UNIT);
        when(executorService.schedule(any(Runnable.class), anyLong(), any())).thenReturn(mock(ScheduledFuture.class));

        // When
        final CompletableFuture<Object> promise = promiseFactory.newTimeLimitedPromise(() -> "");

        // Then
        assertThat(promise).isNotNull().isNotDone();
        verify(executorService).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void shouldCancelTimeoutWhenTaskCompletes() {

        // ScheduledExecutorService is only auto closeable in JDK 19+
        //noinspection resource
        final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        try {
            final ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
            promiseFactory = new PromiseFactory(executorService, TIMEOUT, TIMEOUT_UNIT);

            final AtomicReference<ScheduledFuture<?>> timeoutFuture = new AtomicReference<>();
            when(executorService.schedule(any(Runnable.class), anyLong(), any())).thenAnswer(invocationOnMock -> {
                final ScheduledFuture<?> newValue = scheduledExecutorService.schedule((Runnable) invocationOnMock.getArgument(0),
                        invocationOnMock.getArgument(1),
                        invocationOnMock.getArgument(2));
                timeoutFuture.set(newValue);
                return newValue;
            });

            final CompletableFuture<Object> promise = promiseFactory.newTimeLimitedPromise(() -> "");

            // When
            promise.complete(null);

            // Then
            assertThat(timeoutFuture).satisfies(atomicRef -> assertThat(atomicRef).hasValueMatching(Objects::nonNull)
                    .hasValueSatisfying(scheduledFuture -> Assertions.FUTURE.createAssert(scheduledFuture).isCancelled()));
        }
        finally {
            scheduledExecutorService.shutdownNow();
        }
    }
}