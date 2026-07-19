package com.mnnu.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 接口鉴权配置类
 *
 * 从 application.yml 读取 auth.token。
 * 前端请求 /api/chat 时必须在 Header 中携带 X-Auth-Token，
 * 与后端配置的 token 匹配才允许访问，防止接口被恶意调用。
 */
@Configuration
@ConfigurationProperties(prefix = "auth")
public class AuthConfig {

    /** 鉴权令牌 */
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
