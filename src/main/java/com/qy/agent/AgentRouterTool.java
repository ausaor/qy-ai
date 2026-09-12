package com.qy.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 路由工具
 * Router Agent 通过此工具将用户请求分发到专业 Agent
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AgentRouterTool {

    private final AgentRegistry registry;

    /**
     * 每个会话最近一次路由返回的内容，用于检测"模型把工具返回结果再次作为查询路由"的循环调用。
     * 正常新查询不会以上一轮完整回答文本开头，跨请求误伤概率极低
     */
    private final Map<String, String> lastRouteResultByConversation = new ConcurrentHashMap<>();

    @Tool(description = "将用户请求路由到指定的专业Agent。" +
            "可选的 agentName 值: text_to_sql(自然语言转sql), " +
            "document_qa(文档问答), general(通用对话)")
    public String routeToAgent(
            @ToolParam(description = "目标Agent名称") String agentName,
            @ToolParam(description = "用户的原始或优化后的查询") String query,
            ToolContext toolContext) {

        log.info("路由到 Agent: {}, 查询: {}", agentName,
                query.length() > 50 ? query.substring(0, 50) + "..." : query);

        // 从 ToolContext 中获取外层会话 ID，继续传递给子 Agent，
        // 避免 MessageChatMemoryAdvisor 因 conversationId 为 null 而报错
        Object conversationIdObj = toolContext.getContext().get("conversationId");
        String conversationId = conversationIdObj != null ? conversationIdObj.toString() : null;
        log.info("外层会话 ID: {}", conversationId);

        // 循环保护：模型若把上一次路由返回的内容再次作为查询路由，
        // 直接原样返回终止循环，避免"路由→工具→再路由"无限循环
        if (conversationId != null) {
            String lastResult = lastRouteResultByConversation.get(conversationId);
            if (lastResult != null && (query.equals(lastResult)
                    || (query.length() > lastResult.length() && query.startsWith(lastResult)))) {
                log.warn("检测到路由循环调用, conversationId = {}, 终止循环并直接返回查询内容", conversationId);
                return query;
            }
        }

        try {
            AgentType type = AgentType.fromId(agentName);
            ChatClient agent = registry.getAgent(type);

            ChatClient.ChatClientRequestSpec spec = agent.prompt().user(query);
            if (conversationId != null) {
                spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
            }

            String response = spec.call().content();

            if (conversationId != null) {
                lastRouteResultByConversation.put(conversationId, response);
            }

            log.info("Agent {} 响应完成，长度: {} 字符", agentName, response.length());
            return response;
        } catch (Exception e) {
            log.error("Agent {} 调用失败: {}", agentName, e.getMessage());
            return "抱歉，处理您的请求时出现了问题，请稍后再试。";
        }
    }
}
