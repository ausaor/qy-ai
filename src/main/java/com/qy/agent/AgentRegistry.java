package com.qy.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 注册中心
 * 管理所有 Agent 的 ChatClient 实例和元数据
 */
@Component
public class AgentRegistry {

    private final Map<AgentType, AgentEntry> agents = new LinkedHashMap<>();

    public void register(AgentType type, ChatClient client, String description) {
        agents.put(type, new AgentEntry(client, description));
    }

    public ChatClient getAgent(AgentType type) {
        AgentEntry entry = agents.get(type);
        if (entry == null) {
            throw new IllegalArgumentException("Agent 未注册: " + type.getId());
        }
        return entry.client;
    }

    public Map<String, String> getAllDescriptions() {
        Map<String, String> result = new LinkedHashMap<>();
        agents.forEach((type, entry) -> result.put(type.getId(), entry.description));
        return result;
    }

    public boolean isRegistered(AgentType type) {
        return agents.containsKey(type);
    }

    private record AgentEntry(ChatClient client, String description) {
    }
}
