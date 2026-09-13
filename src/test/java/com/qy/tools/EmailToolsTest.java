package com.qy.tools;

import com.qy.entity.User;
import com.qy.mapper.UserMapper;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * EmailTools 单元测试
 * 验证收件人校验（按姓名/邮箱查系统用户表）与问候邮件发送的核心逻辑
 */
@DisplayName("邮件发送工具")
class EmailToolsTest {

    private UserMapper userMapper;

    private JavaMailSender mailSender;

    private TemplateEngine templateEngine;

    private EmailTools emailTools;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        mailSender = mock(JavaMailSender.class);
        templateEngine = mock(TemplateEngine.class);
        emailTools = new EmailTools(userMapper, mailSender, templateEngine);
        ReflectionTestUtils.setField(emailTools, "senderEmail", "qingyu017@qq.com");
    }

    private User buildUser(String userName, String nickName, String email) {
        User user = new User();
        user.setId(1L);
        user.setUserName(userName);
        user.setNickName(nickName);
        user.setEmail(email);
        return user;
    }

    // ==================== 收件人校验 ====================

    @Test
    @DisplayName("按用户名校验：命中用户时应返回其注册邮箱")
    void verifyByNameShouldReturnRegisteredEmail() {
        when(userMapper.selectList(any()))
                .thenReturn(List.of(buildUser("Spring", "春", "11111111@qq.com")), List.of());

        Map<String, Object> result = emailTools.verifyRecipient("Spring", null);

        assertEquals(Boolean.TRUE, result.get("verified"));
        assertEquals("11111111@qq.com", result.get("email"));
        assertEquals("春", result.get("nickName"));
    }

    @Test
    @DisplayName("按用户名校验：用户不存在时应返回校验失败")
    void verifyByNameShouldFailWhenUserNotFound() {
        when(userMapper.selectList(any())).thenReturn(List.of(), List.of());

        Map<String, Object> result = emailTools.verifyRecipient("不存在的人", null);

        assertEquals(Boolean.FALSE, result.get("verified"));
        assertNotNull(result.get("error"));
    }

    @Test
    @DisplayName("按邮箱校验：邮箱已注册时应返回校验通过")
    void verifyByEmailShouldPassWhenRegistered() {
        when(userMapper.selectOne(any()))
                .thenReturn(buildUser("Winter", "冬", "11111111@163.com"));

        Map<String, Object> result = emailTools.verifyRecipient(null, "11111111@163.com");

        assertEquals(Boolean.TRUE, result.get("verified"));
        assertEquals("Winter", result.get("userName"));
    }

    @Test
    @DisplayName("按邮箱校验：邮箱未注册时应返回校验失败")
    void verifyByEmailShouldFailWhenNotRegistered() {
        when(userMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = emailTools.verifyRecipient(null, "unknown@qy.com");

        assertEquals(Boolean.FALSE, result.get("verified"));
        assertNotNull(result.get("error"));
    }

    // ==================== 发送问候邮件 ====================

    @Test
    @DisplayName("发送问候邮件：邮箱未注册时应拒绝发送")
    void sendShouldRejectUnregisteredEmail() {
        when(userMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = emailTools.sendGreetingEmail("unknown@qy.com", "你好呀！");

        assertEquals(Boolean.FALSE, result.get("success"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("发送问候邮件：邮箱已注册时应渲染模版并发送")
    void sendShouldRenderTemplateAndSend() {
        when(userMapper.selectOne(any()))
                .thenReturn(buildUser("Spring", "春", "11111111@qq.com"));
        when(templateEngine.process(eq("greeting-email"), any()))
                .thenReturn("<html>问候邮件</html>");
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        Map<String, Object> result = emailTools.sendGreetingEmail("11111111@qq.com", "愿你开心！");

        assertEquals(Boolean.TRUE, result.get("success"));
        verify(templateEngine).process(eq("greeting-email"), any());
        verify(mailSender).send(any(MimeMessage.class));
    }
}
