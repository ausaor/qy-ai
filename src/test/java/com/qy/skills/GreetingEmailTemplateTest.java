package com.qy.skills;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 问候邮件模板渲染验证
 * 确认 templates/greeting-email.html 可正常渲染，且包含项目名「青语」、发送人「聴夏」与问候内容
 */
@DisplayName("问候邮件模板渲染")
class GreetingEmailTemplateTest {

    @Test
    @DisplayName("模板渲染应包含项目名称、发送人名称与问候内容")
    void templateShouldContainProjectNameSenderAndGreeting() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);

        Context context = new Context();
        context.setVariable("nickName", "春");
        context.setVariable("greeting", "愿你拥有美好的一天！");

        String html = engine.process("greeting-email", context);

        assertTrue(html.contains("青 语"), "模板应包含项目名称「青语」");
        assertTrue(html.contains("聴夏"), "模板应包含发送人名称「聴夏」");
        assertTrue(html.contains("亲爱的 春："), "模板应包含收件人称呼");
        assertTrue(html.contains("愿你拥有美好的一天！"), "模板应包含问候语内容");
    }
}
