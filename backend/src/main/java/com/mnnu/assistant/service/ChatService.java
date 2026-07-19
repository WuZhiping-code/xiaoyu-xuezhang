package com.mnnu.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnnu.assistant.config.DeepSeekConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI对话服务
 *
 * 核心逻辑：
 * 1. 接收前端问题
 * 2. 构建 OpenAI 兼容格式的请求体
 * 3. 通过 OkHttp 流式调用 DeepSeek API（stream=true）
 * 4. 逐行解析 DeepSeek 返回的 SSE 数据
 * 5. 提取内容增量，通过 SseEmitter 实时推送给前端
 *
 * 安全要点：
 * DeepSeek API Key 只在这个服务类中使用，从 application.yml 注入，
 * 永远不会出现在前端JS代码中。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final DeepSeekConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** 虚拟线程执行器，处理流式请求（Java 21+可用，Java 17用固定线程池） */
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public ChatService(DeepSeekConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();

        // 配置 OkHttp 客户端：读取超时设为配置值，支持长连接SSE流
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(java.time.Duration.ofMillis(config.getTimeout()))
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .writeTimeout(java.time.Duration.ofSeconds(30))
                .build();
    }

    /**
     * 流式对话
     *
     * @param userQuestion 用户问题
     * @return SseEmitter 对象，Controller层直接返回给前端
     */
    public SseEmitter chatStream(String userQuestion) {
        // 创建 SSE 发射器，超时时间与 DeepSeek 请求超时一致
        SseEmitter emitter = new SseEmitter((long) config.getTimeout());

        // 在独立线程中执行流式调用（不阻塞主线程）
        executor.execute(() -> {
            try {
                doStreamChat(emitter, userQuestion);
            } catch (Exception e) {
                log.error("流式对话异常", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("抱歉，AI服务暂时不可用：" + e.getMessage()));
                } catch (IOException ex) {
                    log.error("发送错误信息失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 执行流式对话的具体逻辑
     */
    private void doStreamChat(SseEmitter emitter, String userQuestion) throws IOException {
        // 1. 构建 OpenAI 兼容请求体
        Map<String, Object> requestBody = buildRequestBody(userQuestion);
        String jsonBody = objectMapper.writeValueAsString(requestBody);

        log.debug("发送请求到 DeepSeek API，问题: {}", userQuestion.substring(0, Math.min(50, userQuestion.length())));

        // 2. 构建 HTTP 请求
        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")  // 声明接受 SSE 流
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        // 3. 执行请求，获取流式响应
        try (Response response = httpClient.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "无错误详情";
                log.error("DeepSeek API 返回错误: {} - {}", response.code(), errorBody);
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("AI服务返回错误，状态码：" + response.code()));
                emitter.complete();
                return;
            }

            // 4. 逐行读取 SSE 响应流
            ResponseBody body = response.body();
            if (body == null) {
                emitter.send(SseEmitter.event().name("error").data("AI服务返回空响应"));
                emitter.complete();
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                String line;
                StringBuilder fullContent = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    // SSE 格式：每行以 "data: " 开头
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();

                        // DeepSeek 流结束标记
                        if ("[DONE]".equals(data)) {
                            log.debug("DeepSeek 流式响应结束，总内容长度: {}", fullContent.length());
                            break;
                        }

                        try {
                            // 解析 JSON，提取 content delta
                            @SuppressWarnings("unchecked")
                            Map<String, Object> chunk = objectMapper.readValue(data, Map.class);

                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");

                            if (choices != null && !choices.isEmpty()) {
                                Map<String, Object> choice = choices.get(0);
                                Map<String, Object> delta = (Map<String, Object>) choice.get("delta");

                                if (delta != null && delta.containsKey("content")) {
                                    String content = (String) delta.get("content");
                                    if (content != null && !content.isEmpty()) {
                                        fullContent.append(content);
                                        // 将增量内容通过 SSE 推送给前端
                                        emitter.send(SseEmitter.event()
                                                .name("message")
                                                .data(content));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // 某行解析失败不影响整体流程（可能是空行或格式异常）
                            log.trace("SSE行解析跳过: {}", data.substring(0, Math.min(50, data.length())));
                        }
                    }
                }

                // 5. 发送完成事件
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("complete"));
                emitter.complete();

                log.debug("对话完成，回复长度: {} 字符", fullContent.length());
            }
        }
    }

    /**
     * 构建 OpenAI 兼容的请求体
     *
     * 格式：
     * {
     *   "model": "deepseek-chat",
     *   "messages": [
     *     {"role": "system", "content": "系统提示词"},
     *     {"role": "user", "content": "用户问题"}
     *   ],
     *   "stream": true,
     *   "temperature": 0.7,
     *   "max_tokens": 1000
     * }
     */
    private Map<String, Object> buildRequestBody(String userQuestion) {
        Map<String, Object> body = new HashMap<>();

        // 模型名称
        body.put("model", config.getModel());

        // 消息列表
        List<Map<String, String>> messages = new ArrayList<>();

        // 系统消息：定义AI角色
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", config.getSystemPrompt());
        messages.add(systemMsg);

        // 用户消息：具体问题
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userQuestion);
        messages.add(userMsg);

        body.put("messages", messages);

        // 启用流式输出
        body.put("stream", true);

        // 参数
        body.put("temperature", config.getTemperature());
        body.put("max_tokens", config.getMaxTokens());

        return body;
    }
}
