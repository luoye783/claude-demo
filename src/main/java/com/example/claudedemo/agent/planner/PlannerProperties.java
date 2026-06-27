package com.example.claudedemo.agent.planner;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Planner 配置(Agent Runtime V3).
 *
 * <p>对应 application.yml 中的 {@code agent.planner} 配置块。
 *
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "agent.planner")
public class PlannerProperties {

    /** 是否启用 Planner(默认关闭,避免影响老测试). */
    private boolean enabled = false;

    /** Planner 类型(simple). */
    private String type = "simple";

    /** 是否启用偏差检测(默认 true,仅在 planner 启用时生效). */
    private boolean deviationDetectionEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isDeviationDetectionEnabled() {
        return deviationDetectionEnabled;
    }

    public void setDeviationDetectionEnabled(boolean deviationDetectionEnabled) {
        this.deviationDetectionEnabled = deviationDetectionEnabled;
    }
}
