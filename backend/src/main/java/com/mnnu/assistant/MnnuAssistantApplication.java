package com.mnnu.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 闽师小羽学长 - AI智能校园助手中转后端
 *
 * 启动类。使用 SpringBoot 内嵌 Tomcat，默认端口 8080。
 *
 * 安全架构说明：
 * DeepSeek API Key 仅存储在服务器端 application.yml 中，
 * 前端JS代码中不包含任何密钥信息。
 * 前端通过 POST /api/chat 发送问题 → 后端鉴权 → 后端代调用DeepSeek → SSE流式返回。
 */
@SpringBootApplication
public class MnnuAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(MnnuAssistantApplication.class, args);
        System.out.println("========================================");
        System.out.println("  闽师小羽学长 AI助手后端已启动！");
        System.out.println("  接口地址: http://localhost:8080/api/chat");
        System.out.println("  鉴权方式: Header X-Auth-Token");
        System.out.println("  大模型: DeepSeek (密钥保护在后端)");
        System.out.println("========================================");
    }
}
