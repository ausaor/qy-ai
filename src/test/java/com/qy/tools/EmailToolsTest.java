package com.qy.tools;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qy.entity.User;
import com.qy.mapper.UserMapper;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Map;
import java.util.Properties;

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

    /**
     * 纯 JUnit 环境（无 Spring 上下文）下初始化 MyBatis-Plus TableInfo 缓存。
     * LambdaQueryWrapper 的 .in() 方法在构造时会立即解析实体字段映射，
     * 未初始化 TableInfo 将抛出 "can not find lambda cache" 异常。
     */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), User.class);
    }

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

        Map<String, Object> result = emailTools.verifyRecipient(null, "unknown@example.com");

        assertEquals(Boolean.FALSE, result.get("verified"));
        assertNotNull(result.get("error"));
    }

    // ==================== 查询所有用户邮箱 ====================

    @Test
    @DisplayName("查询所有用户邮箱：应返回状态正常用户的邮箱列表")
    void getAllUserEmailsShouldReturnActiveEmails() {
        when(userMapper.selectList(any())).thenReturn(List.of(
                buildUser("A", "甲", "a@qq.com"),
                buildUser("B", "乙", "b@qq.com")));

        Map<String, Object> result = emailTools.getAllUserEmails();

        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals(2, result.get("total"));
        assertEquals(List.of("a@qq.com", "b@qq.com"), result.get("emails"));
    }

    @Test
    @DisplayName("查询所有用户邮箱：应过滤掉 @qy.com 结尾的系统内部邮箱")
    void getAllUserEmailsShouldFilterInternalEmails() {
        when(userMapper.selectList(any())).thenReturn(List.of(
                buildUser("A", "甲", "a@qq.com"),
                buildUser("B", "乙", "internal@qy.com"),
                buildUser("C", "丙", "c@163.com")));

        Map<String, Object> result = emailTools.getAllUserEmails();

        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals(2, result.get("total"));
        assertEquals(List.of("a@qq.com", "c@163.com"), result.get("emails"));
    }

    @Test
    @DisplayName("查询所有用户邮箱：无状态正常用户时应返回失败")
    void getAllUserEmailsShouldFailWhenEmpty() {
        when(userMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = emailTools.getAllUserEmails();

        assertEquals(Boolean.FALSE, result.get("success"));
        assertNotNull(result.get("error"));
    }

    // ==================== 发送问候邮件 ====================

    @Test
    @DisplayName("发送问候邮件：邮箱未注册时应拒绝发送")
    void sendShouldRejectUnregisteredEmail() {
        when(userMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = emailTools.sendGreetingEmail(List.of("unknown@example.com"), "你好", "亲爱的朋友：", "你好呀！");

        assertEquals(Boolean.FALSE, result.get("success"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("发送问候邮件：@qy.com 结尾的内部邮箱应拒绝发送")
    void sendShouldRejectInternalEmail() {
        Map<String, Object> result = emailTools.sendGreetingEmail(List.of("internal@qy.com"), "你好", "亲爱的朋友：", "你好呀！");

        assertEquals(Boolean.FALSE, result.get("success"));
        assertNotNull(result.get("error"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("发送问候邮件：邮箱已注册时应渲染模版并发送")
    void sendShouldRenderTemplateAndSend() {
        when(userMapper.selectList(any()))
                .thenReturn(List.of(buildUser("Spring", "春", "11111111@qq.com")));
        when(templateEngine.process(eq("greeting-email"), any()))
                .thenReturn("<html>问候邮件</html>");
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        Map<String, Object> result = emailTools.sendGreetingEmail(List.of("11111111@qq.com"), "你好", "亲爱的朋友：", "愿你开心！");

        assertEquals(Boolean.TRUE, result.get("success"));
        verify(templateEngine).process(eq("greeting-email"), any());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("发送问候邮件：单个收件人应使用 To 直发，且不使用 Bcc")
    void sendShouldUseToForSingleRecipient() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(userMapper.selectList(any()))
                .thenReturn(List.of(buildUser("Spring", "春", "11111111@qq.com")));
        when(templateEngine.process(eq("greeting-email"), any()))
                .thenReturn("<html>问候邮件</html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        Map<String, Object> result = emailTools.sendGreetingEmail(List.of("11111111@qq.com"), "早安", "亲爱的朋友：", "愿你开心！");

        assertEquals(Boolean.TRUE, result.get("success"));
        Address[] to = mimeMessage.getRecipients(Message.RecipientType.TO);
        assertEquals(1, to.length);
        assertNull(mimeMessage.getRecipients(Message.RecipientType.BCC));
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("发送问候邮件：多个收件人应使用密送 Bcc 发送，保护邮箱隐私")
    void sendShouldUseBccForMultipleRecipients() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(userMapper.selectList(any())).thenReturn(List.of(
                buildUser("A", "甲", "a@qq.com"),
                buildUser("B", "乙", "b@qq.com")));
        when(templateEngine.process(eq("greeting-email"), any()))
                .thenReturn("<html>群发问候</html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        Map<String, Object> result = emailTools.sendGreetingEmail(
                List.of("a@qq.com", "b@qq.com"), "全体问候", "亲爱的朋友：", "大家好！");

        assertEquals(Boolean.TRUE, result.get("success"));
        Address[] bcc = mimeMessage.getRecipients(Message.RecipientType.BCC);
        assertEquals(2, bcc.length);
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("发送问候邮件：主题超过 20 个字时应截断保留前 20 个字")
    void sendShouldTruncateLongSubject() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(userMapper.selectList(any()))
                .thenReturn(List.of(buildUser("Spring", "春", "11111111@qq.com")));
        when(templateEngine.process(eq("greeting-email"), any()))
                .thenReturn("<html>问候邮件</html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        String longSubject = "这是一段超过二十个字的主题用来验证截断逻辑是否正常工作";
        emailTools.sendGreetingEmail(List.of("11111111@qq.com"), longSubject, "亲爱的朋友：", "愿你开心！");

        String actualSubject = mimeMessage.getSubject();
        assertNotNull(actualSubject);
        assertEquals(20, actualSubject.length());
    }

    @Test
    @DisplayName("发送问候邮件：未提供主题时应使用默认主题")
    void sendShouldUseDefaultSubjectWhenMissing() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(userMapper.selectList(any()))
                .thenReturn(List.of(buildUser("Spring", "春", "11111111@qq.com")));
        when(templateEngine.process(eq("greeting-email"), any()))
                .thenReturn("<html>问候邮件</html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailTools.sendGreetingEmail(List.of("11111111@qq.com"), null, "亲爱的朋友：", "愿你开心！");

        assertEquals("来自青语的一份问候", mimeMessage.getSubject());
    }

    @Test
    @DisplayName("发送问候邮件：大模型生成的敬语应传入模版渲染")
    void sendShouldPassSalutationToTemplate() {
        when(userMapper.selectList(any()))
                .thenReturn(List.of(buildUser("Spring", "春", "11111111@qq.com")));
        when(userMapper.selectOne(any()))
                .thenReturn(buildUser("Spring", "春", "11111111@qq.com"));
        when(templateEngine.process(eq("greeting-email"), any()))
                .thenReturn("<html>问候邮件</html>");
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        emailTools.sendGreetingEmail(List.of("11111111@qq.com"), "早安", "春，您好：", "愿你开心！");

        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("greeting-email"), captor.capture());
        assertEquals("春，您好：", captor.getValue().getVariable("salutation"));
        assertEquals("愿你开心！", captor.getValue().getVariable("greeting"));
    }

    @Test
    @DisplayName("发送问候邮件：未提供敬语时应使用默认称呼「亲爱的 + 昵称 + ：」")
    void sendShouldUseDefaultSalutationWhenMissing() {
        when(userMapper.selectList(any()))
                .thenReturn(List.of(buildUser("Spring", "春", "11111111@qq.com")));
        when(userMapper.selectOne(any()))
                .thenReturn(buildUser("Spring", "春", "11111111@qq.com"));
        when(templateEngine.process(eq("greeting-email"), any()))
                .thenReturn("<html>问候邮件</html>");
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        emailTools.sendGreetingEmail(List.of("11111111@qq.com"), "早安", null, "愿你开心！");

        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("greeting-email"), captor.capture());
        assertEquals("亲爱的 春：", captor.getValue().getVariable("salutation"));
    }

    // ==================== 发送系统通知邮件 ====================

    @Test
    @DisplayName("发送系统通知：邮箱未注册时应拒绝发送")
    void sendNotificationShouldRejectUnregisteredEmail() {
        when(userMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = emailTools.sendSystemNotification(
                "系统维护", List.of("unknown@example.com"), "维护通知", "尊敬的用户：", "系统将于今晚维护。");

        assertEquals(Boolean.FALSE, result.get("success"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("发送系统通知：@qy.com 结尾的内部邮箱应拒绝发送")
    void sendNotificationShouldRejectInternalEmail() {
        Map<String, Object> result = emailTools.sendSystemNotification(
                "系统维护", List.of("internal@qy.com"), "维护通知", "尊敬的用户：", "系统将于今晚维护。");

        assertEquals(Boolean.FALSE, result.get("success"));
        assertNotNull(result.get("error"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("发送系统通知：通知正文为空时应拒绝发送")
    void sendNotificationShouldRejectBlankContent() {
        Map<String, Object> result = emailTools.sendSystemNotification(
                "系统维护", List.of("11111111@qq.com"), "维护通知", "尊敬的用户：", "");

        assertEquals(Boolean.FALSE, result.get("success"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("发送系统通知：单个收件人应使用 To 直发，且不使用 Bcc")
    void sendNotificationShouldUseToForSingleRecipient() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(userMapper.selectList(any()))
                .thenReturn(List.of(buildUser("Spring", "春", "11111111@qq.com")));
        when(userMapper.selectOne(any()))
                .thenReturn(buildUser("Spring", "春", "11111111@qq.com"));
        when(templateEngine.process(eq("system-notification"), any()))
                .thenReturn("<html>系统通知</html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        Map<String, Object> result = emailTools.sendSystemNotification(
                "系统维护", List.of("11111111@qq.com"), "青语系统维护通知", "尊敬的用户：", "系统将于今晚 22:00 维护。");

        assertEquals(Boolean.TRUE, result.get("success"));
        Address[] to = mimeMessage.getRecipients(Message.RecipientType.TO);
        assertEquals(1, to.length);
        assertNull(mimeMessage.getRecipients(Message.RecipientType.BCC));
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("发送系统通知：多个收件人应使用密送 Bcc 发送，保护邮箱隐私")
    void sendNotificationShouldUseBccForMultipleRecipients() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(userMapper.selectList(any())).thenReturn(List.of(
                buildUser("A", "甲", "a@qq.com"),
                buildUser("B", "乙", "b@qq.com")));
        when(templateEngine.process(eq("system-notification"), any()))
                .thenReturn("<html>群发系统通知</html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        Map<String, Object> result = emailTools.sendSystemNotification(
                "系统公告", List.of("a@qq.com", "b@qq.com"), "青语系统公告", "尊敬的用户：", "各位用户，青语新版本已上线。");

        assertEquals(Boolean.TRUE, result.get("success"));
        Address[] bcc = mimeMessage.getRecipients(Message.RecipientType.BCC);
        assertEquals(2, bcc.length);
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("发送系统通知：主题超过 50 个字时应截断保留前 50 个字")
    void sendNotificationShouldTruncateLongSubject() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(userMapper.selectList(any()))
                .thenReturn(List.of(buildUser("Spring", "春", "11111111@qq.com")));
        when(userMapper.selectOne(any()))
                .thenReturn(buildUser("Spring", "春", "11111111@qq.com"));
        when(templateEngine.process(eq("system-notification"), any()))
                .thenReturn("<html>系统通知</html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        String longSubject = "青语系统维护通知：为提供更稳定的服务，我们计划于本周六晚十点至次日凌晨两点对系统进行停机维护升级，期间所有服务将暂停使用，请各位用户合理安排使用时间并相互转告，感谢您的理解与支持";
        emailTools.sendSystemNotification("系统维护", List.of("11111111@qq.com"), longSubject, "尊敬的用户：", "维护内容。");

        String actualSubject = mimeMessage.getSubject();
        assertNotNull(actualSubject);
        assertEquals(50, actualSubject.length());
    }

    @Test
    @DisplayName("发送系统通知：未提供主题时应使用默认主题")
    void sendNotificationShouldUseDefaultSubjectWhenMissing() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(userMapper.selectList(any()))
                .thenReturn(List.of(buildUser("Spring", "春", "11111111@qq.com")));
        when(userMapper.selectOne(any()))
                .thenReturn(buildUser("Spring", "春", "11111111@qq.com"));
        when(templateEngine.process(eq("system-notification"), any()))
                .thenReturn("<html>系统通知</html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailTools.sendSystemNotification("系统维护", List.of("11111111@qq.com"), null, "尊敬的用户：", "维护内容。");

        assertEquals("青语系统通知", mimeMessage.getSubject());
    }

    @Test
    @DisplayName("发送系统通知：通知类型不在允许范围内时应归一化为系统通知")
    void sendNotificationShouldNormalizeUnknownType() {
        when(userMapper.selectList(any()))
                .thenReturn(List.of(buildUser("Spring", "春", "11111111@qq.com")));
        when(userMapper.selectOne(any()))
                .thenReturn(buildUser("Spring", "春", "11111111@qq.com"));
        when(templateEngine.process(eq("system-notification"), any()))
                .thenReturn("<html>系统通知</html>");
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        emailTools.sendSystemNotification("随便的类型", List.of("11111111@qq.com"), "通知", "尊敬的各位用户：", "内容。");

        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("system-notification"), captor.capture());
        assertEquals("系统通知", captor.getValue().getVariable("notificationType"));
        assertEquals("尊敬的各位用户：", captor.getValue().getVariable("salutation"));
    }

    @Test
    @DisplayName("发送系统通知：未提供敬语时应使用默认称呼「尊敬的用户：」")
    void sendNotificationShouldUseDefaultSalutationWhenMissing() {
        when(userMapper.selectList(any()))
                .thenReturn(List.of(buildUser("Spring", "春", "11111111@qq.com")));
        when(templateEngine.process(eq("system-notification"), any()))
                .thenReturn("<html>系统通知</html>");
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        emailTools.sendSystemNotification("系统维护", List.of("11111111@qq.com"), "维护通知", null, "维护内容。");

        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("system-notification"), captor.capture());
        assertEquals("尊敬的用户：", captor.getValue().getVariable("salutation"));
    }
}
