package com.mnnu.assistant.config;

import com.mnnu.assistant.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * 1. 注册鉴权拦截器，对 /api/** 路径进行Token校验
 * 2. 配置CORS跨域，允许前端页面（无论部署在哪）调用后端接口
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * 注册鉴权拦截器
     * /api/chat 需要校验 X-Auth-Token
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")           // 拦截所有 /api/ 路径
                .excludePathPatterns("/api/health");  // 健康检查接口不需要鉴权
    }

    /**
     * CORS 跨域配置
     * 允许前端页面跨域访问后端接口
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")       // 允许所有来源（生产环境建议限制具体域名）
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
