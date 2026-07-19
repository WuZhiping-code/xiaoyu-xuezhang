package com.mnnu.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * DeepSeek 大模型配置类
 *
 * 从 application.yml 中读取 deepseek.* 配置项。
 * 密钥、BaseURL、模型名称等全部在后端配置，前端JS无法获取。
 */
@Configuration
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekConfig {

    /** DeepSeek API Key（敏感信息，仅后端持有） */
    private String apiKey;

    /** DeepSeek API 基础地址，如 https://api.deepseek.com/v1 */
    private String baseUrl;

    /** 模型名称，如 deepseek-chat / deepseek-reasoner */
    private String model;

    /** 系统提示词，定义AI人设 */
    private String systemPrompt;

    /** HTTP请求超时（毫秒） */
    private int timeout = 60000;

    /** 温度参数，0-2 */
    private double temperature = 0.7;

    /** 最大输出token数 */
    private int maxTokens = 1000;

    // ===== Getters & Setters =====

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
}
