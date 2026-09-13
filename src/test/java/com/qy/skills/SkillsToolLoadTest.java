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
 * 确认 resources/skills 下的 SKILL.md（问候邮件、系统通知）能被正确发现并注册为 Skill 工具
 */
@DisplayName("邮件技能加载")
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

    @Test
    @DisplayName("SkillsTool 应从 classpath 加载系统通知邮件技能")
    void skillsToolShouldLoadSystemNotificationSkill() {
        ToolCallback callback = SkillsTool.builder()
                .addSkillsResource(new ClassPathResource("skills/send-system-notification/"))
                .build();

        assertEquals("Skill", callback.getToolDefinition().name(), "工具名应为 Skill");
        String description = callback.getToolDefinition().description();
        assertTrue(description.contains("send-system-notification"),
                "工具描述应包含技能名 send-system-notification，实际: " + description);
    }

    @Test
    @DisplayName("单个 SkillsTool 合并加载两个邮件技能，工具名唯一且描述包含全部技能")
    void skillsToolShouldLoadBothMailSkillsInOneCallback() {
        ToolCallback callback = SkillsTool.builder()
                .addSkillsResource(new ClassPathResource("skills/send-greeting-email/"))
                .addSkillsResource(new ClassPathResource("skills/send-system-notification/"))
                .build();

        assertEquals("Skill", callback.getToolDefinition().name(),
                "合并后工具名仍为 Skill（唯一，不重复）");
        String description = callback.getToolDefinition().description();
        assertTrue(description.contains("send-greeting-email"),
                "合并工具描述应包含技能名 send-greeting-email，实际: " + description);
        assertTrue(description.contains("send-system-notification"),
                "合并工具描述应包含技能名 send-system-notification，实际: " + description);
    }
}
