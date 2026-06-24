package com.example.claudedemo.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SummaryMemory} 单元测试.
 *
 * @since 0.0.1
 */
class SummaryMemoryTest {

    @Test
    void should_build_empty_factory() {
        SummaryMemory s = SummaryMemory.empty();
        assertNotNull(s);
        assertTrue(s.isEmpty());
        assertEquals(null, s.summary());
        assertEquals(List.of(), s.keyFacts());
        assertEquals(0L, s.version());
        assertEquals(0L, s.updatedAtMs());
    }

    @Test
    void should_not_be_empty_when_summary_text_present() {
        SummaryMemory s = new SummaryMemory("一句话摘要", List.of(), 1L);
        assertFalse(s.isEmpty());
    }

    @Test
    void should_not_be_empty_when_keyFacts_present() {
        SummaryMemory s = new SummaryMemory(null, List.of("fact1"), 1L);
        assertFalse(s.isEmpty());
    }

    @Test
    void should_not_be_empty_when_version_positive() {
        SummaryMemory s = new SummaryMemory(null, List.of(), 5L);
        assertFalse(s.isEmpty());
    }

    @Test
    void should_defensive_copy_keyFacts() {
        java.util.ArrayList<String> mutable = new java.util.ArrayList<>(List.of("a", "b"));
        SummaryMemory s = new SummaryMemory("sum", mutable, 1L);
        mutable.add("c");
        assertEquals(2, s.keyFacts().size(), "构造器应拷贝,外部修改不应影响 record");
    }

    @Test
    void should_serialize_to_expected_json() throws Exception {
        ObjectMapper om = new ObjectMapper();
        SummaryMemory s = new SummaryMemory("一句话", List.of("f1", "f2"), 3L, 123L);
        String json = om.writeValueAsString(s);
        assertTrue(json.contains("\"summary\":\"一句话\""));
        assertTrue(json.contains("\"keyFacts\":[\"f1\",\"f2\"]"));
        assertTrue(json.contains("\"version\":3"));
        assertTrue(json.contains("\"updatedAtMs\":123"));
    }

    @Test
    void should_have_record_equality() {
        SummaryMemory a = new SummaryMemory("x", List.of("y"), 1L, 100L);
        SummaryMemory b = new SummaryMemory("x", List.of("y"), 1L, 100L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void should_treat_null_keyFacts_as_empty_list() {
        SummaryMemory s = new SummaryMemory("sum", null, 1L);
        assertEquals(List.of(), s.keyFacts());
        // 同样不可变
        assertThrowsOnModify(s.keyFacts());
    }

    private static void assertThrowsOnModify(List<String> list) {
        try {
            list.add("x");
            org.junit.jupiter.api.Assertions.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertSame(UnsupportedOperationException.class, expected.getClass());
        }
    }
}
