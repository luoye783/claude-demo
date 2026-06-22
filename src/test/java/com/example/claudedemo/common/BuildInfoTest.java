package com.example.claudedemo.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BuildInfo 单元测试：版本号常量的值.
 *
 * @author claude-code
 * @since 0.0.1
 */
class BuildInfoTest {

    @Test
    void should_have_version_0_0_1() {
        assertEquals("0.0.1", BuildInfo.VERSION);
    }
}
