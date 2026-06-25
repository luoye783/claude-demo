package com.example.claudedemo.agent.session;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link TokenUsage} 单元测试.
 *
 * @since 0.0.1
 */
class TokenUsageTest {

    @Test
    void should_initialize_to_zero() {
        TokenUsage t = new TokenUsage();
        assertEquals(0L, t.promptTokens());
        assertEquals(0L, t.completionTokens());
        assertEquals(0L, t.totalTokens());
    }

    @Test
    void should_accumulate_prompt() {
        TokenUsage t = new TokenUsage();
        t.addPrompt(100);
        t.addPrompt(50);
        assertEquals(150L, t.promptTokens());
        assertEquals(0L, t.completionTokens());
        assertEquals(150L, t.totalTokens());
    }

    @Test
    void should_accumulate_completion() {
        TokenUsage t = new TokenUsage();
        t.addCompletion(200);
        t.addCompletion(75);
        assertEquals(275L, t.completionTokens());
        assertEquals(0L, t.promptTokens());
        assertEquals(275L, t.totalTokens());
    }

    @Test
    void should_track_separate_prompt_and_completion() {
        TokenUsage t = new TokenUsage();
        t.addPrompt(100);
        t.addCompletion(50);
        assertEquals(100L, t.promptTokens());
        assertEquals(50L, t.completionTokens());
        assertEquals(150L, t.totalTokens());
    }

    @Test
    void should_reject_negative_prompt() {
        TokenUsage t = new TokenUsage();
        assertThrows(IllegalArgumentException.class, () -> t.addPrompt(-1));
    }

    @Test
    void should_reject_negative_completion() {
        TokenUsage t = new TokenUsage();
        assertThrows(IllegalArgumentException.class, () -> t.addCompletion(-1));
    }

    @Test
    void should_merge_other_token_usage() {
        TokenUsage a = new TokenUsage();
        a.addPrompt(100);
        a.addCompletion(50);

        TokenUsage b = new TokenUsage();
        b.addPrompt(30);
        b.addCompletion(20);

        a.add(b);
        assertEquals(130L, a.promptTokens());
        assertEquals(70L, a.completionTokens());
        assertEquals(200L, a.totalTokens());
    }

    @Test
    void should_treat_null_add_as_noop() {
        TokenUsage t = new TokenUsage();
        t.addPrompt(10);
        t.add(null);
        assertEquals(10L, t.promptTokens());
        assertEquals(10L, t.totalTokens());
    }

    @Test
    void should_reset_to_zero() {
        TokenUsage t = new TokenUsage();
        t.addPrompt(100);
        t.addCompletion(50);
        t.reset();
        assertEquals(0L, t.promptTokens());
        assertEquals(0L, t.completionTokens());
        assertEquals(0L, t.totalTokens());
    }

    @Test
    void should_copy_via_constructor() {
        TokenUsage src = new TokenUsage();
        src.addPrompt(100);
        src.addCompletion(50);

        TokenUsage copy = new TokenUsage(src);
        assertEquals(100L, copy.promptTokens());
        assertEquals(50L, copy.completionTokens());
        assertEquals(150L, copy.totalTokens());

        // 修改 copy 不影响 src
        copy.addPrompt(999);
        assertEquals(100L, src.promptTokens());
        assertEquals(1099L, copy.promptTokens());
    }

    @Test
    void should_handle_null_in_copy_constructor() {
        TokenUsage copy = new TokenUsage(null);
        assertEquals(0L, copy.promptTokens());
    }

    @Test
    void should_be_thread_safe_under_concurrent_increments() throws Exception {
        TokenUsage t = new TokenUsage();
        int threads = 10;
        int incrementsPerThread = 1000;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        t.addPrompt(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertEquals(true, done.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        long expected = (long) threads * incrementsPerThread;
        assertEquals(expected, t.promptTokens());
        assertEquals(expected, t.totalTokens());
    }
}
