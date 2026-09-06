package com.qy.service.impl;

import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartInputAudio;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.qy.entity.AiChatMessage;
import com.qy.enums.ChatModeType;
import com.qy.model.ChatMessageRequest;
import com.qy.model.ChatRequest;
import com.qy.service.IAiChatMessageService;
import com.qy.service.IChatService;
import com.qy.session.SessionContext;
import com.qy.session.UserSession;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通义千问对话服务实现
 * 通过 DashScope 的 OpenAI 兼容接口，基于 Spring AI 2.0 的 ChatClient 进行流式对话
 */
@Slf4j
@Service("qianWenAiChatServiceImpl")
public class QianWenAiChatServiceImpl implements IChatService {

    /** 全局系统提示词 */
    @Value("${spring.ai.assistant.system-prompt}")
    private String systemPrompt;

    /** 多模态模型名称（如 qwen-vl-plus，仅用于图片/音频/视频场景） */
    @Value("${spring.ai.qianwen.chat.options.multimodal-model:qwen-vl-plus}")
    private String multimodalModel;

    /** 千问默认聊天选项（模型/温度/最大token数） */
    private final OpenAiChatOptions chatOptions;

    /** 千问 ChatClient */
    private final ChatClient chatClient;

    /** Jackson 3 ObjectMapper */
    private final ObjectMapper objectMapper;

    /** DashScope OpenAI 兼容客户端（音频场景直接构造请求，绕过 Spring AI 的 input_audio 纯 base64 转换） */
    private final OpenAIClient qianwenOpenAiClient;

    /** 对话记忆（音频场景手动维护，仅存文本，避免 media 被 Spring AI 再次转换） */
    private final ChatMemory chatMemory;

    private final IAiChatMessageService aiChatMessageService;

    /**
     * 构造器注入：通过 @Qualifier 明确指定千问专属的 Bean，
     * 避免与 DeepSeek 的同类型 Bean 产生歧义
     */
    public QianWenAiChatServiceImpl(
            @Qualifier("qianwenChatOptions") OpenAiChatOptions chatOptions,
            @Qualifier("qianwenChatClient") ChatClient chatClient,
            @Qualifier("qianwenOpenAiClient") OpenAIClient qianwenOpenAiClient,
            ObjectMapper objectMapper,
            ChatMemory chatMemory,
            IAiChatMessageService aiChatMessageService) {
        this.chatOptions = chatOptions;
        this.chatClient = chatClient;
        this.qianwenOpenAiClient = qianwenOpenAiClient;
        this.objectMapper = objectMapper;
        this.chatMemory = chatMemory;
        this.aiChatMessageService = aiChatMessageService;
    }

    /**
     * 构建自定义聊天选项，请求参数优先，缺省使用默认配置
     */
    private OpenAiChatOptions.Builder buildOptions(ChatMessageRequest request) {
        return OpenAiChatOptions.builder()
                .model(chatOptions.getModel())
                .temperature(request.getTemperature() != null ? request.getTemperature().doubleValue() : 0.7)
                .maxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : 2000);
    }

    /**
     * 构建多模态聊天选项，使用多模态专用模型
     */
    private OpenAiChatOptions.Builder buildMultimodalOptions() {
        return OpenAiChatOptions.builder()
                .model(multimodalModel)
                .temperature(chatOptions.getTemperature())
                .maxTokens(chatOptions.getMaxTokens());
    }

    /**
     * 保存 AI 回复消息
     */
    private void saveAssistantMessage(ChatMessageRequest request, String content, UserSession session) {
        if (content == null || content.isEmpty()) {
            return;
        }
        AiChatMessage aiChatMessage = new AiChatMessage();
        aiChatMessage.setSessionId(request.getSessionId());
        aiChatMessage.setRole("assistant");
        aiChatMessage.setContent(content);
        aiChatMessage.setModel(request.getModel());
        aiChatMessage.setUserId(session != null ? session.getUserId() : null);
        aiChatMessage.setCreateTime(LocalDateTime.now());
        aiChatMessageService.save(aiChatMessage);
    }

    @Override
    public Flux<ChatResponse> streamMessage(ChatMessageRequest request) {
        log.info("千问流式发送消息到会话: sessionId = {}, content = {}", request.getSessionId(), request.getContent());

        UserSession session = SessionContext.getSession();
        String conversationId = (session != null ? session.getUserId() : "anonymous") + "-" + request.getSessionId();

        // 累积完整回复内容
        StringBuilder contentBuilder = new StringBuilder();

        return chatClient.prompt()
                .system(systemPrompt)
                .user(request.getContent())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .options(buildOptions(request))
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    try {
                        String content = response.getResult() != null && response.getResult().getOutput() != null
                                ? response.getResult().getOutput().getText() : null;
                        if (content != null) {
                            contentBuilder.append(content);
                            log.info("AI回复(流式): {}", content);
                        }
                    } catch (Exception e) {
                        log.warn("从响应中获取内容时出错: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    String fullContent = contentBuilder.toString();
                    log.info("AI回复(完整): {}", fullContent);
                    saveAssistantMessage(request, fullContent, session);
                })
                .onErrorResume(e -> {
                    log.error("流式消息处理出错: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    @Override
    public Flux<ServerSentEvent<String>> streamChat(ChatRequest chatRequest) {
        log.info("千问流式发送消息到会话: sessionId = {}, content = {}", chatRequest.getSessionId(), chatRequest.getContent());

        UserSession session = SessionContext.getSession();
        String conversationId = (session != null ? session.getUserId() : "anonymous") + "-" + chatRequest.getSessionId();

        // 累积完整回复内容
        StringBuilder contentBuilder = new StringBuilder();

        return chatClient.prompt()
                .system(systemPrompt)
                .user(chatRequest.getContent())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .doOnNext(contentBuilder::append)
                .doOnComplete(() -> {
                    String fullContent = contentBuilder.toString();
                    log.info("AI回复(完整): {}", fullContent);
                    ChatMessageRequest request = new ChatMessageRequest();
                    request.setSessionId(chatRequest.getSessionId());
                    request.setModel(chatRequest.getModel());
                    saveAssistantMessage(request, fullContent, session);
                })
                .onErrorResume(e -> {
                    log.error("流式消息处理出错: {}", e.getMessage());
                    return Flux.just("抱歉，AI服务暂时不可用，请稍后重试。");
                })
                .map(content -> ServerSentEvent.<String>builder()
                        .data(content)
                        .build());
    }

    @Override
    public Flux<ServerSentEvent<String>> streamMultiModalChat(ChatRequest chatRequest) {
        log.info("千问多模态流式发送消息: sessionId = {}, content = {}, files = {}",
                chatRequest.getSessionId(), chatRequest.getContent(),
                chatRequest.getFiles() != null ? chatRequest.getFiles().size() : 0);

        UserSession session = SessionContext.getSession();
        String conversationId = (session != null ? session.getUserId() : "anonymous") + "-" + chatRequest.getSessionId();

        // 音频文件无法走 Spring AI 的 media 转换（OpenAiChatModel 只向 input_audio.data
        // 填纯 base64，DashScope 要求 URL/data URL，会报 400），改用 SDK 直接构造请求
        if (containsAudio(chatRequest.getFiles())) {
            return streamAudioChat(chatRequest, session, conversationId);
        }

        // 将 MultipartFile 转换为 Media 数组（仅图片/视频等 Spring AI 可处理的类型）
        Media[] mediaArray = convertFilesToMedia(chatRequest.getFiles());

        StringBuilder contentBuilder = new StringBuilder();

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userSpec -> userSpec
                        .text(chatRequest.getContent() != null ? chatRequest.getContent() : "")
                        .media(mediaArray))
                .options(buildMultimodalOptions())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .doOnNext(contentBuilder::append)
                .doOnComplete(() -> {
                    String fullContent = contentBuilder.toString();
                    log.info("AI多模态回复(完整): {}", fullContent);
                    ChatMessageRequest request = new ChatMessageRequest();
                    request.setSessionId(chatRequest.getSessionId());
                    request.setModel(chatRequest.getModel());
                    saveAssistantMessage(request, fullContent, session);
                })
                .onErrorResume(e -> {
                    log.error("多模态流式消息处理出错: {}", e.getMessage());
                    return Flux.just("抱歉，AI服务暂时不可用，请稍后重试。");
                })
                .map(content -> ServerSentEvent.<String>builder()
                        .data(content)
                        .build());
    }

    /**
     * 音频多模态流式对话
     * <p>
     * DashScope 的 qwen 多模态模型在 OpenAI 兼容模式下，input_audio.data 仅接受
     * 音频 URL 或 data URL（data:audio/xxx;base64,...），而 Spring AI 2.0 的
     * OpenAiChatModel 只会填纯 base64 字符串，导致 400 报错（URL 无效）。
     * 因此音频场景改用 openai-java SDK 直接构造请求。
     */
    private Flux<ServerSentEvent<String>> streamAudioChat(ChatRequest chatRequest, UserSession session, String conversationId) {
        try {
            ChatCompletionCreateParams params = buildAudioChatParams(chatRequest, conversationId);
            StringBuilder contentBuilder = new StringBuilder();

            return Flux.using(
                            () -> qianwenOpenAiClient.chat().completions().createStreaming(params),
                            streamResponse -> Flux.fromStream(streamResponse::stream),
                            StreamResponse::close)
                    .mapNotNull(chunk -> chunk.choices().stream()
                            .map(choice -> choice.delta().content().orElse(""))
                            .collect(Collectors.joining()))
                    .doOnNext(contentBuilder::append)
                    .filter(text -> !text.isEmpty())
                    .doOnComplete(() -> {
                        String fullContent = contentBuilder.toString();
                        log.info("AI音频多模态回复(完整): {}", fullContent);
                        ChatMessageRequest request = new ChatMessageRequest();
                        request.setSessionId(chatRequest.getSessionId());
                        request.setModel(chatRequest.getModel());
                        saveAssistantMessage(request, fullContent, session);
                        // 对话记忆仅存文本，避免 media 被 Spring AI 重新转换（audio 会再次触发纯 base64 问题）
                        chatMemory.add(conversationId, List.of(
                                new UserMessage(chatRequest.getContent() != null ? chatRequest.getContent() : ""),
                                new AssistantMessage(fullContent)));
                    })
                    .onErrorResume(e -> {
                        log.error("音频多模态流式消息处理出错: {}", e.getMessage(), e);
                        return Flux.just("抱歉，AI服务暂时不可用，请稍后重试。");
                    })
                    .map(content -> ServerSentEvent.<String>builder()
                            .data(content)
                            .build());
        } catch (Exception e) {
            log.error("音频多模态请求构建失败: {}", e.getMessage(), e);
            return Flux.just(ServerSentEvent.<String>builder()
                    .data("抱歉，AI服务暂时不可用，请稍后重试。")
                    .build());
        }
    }

    /**
     * 构建音频场景的聊天请求参数：系统提示词 + 历史对话（仅文本）+ 当前用户消息（文本 + content parts）
     */
    private ChatCompletionCreateParams buildAudioChatParams(ChatRequest chatRequest, String conversationId) throws Exception {
        ChatCompletionCreateParams.Builder paramsBuilder = ChatCompletionCreateParams.builder()
                .model(multimodalModel)
                .temperature(chatOptions.getTemperature())
                .maxTokens(chatOptions.getMaxTokens())
                .addSystemMessage(systemPrompt);

        // 注入历史对话（仅文本，避免 media 被 Spring AI 再次转换）
        for (Message message : chatMemory.get(conversationId)) {
            if (message.getMessageType() == MessageType.USER) {
                paramsBuilder.addUserMessage(message.getText());
            } else if (message.getMessageType() == MessageType.ASSISTANT) {
                paramsBuilder.addAssistantMessage(message.getText());
            }
        }

        // 当前用户消息：文本 + 音频/图片 content parts
        paramsBuilder.addUserMessageOfArrayOfContentParts(buildContentParts(chatRequest));
        return paramsBuilder.build();
    }

    /**
     * 将文本与附件文件组装为 openai-java SDK 的 content parts
     * <p>
     * 音频使用 data URL（DashScope 要求 input_audio.data 为 URL 形式）；
     * 图片使用 data URL 的 image_url；视频等其他类型降级为 data URL 文本
     * （与 Spring AI 对 video 的降级处理一致）。
     */
    private List<ChatCompletionContentPart> buildContentParts(ChatRequest chatRequest) throws Exception {
        List<ChatCompletionContentPart> parts = new ArrayList<>();
        parts.add(ChatCompletionContentPart.ofText(
                ChatCompletionContentPartText.builder()
                        .text(chatRequest.getContent() != null ? chatRequest.getContent() : "")
                        .build()));

        if (chatRequest.getFiles() == null) {
            return parts;
        }

        for (MultipartFile file : chatRequest.getFiles()) {
            String contentType = file.getContentType();
            byte[] data = file.getBytes();
            String dataUrl = "data:" + (contentType != null ? contentType : "application/octet-stream")
                    + ";base64," + Base64.getEncoder().encodeToString(data);

            if (contentType != null && contentType.startsWith("audio/")) {
                ChatCompletionContentPartInputAudio.InputAudio inputAudio =
                        ChatCompletionContentPartInputAudio.InputAudio.builder()
                                .data(dataUrl)
                                .format(parseAudioFormat(contentType))
                                .build();
                parts.add(ChatCompletionContentPart.ofInputAudio(
                        ChatCompletionContentPartInputAudio.builder()
                                .inputAudio(inputAudio)
                                .build()));
            } else if (contentType != null && contentType.startsWith("image/")) {
                parts.add(ChatCompletionContentPart.ofImageUrl(
                        ChatCompletionContentPartImage.builder()
                                .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
                                        .url(dataUrl)
                                        .build())
                                .build()));
            } else {
                // 视频等类型降级为 data URL 文本
                parts.add(ChatCompletionContentPart.ofText(
                        ChatCompletionContentPartText.builder()
                                .text(dataUrl)
                                .build()));
            }
        }
        return parts;
    }

    /** 判断附件中是否包含音频文件 */
    private boolean containsAudio(List<MultipartFile> files) {
        return files != null && files.stream()
                .anyMatch(file -> file.getContentType() != null && file.getContentType().startsWith("audio/"));
    }

    /** 根据音频 MIME 类型解析 SDK 支持的音频格式（仅支持 MP3/WAV） */
    private ChatCompletionContentPartInputAudio.InputAudio.Format parseAudioFormat(String contentType) {
        return (contentType.contains("mp3") || contentType.contains("mpeg"))
                ? ChatCompletionContentPartInputAudio.InputAudio.Format.MP3
                : ChatCompletionContentPartInputAudio.InputAudio.Format.WAV;
    }

    /**
     * 将 MultipartFile 列表转换为 Spring AI Media 数组，
     * 支持图片（image/*）、视频（video/*）等媒体类型（音频走 streamAudioChat 独立路径）
     */
    private Media[] convertFilesToMedia(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return new Media[0];
        }
        return files.stream()
                .map(file -> {
                    try {
                        String contentType = file.getContentType();
                        MimeType mimeType = (contentType != null)
                                ? MimeType.valueOf(contentType)
                                : MimeType.valueOf("application/octet-stream");
                        return Media.builder()
                                .mimeType(mimeType)
                                .data(file.getBytes())
                                .name(file.getOriginalFilename())
                                .build();
                    } catch (Exception e) {
                        log.error("文件转换失败: {}", file.getOriginalFilename(), e);
                        throw new RuntimeException("文件转换失败: " + file.getOriginalFilename(), e);
                    }
                })
                .toArray(Media[]::new);
    }

    @Override
    public SseEmitter chat(ChatRequest chatRequest, SseEmitter emitter) {
        log.info("千问SSE发送消息到会话: sessionId = {}, content = {}", chatRequest.getSessionId(), chatRequest.getContent());

        UserSession session = SessionContext.getSession();
        String conversationId = (session != null ? session.getUserId() : "anonymous") + "-" + chatRequest.getSessionId();

        StringBuilder contentBuilder = new StringBuilder();

        chatClient.prompt()
                .system(systemPrompt)
                .user(chatRequest.getContent())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .doOnNext(content -> {
                    try {
                        contentBuilder.append(content);
                        emitter.send(SseEmitter.event().name("message").data(content, MediaType.TEXT_PLAIN));
                    } catch (Exception e) {
                        log.warn("SSE发送内容时出错: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    try {
                        ChatMessageRequest request = new ChatMessageRequest();
                        request.setSessionId(chatRequest.getSessionId());
                        request.setModel(chatRequest.getModel());
                        saveAssistantMessage(request, contentBuilder.toString(), session);
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("SSE完成事件处理出错: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(e -> {
                    log.error("SSE对话出错: {}", e.getMessage());
                    emitter.completeWithError(e);
                })
                .subscribe();

        return emitter;
    }

    @Override
    public Flux<ServerSentEvent<String>> mcpChat(ChatMessageRequest request) {
        UserSession session = SessionContext.getSession();
        String conversationId = (session != null ? session.getUserId() : "anonymous") + "-" + request.getSessionId();
        StringBuilder contentBuilder = new StringBuilder();

        return chatClient.prompt()
                .system(systemPrompt)
                .user(request.getContent())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .options(buildOptions(request))
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    try {
                        String content = response.getResult() != null && response.getResult().getOutput() != null
                                ? response.getResult().getOutput().getText() : null;
                        if (content != null) {
                            contentBuilder.append(content);
                        }
                    } catch (Exception e) {
                        log.warn("Error getting content from response: {}", e.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    // 当流完成时，保存完整的对话结果
                    String fullContent = contentBuilder.toString();
                    if (!fullContent.isEmpty()) {
                        log.info("Complete mcp chat result: {}", fullContent);
                        saveAssistantMessage(request, fullContent, session);
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
     * 将流式回答结果转json字符串
     *
     * @param chatResponse 流式回答结果
     * @return String json字符串
     */
    @SneakyThrows
    public String toJson(ChatResponse chatResponse) {
        return objectMapper.writeValueAsString(chatResponse);
    }

    @Override
    public String getCategory() {
        return ChatModeType.QIANWEN.getCode();
    }
}
