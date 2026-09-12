package com.qy.config;

import com.qy.agent.AgentRegistry;
import com.qy.agent.AgentRouterTool;
import com.qy.agent.AgentType;
import com.qy.contant.PromptConstant;
import com.qy.tools.TextToSqlTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * 多 Agent 系统配置
 * 定义 Router Agent 和 4 个专业 Agent 的 ChatClient bean
 */
@Configuration
public class MultiAgentConfiguration {

    @Bean
    public AgentRegistry agentRegistry() {
        return new AgentRegistry();
    }

    /**
     * 文档问答 Agent — 基于对话上下文的文档问答
     * 注意：RAG 管线（RetrievalAugmentationAdvisor）需在配置 VectorStore 后再启用
     * 使用 RetrievalAugmentationAdvisor 替代 QuestionAnswerAdvisor
     */
    @Bean
    public ChatClient documentQaAgent(
            @Qualifier("qianwenChatModel") OpenAiChatModel model,
            RetrievalAugmentationAdvisor advancedRagAdvisor,
            ChatMemory chatMemory,
            AgentRegistry registry) {
        ChatClient client = ChatClient.builder(model)
                .defaultSystem("""
                        你是文档问答助手。请根据提供的上下文信息回答用户问题。
                        回答时必须使用 [N] 标注引用来源，例如 [1]、[2]。
                        如果上下文中没有相关信息，请明确告知用户无法回答。
                        """)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        advancedRagAdvisor,
                        new SimpleLoggerAdvisor()
                )
                .build();
        registry.register(AgentType.DOCUMENT_QA, client, "文档问答（支持 RAG）");
        return client;
    }

    /**
     * 通用对话 Agent
     */
    @Bean
    public ChatClient generalAgent(
            @Qualifier("qianwenChatModel") OpenAiChatModel model, ChatMemory chatMemory,
            AgentRegistry registry) {
        ChatClient client = ChatClient.builder(model)
                .defaultSystem("你是一个博古通今的智能助手，可以回答各种通用问题。")
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor()
                )
                .build();
        registry.register(AgentType.GENERAL, client, "通用对话和问答");
        return client;
    }

    /**
     * Router Agent — 意图分发入口
     * DependsOn 确保所有子 Agent 先注册到 Registry，再构建路由提示词
     */
    @Bean
    @DependsOn({"generalAgent", "textToSqlAgent", "documentQaAgent"})
    public ChatClient routerAgent(
            @Qualifier("qianwenChatModel") OpenAiChatModel model, ChatMemory chatMemory,
            AgentRouterTool routerTool, AgentRegistry registry) {
        ChatClient client = ChatClient.builder(model)
                .defaultSystem(buildRouterSystemPrompt(registry))
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor()
                )
                .defaultTools(routerTool)
                .build();
        registry.register(AgentType.ROUTER, client, "意图分类和路由分发");
        return client;
    }

    private String buildRouterSystemPrompt(AgentRegistry registry) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个智能路由系统。根据用户意图调用 routeToAgent 工具将请求分发到合适的专业Agent。\n\n");
        sb.append("可用的 Agent:\n");
        for (var entry : registry.getAllDescriptions().entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("""
                \n路由规则:
                - 用户查询系统数据 → text_to_sql
                - 用户询问已上传的文档、文件内容 → document_qa
                - 其他所有问题 → general
                """);
        return sb.toString();
    }

    /**
     * 自然语言转Sql Agent
     */
    @Bean
    public ChatClient textToSqlAgent(
            @Qualifier("qianwenChatModel") OpenAiChatModel model, ChatMemory chatMemory,
            TextToSqlTools textToSqlTools,
            AgentRegistry registry) {
        ChatClient client = ChatClient.builder(model)
                .defaultSystem(PromptConstant.TEXT_TO_SQL_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor()
                )
                .defaultTools(textToSqlTools)
                .build();
        registry.register(AgentType.TEXT_TO_SQL, client, "自然语言转sql");
        return client;
    }
}
