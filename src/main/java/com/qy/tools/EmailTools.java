package com.qy.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qy.entity.User;
import com.qy.mapper.UserMapper;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 邮件发送工具
 * 供 send_email Agent 使用：发送前必须查询系统用户表（im_user）校验收件人，
 * 校验通过后基于 Thymeleaf 模版渲染问候邮件并发送。
 * 发件邮箱固定使用配置文件中的 spring.mail.username，发件人名称固定为「聴夏」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailTools {

    /**
     * 简易邮箱格式校验
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * 问候邮件模版名（对应 templates/greeting-email.html）
     */
    private static final String GREETING_TEMPLATE = "greeting-email";

    /**
     * 发件人名称
     */
    private static final String SENDER_NAME = "聴夏";

    /**
     * 邮件主题
     */
    private static final String GREETING_SUBJECT = "来自青语的一份问候";

    private final UserMapper userMapper;

    private final JavaMailSender mailSender;

    private final TemplateEngine templateEngine;

    /**
     * 发件邮箱：读取配置文件 spring.mail.username，不硬编码
     */
    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * 校验收件人：按用户名/昵称或邮箱查询系统用户表，确认用户存在且状态正常。
     * 发送任何邮件前必须先调用本工具校验通过，严禁使用未经校验的邮箱发送。
     */
    @Tool(name = "verifyRecipient", description = "查询系统用户表校验收件人。用户提供姓名/昵称时按用户名或昵称查询并返回其注册邮箱；"
            + "用户直接提供邮箱时校验该邮箱是否已注册。发送邮件前必须先调用本工具，校验通过后才能继续发送")
    public Map<String, Object> verifyRecipient(
            @ToolParam(description = "收件人用户名或昵称（用户提到向某人发送时提供，与 email 二选一即可）") String name,
            @ToolParam(description = "收件人邮箱（用户直接指定邮箱时提供，与 name 二选一即可）") String email) {

        log.info("校验收件人: name = {}, email = {}", name, email);
        Map<String, Object> result = new HashMap<>();

        String trimmedName = StringUtils.hasText(name) ? name.trim() : null;
        String trimmedEmail = StringUtils.hasText(email) ? email.trim() : null;

        if (trimmedName == null && trimmedEmail == null) {
            result.put("verified", false);
            result.put("error", "缺少收件人信息：请提供用户名/昵称或邮箱");
            return result;
        }

        // 优先按姓名/昵称查询；未提供姓名时按邮箱查询
        if (trimmedName != null) {
            return verifyByName(trimmedName);
        }
        return verifyByEmail(trimmedEmail);
    }

    /**
     * 发送问候邮件：使用 templates/greeting-email.html 模版渲染正文。
     * 内部会再次强校验收件邮箱在系统中已注册，未注册直接拒绝，防止跳过校验步骤。
     */
    @Tool(name = "sendGreetingEmail", description = "向系统用户发送问候邮件。参数 email 必须是 verifyRecipient 校验通过后返回的注册邮箱；"
            + "内部会再次校验邮箱是否已注册，未注册将拒绝发送。邮件正文使用问候模版渲染，发件邮箱和发件人名称由系统固定配置")
    public Map<String, Object> sendGreetingEmail(
            @ToolParam(description = "收件人邮箱（必须来自 verifyRecipient 的校验结果）") String email,
            @ToolParam(description = "问候语内容，简洁温馨，一两句话即可") String greeting) {

        log.info("发送问候邮件: email = {}", email);
        Map<String, Object> result = new HashMap<>();

        String trimmedEmail = StringUtils.hasText(email) ? email.trim() : null;
        if (trimmedEmail == null || !EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            result.put("success", false);
            result.put("error", "邮箱格式不合法: " + email);
            return result;
        }
        if (!StringUtils.hasText(greeting)) {
            result.put("success", false);
            result.put("error", "问候语内容不能为空");
            return result;
        }

        // 强校验：邮箱必须在系统用户表中已注册且状态正常
        User user = findActiveUserByEmail(trimmedEmail);
        if (user == null) {
            result.put("success", false);
            result.put("error", "邮箱 " + trimmedEmail + " 未在系统中注册或账号状态异常，已拒绝发送");
            return result;
        }

        try {
            String html = renderGreetingHtml(user.getNickName(), greeting);
            sendHtmlEmail(trimmedEmail, GREETING_SUBJECT, html);
            result.put("success", true);
            result.put("message", "问候邮件已成功发送至 " + trimmedEmail + "（收件人: " + user.getNickName() + "）");
            log.info("问候邮件发送成功: {} -> {}", senderEmail, trimmedEmail);
        } catch (Exception e) {
            log.error("问候邮件发送失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", "邮件发送失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 按用户名/昵称校验：先精确匹配，再模糊匹配；多个模糊候选时返回候选列表
     */
    private Map<String, Object> verifyByName(String name) {
        Map<String, Object> result = new HashMap<>();

        List<User> exact = userMapper.selectList(new LambdaQueryWrapper<User>()
                .and(w -> w.eq(User::getUserName, name).or().eq(User::getNickName, name))
                .eq(User::getIsBanned, 0)
                .eq(User::getIsDisable, 0)
                .last("LIMIT 10"));
        if (!exact.isEmpty()) {
            return buildVerifiedResult(exact.get(0));
        }

        // 精确未命中时模糊匹配，帮助用户定位到正确的用户
        List<User> fuzzy = userMapper.selectList(new LambdaQueryWrapper<User>()
                .and(w -> w.like(User::getUserName, name).or().like(User::getNickName, name))
                .eq(User::getIsBanned, 0)
                .eq(User::getIsDisable, 0)
                .last("LIMIT 10"));
        if (fuzzy.isEmpty()) {
            result.put("verified", false);
            result.put("error", "系统用户表中未找到名为「" + name + "」的用户，请确认姓名/昵称后重试");
            return result;
        }
        if (fuzzy.size() > 1) {
            result.put("verified", false);
            result.put("error", "「" + name + "」匹配到多个用户，请指定更精确的用户名或昵称");
            result.put("candidates", fuzzy.stream()
                    .map(u -> u.getNickName() + "(" + u.getUserName() + ")")
                    .toList());
            return result;
        }
        return buildVerifiedResult(fuzzy.get(0));
    }

    /**
     * 按邮箱校验
     */
    private Map<String, Object> verifyByEmail(String email) {
        Map<String, Object> result = new HashMap<>();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            result.put("verified", false);
            result.put("error", "邮箱格式不合法: " + email);
            return result;
        }
        User user = findActiveUserByEmail(email);
        if (user == null) {
            result.put("verified", false);
            result.put("error", "邮箱 " + email + " 未在系统中注册或账号状态异常");
            return result;
        }
        return buildVerifiedResult(user);
    }

    /**
     * 查询状态正常（未删除、未封禁、未禁用）的用户
     */
    private User findActiveUserByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .eq(User::getIsBanned, 0)
                .eq(User::getIsDisable, 0)
                .last("LIMIT 1"));
    }

    /**
     * 组装校验通过的返回结果，供模型确认收件人信息
     */
    private Map<String, Object> buildVerifiedResult(User user) {
        Map<String, Object> result = new HashMap<>();
        result.put("verified", true);
        result.put("userId", user.getId());
        result.put("userName", user.getUserName());
        result.put("nickName", user.getNickName());
        result.put("email", user.getEmail());
        return result;
    }

    /**
     * 基于 Thymeleaf 模版渲染问候邮件 HTML 正文
     */
    private String renderGreetingHtml(String nickName, String greeting) {
        Context context = new Context();
        context.setVariable("nickName", nickName);
        context.setVariable("greeting", greeting);
        return templateEngine.process(GREETING_TEMPLATE, context);
    }

    /**
     * 发送 HTML 邮件：发件邮箱取配置 spring.mail.username，发件人名称固定「聴夏」
     */
    private void sendHtmlEmail(String to, String subject, String html) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(new InternetAddress(senderEmail, SENDER_NAME, "UTF-8"));
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
    }
}
