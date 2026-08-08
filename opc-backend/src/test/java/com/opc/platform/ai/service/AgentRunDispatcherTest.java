package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAgentSessionMapper;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AgentRuntimeConfigProvider;
import com.opc.platform.userauth.entity.PlatformUser;
import com.opc.platform.userauth.mapper.PlatformUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunDispatcherTest {

    @Test
    void concurrentDispatchAttemptsExecuteOnlyTheSingleRunThatWasClaimed() throws Exception {
        AgentRunQueueService queueService = mock(AgentRunQueueService.class);
        AgentRunLifecycleService lifecycle = mock(AgentRunLifecycleService.class);
        AgentResearchWorker worker = mock(AgentResearchWorker.class);
        AgentClarificationPolicy clarificationPolicy = mock(AgentClarificationPolicy.class);
        AgentRuntimeConfigProvider configProvider = mock(AgentRuntimeConfigProvider.class);
        AiAgentSessionMapper sessionMapper = mock(AiAgentSessionMapper.class);
        AiAgentMessageMapper messageMapper = mock(AiAgentMessageMapper.class);
        PlatformUserMapper userMapper = mock(PlatformUserMapper.class);
        TaskExecutor taskExecutor = Runnable::run;
        AgentRuntimeConfig config = new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan");
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(60L);
        run.setUserId(41L);
        run.setSessionId(42L);
        run.setUserMessageId(43L);
        AtomicBoolean claimed = new AtomicBoolean();
        when(queueService.claimNext(any())).thenAnswer(invocation -> claimed.compareAndSet(false, true) ? run : null);
        when(configProvider.agentRuntimeConfig()).thenReturn(config);
        AgentRunLease lease = new AgentRunLease(run, null, null, config);
        when(lifecycle.resume(run, config)).thenReturn(lease);
        PlatformUser user = new PlatformUser();
        user.setId(41L);
        user.setUsername("researcher");
        user.setEmail("researcher@example.com");
        user.setStatus("active");
        when(userMapper.selectById(41L)).thenReturn(user);
        AiAgentSession session = new AiAgentSession();
        session.setId(42L);
        session.setStatus("active");
        session.setTaskContextJson("{}");
        when(sessionMapper.selectById(42L)).thenReturn(session);
        AiAgentMessage message = new AiAgentMessage();
        message.setId(43L);
        message.setContent("Research");
        when(messageMapper.selectById(43L)).thenReturn(message);
        when(clarificationPolicy.runtimeProfile(any(), any())).thenReturn("{}");
        AgentRunDispatcher dispatcher = new AgentRunDispatcher(
                queueService, lifecycle, worker, clarificationPolicy, configProvider,
                sessionMapper, messageMapper, userMapper, taskExecutor);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> {
                start.await();
                dispatcher.processNext();
                return null;
            });
            Future<?> second = executor.submit(() -> {
                start.await();
                dispatcher.processNext();
                return null;
            });
            start.countDown();
            first.get(1, TimeUnit.SECONDS);
            second.get(1, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }

        verify(queueService, times(2)).claimNext(any());
        verify(lifecycle, times(1)).resume(run, config);
        verify(worker, times(1)).execute(eq(lease), any(), eq("{}"), eq("Research"), eq("{}"));
    }

    @Test
    void scheduledTickReturnsWhileWorkerExecutionIsBlockedAndSubmitsOneClaim() throws Exception {
        AgentRunQueueService queueService = mock(AgentRunQueueService.class);
        AgentRunLifecycleService lifecycle = mock(AgentRunLifecycleService.class);
        AgentResearchWorker worker = mock(AgentResearchWorker.class);
        AgentClarificationPolicy clarificationPolicy = mock(AgentClarificationPolicy.class);
        AgentRuntimeConfigProvider configProvider = mock(AgentRuntimeConfigProvider.class);
        AiAgentSessionMapper sessionMapper = mock(AiAgentSessionMapper.class);
        AiAgentMessageMapper messageMapper = mock(AiAgentMessageMapper.class);
        PlatformUserMapper userMapper = mock(PlatformUserMapper.class);
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        ExecutorService agentExecutor = Executors.newSingleThreadExecutor();
        ExecutorService schedulerExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);

        try {
            doAnswer(invocation -> {
                agentExecutor.execute(invocation.getArgument(0, Runnable.class));
                return null;
            }).when(taskExecutor).execute(any(Runnable.class));

            AgentRuntimeConfig config = new AgentRuntimeConfig(
                    true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan");
            AiAnalysisRun run = new AiAnalysisRun();
            run.setId(40L);
            run.setUserId(41L);
            run.setSessionId(42L);
            run.setUserMessageId(43L);
            AgentRunLease lease = new AgentRunLease(run, null, null, config);
            when(queueService.claimNext(any())).thenReturn(run);
            when(configProvider.agentRuntimeConfig()).thenReturn(config);
            when(lifecycle.resume(run, config)).thenReturn(lease);

            PlatformUser user = new PlatformUser();
            user.setId(41L);
            user.setUsername("researcher");
            user.setEmail("researcher@example.com");
            user.setStatus("active");
            when(userMapper.selectById(41L)).thenReturn(user);

            AiAgentSession session = new AiAgentSession();
            session.setId(42L);
            session.setStatus("active");
            session.setTaskContextJson("{}");
            when(sessionMapper.selectById(42L)).thenReturn(session);

            AiAgentMessage message = new AiAgentMessage();
            message.setId(43L);
            message.setContent("Research Hubei AI opportunities");
            when(messageMapper.selectById(43L)).thenReturn(message);
            when(clarificationPolicy.runtimeProfile(any(), any())).thenReturn("{}");
            doAnswer(invocation -> {
                workerStarted.countDown();
                if (!releaseWorker.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("worker was not released");
                }
                return null;
            }).when(worker).execute(
                    eq(lease), any(), eq("{}"), eq("Research Hubei AI opportunities"), eq("{}"));

            AgentRunDispatcher dispatcher = new AgentRunDispatcher(
                    queueService, lifecycle, worker, clarificationPolicy, configProvider,
                    sessionMapper, messageMapper, userMapper, taskExecutor);
            ReflectionTestUtils.setField(dispatcher, "scheduledWorkerEnabled", true);

            Future<?> scheduledTick = schedulerExecutor.submit(dispatcher::scheduledTick);

            assertTrue(workerStarted.await(1, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> scheduledTick.get(1, TimeUnit.SECONDS));
            assertEquals(1L, releaseWorker.getCount());
            verify(taskExecutor, times(1)).execute(any(Runnable.class));
            verify(queueService, times(1)).claimNext(any());
            verify(worker, times(1)).execute(
                    eq(lease), any(), eq("{}"), eq("Research Hubei AI opportunities"), eq("{}"));
        } finally {
            releaseWorker.countDown();
            schedulerExecutor.shutdown();
            agentExecutor.shutdown();
            schedulerExecutor.awaitTermination(5, TimeUnit.SECONDS);
            agentExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
