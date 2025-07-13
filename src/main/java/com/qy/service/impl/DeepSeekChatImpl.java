package com.qy.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qy.enums.ChatModeType;
import com.qy.model.ChatMessageRequest;
import com.qy.model.ChatRequest;
import com.qy.service.IChatService;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service("deepSeekChatImpl")
@RequiredArgsConstructor
public class DeepSeekChatImpl implements IChatService {
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;  // DeepSeek API 密钥

    @Value("${spring.ai.openai.base-url}")
    private String apiUrl;  // DeepSeek API 地址

    private final ChatModel chatModel;

    private final OpenAiChatOptions chatOptions;

    private final ChatClient chatClient;

    private final ObjectMapper objectMapper;

    @Override
    public Flux<ChatResponse> streamMessage(Long sessionId, ChatMessageRequest request) {
        log.info("流式发送消息到会话: sessionId = {}, content = {}", sessionId, request.getContent());

        // 创建自定义选项
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(chatOptions.getModel())
                .temperature(request.getTemperature() != null ? request.getTemperature().doubleValue() : 0.7)
                .maxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : 2000)
                .build();

        // 创建消息列表
        List<Message> messages = new ArrayList<>();

        // 添加系统消息，定义 AI 助手的角色和行为
        messages.add(new SystemMessage("你是一个乐于助人的AI助手，能够以对话的方式回应用户。请提供详细且准确的信息。"));

        messages.add(new AssistantMessage(request.getContent()));

        // 创建提示并流式返回响应
        Prompt prompt = new Prompt(messages, options);

        // 获取AI响应流
        Flux<ChatResponse> responseFlux = chatModel.stream(prompt);

        // 创建一个StringBuilder来累积内容
        StringBuilder contentBuilder = new StringBuilder();

        // 返回处理后的响应流
        return responseFlux
                .doOnNext(response -> {
                    try {
                        if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
                            String content = response.getResults().get(0).getOutput().getText();
                            if (content != null) {
                                contentBuilder.append(content);
                                // 打印流式响应的内容
                                log.info("AI回复(流式): {}", content);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("从响应中获取内容时出错: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    // 当流完成时，保存完整的AI回复消息
                    String fullContent = contentBuilder.toString();
                    log.info("AI回复(完整): {}", fullContent);
                })
                .onErrorResume(e -> {
                    log.error("流式消息处理出错: {}", e.getMessage());

                    return Flux.empty();
                })
                // 确保响应被正确发送到前端
                .map(response -> {
                    if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
                        return response;
                    }
                    return null;
                })
                .filter(Objects::nonNull);
    }

    @Override
    public Flux<String> streamChat(Long sessionId, ChatRequest request) {
        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(apiUrl)
                .apiKey(apiKey)
                .modelName(chatOptions.getModel())
                .logRequests(true)
                .logResponses(true)
                .temperature(0.8)
                .build();

        return Flux.create(sink -> {
            try {
                model.chat(request.getMessages(), new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        // 使用正确的构建方式
                        sink.next(partialResponse);
                        log.info("收到消息片段: {}", partialResponse);
                    }

                    @Override
                    public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                        log.info("消息结束，完整消息ID: {}", completeResponse.id());
                        sink.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("流式请求失败:", error);
                        sink.error(error);
                    }
                });
            } catch (Exception e) {
                log.error("千问请求失败：{}", e.getMessage());
                sink.error(e);
            }
        });
    }

    @Override
    public SseEmitter chat(ChatRequest chatRequest, SseEmitter emitter) {
        StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .baseUrl(apiUrl)
                .apiKey(apiKey)
                .modelName(chatOptions.getModel())
                .logRequests(true)
                .logResponses(true)
                .temperature(0.8)
                .build();
        // 发送流式消息
        try {
            chatModel.chat(chatRequest.getMessages(), new StreamingChatResponseHandler() {
                @SneakyThrows
                @Override
                public void onPartialResponse(String partialResponse) {
                    emitter.send(partialResponse);
                    log.info("收到消息片段: {}", partialResponse);
                    System.out.print(partialResponse);
                }

                @Override
                public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                    try {
                        emitter.send(SseEmitter.event().name("end").data("DONE"));
                    } catch (IOException e) {
                        log.error("SSE发送失败: {}", e.getMessage());
                    }
                    emitter.complete();
                    log.info("消息结束，完整消息ID: {}", completeResponse);
                }

                @Override
                public void onError(Throwable error) {
                    System.err.println("错误: " + error.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("deepseek请求失败：{}", e.getMessage());
        }

        return emitter;
    }

    @Override
    public Flux<ServerSentEvent<String>> mcpChat(Long sessionId, String message) {
        StringBuilder contentBuilder = new StringBuilder();

        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent(message);
        request.setRole("user");

        Prompt prompt = promptBuilder(sessionId, request);

        return chatClient.prompt(prompt)
                .user(message)
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    try {
                        if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
                            String content = response.getResults().get(0).getOutput().getText();
                            if (content != null) {
                                contentBuilder.append(content);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Error getting content from response: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    // 当流完成时，打印完整的分析结果
                    String fullContent = contentBuilder.toString();
                    if (!fullContent.isEmpty()) {
                        log.info("Complete mcp chat result: {}", fullContent);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error in mcp chat: {}", e.getMessage());
                    return Flux.empty();
                })
                .map(chatResponse -> ServerSentEvent.<String>builder()
                        .data(toJson(chatResponse))
                        .event("message")
                        .build());
    }

    /**
     *  构建提示词,能查询数据库关联之前的对话记录
     *
     * @param sessionId 会话ID
     * @param request 请求
     * @return 提示词
     */
    private Prompt promptBuilder(Long sessionId, ChatMessageRequest request) {
        // 创建自定义选项
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(chatOptions.getModel())
                .temperature(request.getTemperature() != null ? request.getTemperature().doubleValue() : 0.7)
                .maxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : 2000)
                .build();

        // 创建消息列表
        List<Message> messages = new ArrayList<>();
        // 添加系统消息，定义 AI 助手的角色和行为
        messages.add(new SystemMessage("你是一个乐于助人的AI助手，能够以对话的方式回应用户。请提供详细且准确的信息。"));

        return new Prompt(messages, options);
    }

    /**
     * 将流式回答结果转json字符串
     *
     * @param chatResponse 流式回答结果
     * @return String json字符串
     */
    @SneakyThrows
    public String toJson(org.springframework.ai.chat.model.ChatResponse chatResponse) {
        return objectMapper.writeValueAsString(chatResponse);
    }

    @Override
    public String getCategory() {
        return ChatModeType.DEEPSEEK.getCode();
    }
}
