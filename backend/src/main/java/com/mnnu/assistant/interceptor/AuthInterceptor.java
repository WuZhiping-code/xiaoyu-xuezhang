package com.mnnu.assistant.interceptor;

import com.mnnu.assistant.config.AuthConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 简易 Token 鉴权拦截器
 *
 * 校验请求 Header 中的 X-Auth-Token 是否与后端配置一致。
 * 这是第一道防线，防止接口被陌生人随意调用。
 *
 * 注意：这不是生产级别安全方案（Token明文传输），
 * 适合校园助手这类低敏感度场景。如需更高安全级别，
 * 可升级为 JWT + HTTPS + 限流 方案。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    /** Header中鉴权Token的键名 */
    public static final String AUTH_HEADER = "X-Auth-Token";

    private final AuthConfig authConfig;

    public AuthInterceptor(AuthConfig authConfig) {
        this.authConfig = authConfig;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // CORS 预检请求（OPTIONS）不校验Token，直接放行
        // 否则浏览器在跨域场景下无法完成预检，导致"Failed to fetch"
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 获取请求中的Token
        String token = request.getHeader(AUTH_HEADER);

        // 与配置中的Token比对
        if (authConfig.getToken().equals(token)) {
            return true; // 鉴权通过，放行
        }

        // 鉴权失败：返回401
        log.warn("接口鉴权失败！请求方法: {}, 请求IP: {}, 携带Token: {}",
                request.getMethod(),
                request.getRemoteAddr(),
                token == null ? "无" : token.substring(0, Math.min(8, token.length())) + "...");

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"未授权访问，请提供有效的X-Auth-Token\"}");
        return false;
    }
}
