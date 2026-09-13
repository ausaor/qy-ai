package com.qy.config;

import com.qy.agent.AgentRegistry;
import com.qy.agent.AgentRouterTool;
import com.qy.agent.AgentType;
import com.qy.contant.PromptConstant;
import com.qy.tools.CommonTools;
import com.qy.tools.EmailTools;
import com.qy.tools.TextToSqlTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ClassPathResource;

/**
 * 多 Agent 系统配置
 * 定义 Router Agent 和 5 个专业 Agent 的 ChatClient bean
 */
@Configuration
public class MultiAgentConfiguration {

    @Bean
    public AgentRegistry agentRegistry() {
        return new AgentRegistry();
    }

    /**
     * 邮件发送技能集合（Agent Skills 技术，基于 Claude Code Agent Skills 规范）：
     * 加载 classpath 下 skills/ 中 send-greeting-email 与 send-system-notification 两个技能的 SKILL.md，
     * 供 send_email Agent 通过 Skill 工具按需加载执行。
     * 注意：SkillsTool 的 ToolCallback 工具名固定为 "Skill"，多个 SkillsTool 同时挂载会因重名触发
     * "Multiple tools with the same name (Skill) found in ToolCallingChatOptions" 报错，
     * 因此必须把多个技能资源合并到同一个 SkillsTool 中（其内部按 command 参数分发到对应技能）。
     */
    @Bean
    public ToolCallback mailSkillsTool() {
        return SkillsTool.builder()
                .addSkillsResource(new ClassPathResource("skills/send-greeting-email/"))
                .addSkillsResource(new ClassPathResource("skills/send-system-notification/"))
                .build();
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
            CommonTools commonTools,
            AgentRegistry registry) {
        ChatClient client = ChatClient.builder(model)
                .defaultSystem("""
                        你是一个博古通今的智能助手，可以回答各种通用问题。
                        当用户询问天气时，必须调用 getCityWeather 工具获取实时天气数据后再回答：
                        - 每次用户询问天气都必须重新调用 getCityWeather 工具，即使对话历史中已有天气信息，
                          也严禁直接引用历史对话中的天气数据回答；
                        - 若用户未指明城市或日期，结合上下文推断；无法确定时先向用户确认；
                        - 获取工具返回结果后，直接基于结果回答用户，不要重复调用工具。
                        """)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor()
                )
                .defaultTools(commonTools)
                .build();
        registry.register(AgentType.GENERAL, client, "通用对话和问答");
        return client;
    }

    /**
     * 邮件发送 Agent — 向系统用户发送问候邮件与系统通知邮件
     * 挂载 SkillsTool（send-greeting-email / send-system-notification 技能）与 EmailTools（收件人校验 + 发送）
     */
    @Bean
    public ChatClient sendEmailAgent(
            @Qualifier("qianwenChatModel") OpenAiChatModel model, ChatMemory chatMemory,
            EmailTools emailTools,
            ToolCallback mailSkillsTool,
            AgentRegistry registry) {
        ChatClient client = ChatClient.builder(model)
                .defaultSystem(PromptConstant.SEND_EMAIL_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SimpleLoggerAdvisor()
                )
                .defaultTools(emailTools, mailSkillsTool)
                .build();
        registry.register(AgentType.SEND_EMAIL, client, "发送邮件（问候邮件、系统通知邮件）");
        return client;
    }

    /**
     * Router Agent — 意图分发入口
     * DependsOn 确保所有子 Agent 先注册到 Registry，再构建路由提示词
     * 注意：Router 不挂载 MessageChatMemoryAdvisor。路由决策只依赖当前查询，
     * 若注入历史对话，模型会模仿历史中"直接回答"的模式跳过 routeToAgent 工具，
     * 自行编造答案；多轮指代由子 Agent（如 general）的记忆负责补全。
     */
    @Bean
    @DependsOn({"generalAgent", "textToSqlAgent", "documentQaAgent", "sendEmailAgent"})
    public ChatClient routerAgent(
            @Qualifier("qianwenChatModel") OpenAiChatModel model,
            AgentRouterTool routerTool, AgentRegistry registry) {
        ChatClient client = ChatClient.builder(model)
                .defaultSystem(buildRouterSystemPrompt(registry))
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultTools(routerTool)
                .build();
        registry.register(AgentType.ROUTER, client, "意图分类和路由分发");
        return client;
    }

    private String buildRouterSystemPrompt(AgentRegistry registry) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个智能路由系统。收到用户提问后，必须先调用 routeToAgent 工具将请求分发到合适的专业Agent，\n");
        sb.append("然后把工具返回的内容直接作为最终回答输出。\n\n");
        sb.append("必须遵守：\n");
        sb.append("- 你唯一可以调用的工具是 routeToAgent；\n");
        sb.append("- 收到用户提问时必须调用 routeToAgent 工具分发，严禁未经分发自行编造答案；\n");
        sb.append("- 工具返回结果后立即停止，直接把返回内容原样输出，严禁再次调用 routeToAgent 工具。\n\n");
        sb.append("可用的 Agent（均为 routeToAgent 工具 agentName 参数的取值，不是工具名称）:\n");
        for (var entry : registry.getAllDescriptions().entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("""
                \n路由规则:
                - 用户要求发送问候邮件（向某人/指定邮箱发送问候、祝福等） → send_email
                - 用户要求发送系统通知邮件（系统上线、功能发布、系统维护、系统公告等） → send_email
                - 用户查询系统数据 → text_to_sql
                - 用户询问已上传的文档、文件内容 → document_qa
                - 其他所有问题 → general
                
                重要：text_to_sql、document_qa、send_email、general 只是 Agent 名称（即 agentName 参数的取值），
                不是工具名称，严禁把它们当作工具直接调用；必须通过 routeToAgent 工具并传入 agentName 参数完成分发。
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
