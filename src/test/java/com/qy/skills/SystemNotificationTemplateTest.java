package com.qy.skills;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 系统通知邮件模板渲染验证
 * 确认 templates/system-notification.html 可正常渲染，且包含项目名「青语」、发送人「聴夏」、
 * 通知类型与通知正文内容
 */
@DisplayName("系统通知邮件模板渲染")
class SystemNotificationTemplateTest {

    private SpringTemplateEngine newEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);
        return engine;
    }

    @Test
    @DisplayName("模板渲染应包含项目名称、发送人名称、通知类型与通知内容")
    void templateShouldContainProjectNameSenderTypeAndContent() {
        Context context = new Context();
        context.setVariable("nickName", "尊敬的用户");
        context.setVariable("notificationType", "系统维护");
        context.setVariable("content", "系统将于今晚 22:00 进行停机维护，期间服务暂停。");

        String html = newEngine().process("system-notification", context);

        assertTrue(html.contains("青 语"), "模板应包含项目名称「青语」");
        assertTrue(html.contains("聴夏"), "模板应包含发送人名称「聴夏」");
        assertTrue(html.contains("系统维护"), "模板应包含通知类型");
        assertTrue(html.contains("尊敬的用户："), "模板应包含收件人称呼");
        assertTrue(html.contains("系统将于今晚 22:00 进行停机维护，期间服务暂停。"), "模板应包含通知正文内容");
    }

    @Test
    @DisplayName("模板渲染应保留通知正文的分段换行标记")
    void templateShouldKeepLineBreaksInContent() {
        Context context = new Context();
        context.setVariable("nickName", "尊敬的用户");
        context.setVariable("notificationType", "功能发布");
        context.setVariable("content", "青语新版本已上线。<br/>本次更新包含以下内容：<br/>1. 新增深色模式");

        String html = newEngine().process("system-notification", context);

        assertTrue(html.contains("青语新版本已上线。<br/>本次更新包含以下内容：<br/>1. 新增深色模式"),
                "模板应保留已转换的分段标记");
    }
}
