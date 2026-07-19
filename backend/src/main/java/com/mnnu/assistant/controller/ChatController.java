package com.mnnu.assistant.controller;

import com.mnnu.assistant.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI对话接口控制器
 *
 * POST /api/chat
 * 接收前端提问 → 鉴权拦截器已校验Token → 调用ChatService → SSE流式返回AI回复
 *
 * 安全架构：
 * 1. 请求需携带 Header: X-Auth-Token（由AuthInterceptor校验）
 * 2. DeepSeek API Key 仅在后端使用，前端JS不可见
 * 3. 后端作为"中转代理"，前端只知道自己的后端地址
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 流式对话接口（SSE）
     *
     * 请求体格式：
     * {
     *   "question": "闽南师范大学的录取分数线是多少？"
     * }
     *
     * 返回：SSE 流（text/event-stream）
     * 前端通过 EventSource 或 fetch + ReadableStream 接收
     *
     * @param body 请求体，包含 question 字段
     * @return SseEmitter 流式响应对象
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, String> body) {

        String question = body.get("question");

        // 参数校验
        if (question == null || question.trim().isEmpty()) {
            // 返回一个立即完成的 emitter，带上错误信息
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("问题不能为空哦~请输入你想了解的内容"));
                errorEmitter.complete();
            } catch (Exception e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        log.info("收到AI提问: {}", question.length() > 60 ? question.substring(0, 60) + "..." : question);

        // 调用 Service 进行流式对话
        return chatService.chatStream(question.trim());
    }

    /**
     * 健康检查接口（无需鉴权，已在WebConfig中排除）
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "闽师小羽学长 AI助手后端",
                "model", "DeepSeek",
                "timestamp", System.currentTimeMillis()
        );
    }
}
