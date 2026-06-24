package com.example.claudedemo.agent.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TurnCountCompressionPolicy} 单元测试.
 *
 * @since 0.0.1
 */
class TurnCountCompressionPolicyTest {

    @Test
    void should_not_compress_below_threshold() {
        TurnCountCompressionPolicy p = new TurnCountCompressionPolicy(10, 4);
        assertFalse(p.shouldCompress(0));
        assertFalse(p.shouldCompress(1));
        assertFalse(p.shouldCompress(9));
    }

    @Test
    void should_compress_at_threshold() {
        TurnCountCompressionPolicy p = new TurnCountCompressionPolicy(10, 4);
        assertTrue(p.shouldCompress(10));
        assertTrue(p.shouldCompress(15));
        assertTrue(p.shouldCompress(100));
    }

    @Test
    void should_expose_keepRecentTurns() {
        TurnCountCompressionPolicy p = new TurnCountCompressionPolicy(10, 4);
        assertEquals(4, p.keepRecentTurns());
        assertEquals(10, p.threshold());
    }

    @Test
    void should_reject_invalid_threshold() {
        assertThrows(IllegalArgumentException.class, () -> new TurnCountCompressionPolicy(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TurnCountCompressionPolicy(-1, 0));
    }

    @Test
    void should_reject_negative_keepRecent() {
        assertThrows(IllegalArgumentException.class, () -> new TurnCountCompressionPolicy(10, -1));
    }

    @Test
    void should_reject_keepRecent_ge_threshold() {
        assertThrows(IllegalArgumentException.class, () -> new TurnCountCompressionPolicy(10, 10));
        assertThrows(IllegalArgumentException.class, () -> new TurnCountCompressionPolicy(10, 11));
    }

    @Test
    void should_accept_keepRecent_zero() {
        TurnCountCompressionPolicy p = new TurnCountCompressionPolicy(10, 0);
        assertEquals(0, p.keepRecentTurns());
        assertTrue(p.shouldCompress(10));
    }
}
