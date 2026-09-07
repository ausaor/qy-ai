package com.qy.agent;

import java.util.Arrays;

/**
 * Agent 类型枚举
 */
public enum AgentType {

    ROUTER("router", "意图分类和路由分发"),
    TEXT_TO_SQL("text_to_sql", "自然语言转sql"),
    DOCUMENT_QA("document_qa", "文档问答（支持 RAG）"),
    GENERAL("general", "通用对话");

    private final String id;

    private final String description;

    AgentType(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public static AgentType fromId(String id) {
        return Arrays.stream(values())
                .filter(type -> type.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的 Agent 类型: " + id));
    }
}
