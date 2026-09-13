package com.qy.skills;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SkillsTool 加载验证
 * 确认 resources/skills/send-greeting-email/SKILL.md 能被正确发现并注册为 Skill 工具
 */
@DisplayName("问候邮件技能加载")
class SkillsToolLoadTest {

    @Test
    @DisplayName("SkillsTool 应从 classpath 加载问候邮件技能")
    void skillsToolShouldLoadGreetingEmailSkill() {
        ToolCallback callback = SkillsTool.builder()
                .addSkillsResource(new ClassPathResource("skills/send-greeting-email/"))
                .build();

        assertEquals("Skill", callback.getToolDefinition().name(), "工具名应为 Skill");
        String description = callback.getToolDefinition().description();
        assertTrue(description.contains("send-greeting-email"),
                "工具描述应包含技能名 send-greeting-email，实际: " + description);
    }
}
