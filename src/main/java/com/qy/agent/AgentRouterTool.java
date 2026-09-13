package com.qy.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.qy.tools.TextToSqlTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 路由工具
 * Router Agent 通过此工具将用户请求分发到专业 Agent
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AgentRouterTool {

    /**
     * ```sql 代码块提取：兜底执行时从回答中取回模型生成的 SQL
     */
    private static final Pattern SQL_BLOCK = Pattern.compile("```sql\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /**
     * 只读 SQL 起始关键字，与 TextToSqlTools 的校验规则保持一致
     */
    private static final Pattern READ_ONLY_START = Pattern.compile("^(select|with)\\b", Pattern.CASE_INSENSITIVE);

    private final AgentRegistry registry;

    private final TextToSqlTools textToSqlTools;

    /**
     * 每个会话最近一次路由返回的内容，用于检测"模型把工具返回结果再次作为查询路由"的循环调用。
     * 正常新查询不会以上一轮完整回答文本开头，跨请求误伤概率极低
     */
    private final Map<String, String> lastRouteResultByConversation = new ConcurrentHashMap<>();

    @Tool(description = "将用户请求路由到指定的专业Agent。" +
            "可选的 agentName 值: text_to_sql(自然语言转sql), " +
            "send_email(发送邮件), " +
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

            // 记录本轮路由前 executeQuerySql 的执行次数，用于判断子 Agent 是否真正执行了查询
            int executedBefore = TextToSqlTools.currentThreadExecuteCount();
            String response = spec.call().content();

            // 兜底执行：text_to_sql Agent 可能跳过 executeQuerySql 直接输出 SQL 文本，
            // 或直接编造数据（编造表格/SQL 块），此时强制补齐"真实执行查询"环节
            if (type == AgentType.TEXT_TO_SQL) {
                boolean queryExecuted = TextToSqlTools.currentThreadExecuteCount() > executedBefore;
                response = ensureSqlExecuted(query, response, agent, conversationId, queryExecuted);
            }

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

    /**
     * text_to_sql Agent 跳步兜底，覆盖两种模型违规场景：
     * 1. 只输出 SQL 文本未执行 → 自动执行该 SQL，并把真实结果回传给子 Agent 重新组织回答；
     * 2. 本轮未调用 executeQuerySql 且响应不属于"无法处理"类说明（编造表格、编造 SQL 块等）
     *    → 强制子 Agent 重新执行查询后再回答。
     * 是否执行过查询由 executeQuerySql 的线程内执行计数判定，不依赖响应文本特征。
     * 兜底只做一次，避免与模型反复拉扯形成无限循环。
     */
    private String ensureSqlExecuted(String originalQuery, String response, ChatClient agent,
                                     String conversationId, boolean queryExecuted) {
        // 本轮已真实执行过查询（无论成败），相信子 Agent 的后续回答
        if (queryExecuted) {
            return response;
        }

        // 场景1：回答只有 SQL 文本没有结果 → 代为执行
        String sql = extractUnexecutedSql(response);
        if (sql != null) {
            log.warn("text_to_sql 响应疑似未执行 SQL，触发兜底执行: {}", sql);
            Map<String, Object> execResult = textToSqlTools.executeQuerySql(sql);
            if (execResult.containsKey("error")) {
                log.warn("兜底执行 SQL 失败: {}", execResult.get("error"));
                return response;
            }
            return followUpCall(agent, conversationId,
                    "你上一步只输出了 SQL 而没有调用 executeQuerySql 执行查询。"
                            + "以下是该 SQL 的真实执行结果: " + execResult
                            + "\n请直接基于以上结果，按照回答格式用中文向用户解释查询结果并给出数据结论，"
                            + "不要再次调用任何工具，也不要只输出 SQL。");
        }

        // 场景2：本轮未执行查询，且响应不是"无法处理"类说明 → 判定为编造数据，强制重新执行
        if (!isUnanswerableReply(response)) {
            log.warn("text_to_sql 本轮未执行查询，响应疑似编造数据，触发强制重试");
            return followUpCall(agent, conversationId,
                    "你上一次回答没有调用 executeQuerySql 执行查询，内容属于编造数据。"
                            + "用户的问题是: " + originalQuery
                            + "\n请重新回答：必须先调用 executeQuerySql（可先用 explainQuerySql 校验）执行查询，"
                            + "拿到真实数据后才能回答，严禁编造或猜测任何数据。");
        }
        return response;
    }

    /**
     * 判断响应是否为"无法处理的情况"类说明（追问、拒绝、无相关表等），
     * 这类回答天然没有 SQL 执行痕迹，不应触发编造兜底
     */
    private boolean isUnanswerableReply(String response) {
        return response.contains("不属于数据查询范围")
                || response.contains("缺少必要的查询条件")
                || response.contains("请提供")
                || response.contains("数据库中没有相关表")
                || response.contains("不包含该信息");
    }

    /**
     * 向子 Agent 追加一次追问调用，沿用同一会话记忆
     */
    private String followUpCall(ChatClient agent, String conversationId, String message) {
        ChatClient.ChatClientRequestSpec spec = agent.prompt().user(message);
        if (conversationId != null) {
            spec.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
        }
        return spec.call().content();
    }

    /**
     * 从响应中提取"疑似未执行"的 SQL。
     * 返回 null 表示响应是正常回答（含表格结果或非 SQL 内容），无需兜底。
     */
    private String extractUnexecutedSql(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        Matcher matcher = SQL_BLOCK.matcher(response);
        if (matcher.find()) {
            String sql = matcher.group(1).strip();
            if (!READ_ONLY_START.matcher(sql).find()) {
                return null;
            }
            // 代码块之外若几乎没有正文且不含 Markdown 表格（|），说明回答只有 SQL 没有结果
            String rest = response.replace(matcher.group(), " ").strip();
            if (!rest.contains("|") && rest.length() <= 40) {
                return sql;
            }
            return null;
        }
        // 整个响应就是一行 SQL 文本
        String stripped = response.strip();
        if (READ_ONLY_START.matcher(stripped).find()
                && !stripped.contains("\n") && !stripped.contains("|")) {
            return stripped;
        }
        return null;
    }

    /**
     * 兜底别名工具：模型偶尔会把系统提示词中的 Agent 名称（如 text_to_sql）直接当作工具名调用，
     * 而 Router 上并未注册这些名称，导致 "No ToolCallback found for tool name: xxx" 报错。
     * 这里注册与各 Agent 同名的兼容工具并统一转发到 routeToAgent，保证即使模型误调用也能正确路由。
     */
    @Tool(name = "text_to_sql", description = "将用户查询交给自然语言转SQL Agent 处理，等价于 routeToAgent(agentName='text_to_sql')")
    public String routeToTextToSql(
            @ToolParam(description = "用户的查询内容") String query,
            ToolContext toolContext) {
        return routeToAgent(AgentType.TEXT_TO_SQL.getId(), query, toolContext);
    }

    @Tool(name = "document_qa", description = "将用户查询交给文档问答 Agent 处理，等价于 routeToAgent(agentName='document_qa')")
    public String routeToDocumentQa(
            @ToolParam(description = "用户的查询内容") String query,
            ToolContext toolContext) {
        return routeToAgent(AgentType.DOCUMENT_QA.getId(), query, toolContext);
    }

    @Tool(name = "send_email", description = "将用户查询交给邮件发送 Agent 处理，等价于 routeToAgent(agentName='send_email')")
    public String routeToSendEmail(
            @ToolParam(description = "用户的查询内容") String query,
            ToolContext toolContext) {
        return routeToAgent(AgentType.SEND_EMAIL.getId(), query, toolContext);
    }

    @Tool(name = "general", description = "将用户查询交给通用对话 Agent 处理，等价于 routeToAgent(agentName='general')")
    public String routeToGeneral(
            @ToolParam(description = "用户的查询内容") String query,
            ToolContext toolContext) {
        return routeToAgent(AgentType.GENERAL.getId(), query, toolContext);
    }
}
