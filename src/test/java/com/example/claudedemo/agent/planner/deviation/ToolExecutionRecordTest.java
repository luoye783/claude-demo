package com.example.claudedemo.agent.planner.deviation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ToolExecutionRecord} 单元测试.
 *
 * @since 0.0.1
 */
class ToolExecutionRecordTest {

    @Test
    void success_record_has_no_error_message() {
        ToolExecutionRecord r = new ToolExecutionRecord(
                "call_1", "get_schema", 1, true, 100L, 200L, null);
        assertEquals("call_1", r.toolCallId());
        assertEquals("get_schema", r.toolName());
        assertEquals(1, r.order());
        assertTrue(r.success());
        assertNull(r.errorMessage());
    }

    @Test
    void failure_record_has_error_message() {
        ToolExecutionRecord r = new ToolExecutionRecord(
                "call_2", "execute_sql", 2, false, 100L, 200L, "SQL error");
        assertFalse(r.success());
        assertEquals("SQL error", r.errorMessage());
    }

    @Test
    void order_increments() {
        ToolExecutionRecord r1 = new ToolExecutionRecord("c1", "t1", 1, true, 0, 1, null);
        ToolExecutionRecord r2 = new ToolExecutionRecord("c2", "t2", 2, true, 1, 2, null);
        assertTrue(r1.order() < r2.order());
    }
}
