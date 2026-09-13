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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
     * 系统通知邮件模版名（对应 templates/system-notification.html）
     */
    private static final String SYSTEM_NOTIFICATION_TEMPLATE = "system-notification";

    /**
     * 发件人名称
     */
    private static final String SENDER_NAME = "聴夏";

    /**
     * 默认问候邮件主题：大模型未生成主题时的兜底值
     */
    private static final String GREETING_SUBJECT = "来自青语的一份问候";

    /**
     * 默认系统通知主题：大模型未生成主题时的兜底值
     */
    private static final String SYSTEM_SUBJECT_DEFAULT = "青语系统通知";

    /**
     * 问候邮件主题最大长度（字）：主题由大模型生成，超过时自动截断
     */
    private static final int SUBJECT_MAX_LENGTH = 20;

    /**
     * 系统通知主题最大长度（字）：主题由大模型生成，超过时自动截断
     */
    private static final int SYSTEM_SUBJECT_MAX_LENGTH = 50;

    /**
     * 系统内部邮箱后缀：以该后缀结尾的邮箱不参与邮件发送（查询时过滤、发送时拒绝）
     */
    private static final String INTERNAL_EMAIL_SUFFIX = "@qy.com";

    /**
     * 系统通知允许的通知类型：模型传入其他值时归一化为「系统通知」
     */
    private static final Set<String> NOTIFICATION_TYPES = Set.of("系统上线", "功能发布", "系统维护", "系统公告");

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
     * 查询所有状态正常（未删除、未封禁、未禁用）用户的注册邮箱。
     * 自动过滤以 @qy.com 结尾的系统内部邮箱（不发送）。
     * 用户请求向所有人/全体用户群发邮件时，必须先调用本工具获取全部邮箱，
     * 再将返回的 emails 列表传入 sendGreetingEmail（多收件人时自动密送，互不可见邮箱）。
     */
    @Tool(name = "getAllUserEmails", description = "查询系统用户表中所有状态正常用户的注册邮箱列表，已自动过滤 @qy.com 结尾的系统内部邮箱。"
            + "当用户请求向所有人/全体用户/所有成员群发邮件时，必须先调用本工具获取全部邮箱，"
            + "然后将返回的 emails 列表作为 sendGreetingEmail 的 emails 参数传入")
    public Map<String, Object> getAllUserEmails() {
        log.info("查询所有系统用户邮箱");
        Map<String, Object> result = new HashMap<>();

        List<String> emails = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .eq(User::getIsBanned, 0)
                        .eq(User::getIsDisable, 0))
                .stream()
                .map(User::getEmail)
                .filter(StringUtils::hasText)
                .filter(email -> !isInternalEmail(email))
                .distinct()
                .toList();

        result.put("total", emails.size());
        result.put("emails", emails);
        if (emails.isEmpty()) {
            result.put("success", false);
            result.put("error", "系统用户表中没有状态正常的用户邮箱，无法发送邮件");
        } else {
            result.put("success", true);
        }
        return result;
    }

    /**
     * 发送问候邮件：使用 templates/greeting-email.html 模版渲染正文。
     * 主题与开头敬语由大模型生成（主题不超过 20 个字，超长自动截断，未提供时使用默认主题；敬语未提供时使用默认称呼）。
     * 内部会再次强校验收件邮箱在系统中已注册，未注册直接拒绝，防止跳过校验步骤。
     * 以 @qy.com 结尾的系统内部邮箱不允许发送，一律拒绝。
     * 单个收件人使用 To 直发；多个收件人自动使用密送（Bcc），所有接收人互不可见对方的邮箱地址。
     */
    @Tool(name = "sendGreetingEmail", description = "向系统用户发送问候邮件。参数 emails 必须是 verifyRecipient 或 getAllUserEmails 校验通过后返回的注册邮箱列表；"
            + "内部会再次校验每个邮箱是否已注册，未注册将拒绝发送；@qy.com 结尾的系统内部邮箱一律拒绝发送。"
            + "多个收件人时自动使用密送（Bcc）发送，接收人之间互不可见邮箱；"
            + "只有 1 个收件人时使用普通发送（To）。subject 为邮件主题、salutation 为邮件开头的敬语，均由你结合问候场景生成，主题不超过 20 个字；"
            + "greeting 为问候正文，简洁温馨，正文中不要再包含敬语。"
            + "邮件正文使用问候模版渲染，发件邮箱和发件人名称由系统固定配置")
    public Map<String, Object> sendGreetingEmail(
            @ToolParam(description = "收件人邮箱列表（必须来自 verifyRecipient 或 getAllUserEmails 的校验结果，不得包含 @qy.com 结尾的邮箱）") List<String> emails,
            @ToolParam(description = "邮件主题，结合问候场景（节日/场合/用户意图）生成，不超过 20 个字") String subject,
            @ToolParam(description = "邮件开头的敬语，由你生成，如「亲爱的朋友：」，不超过 30 个字；未提供时使用默认称呼") String salutation,
            @ToolParam(description = "问候正文内容，简洁温馨，一两句话即可；正文中不要再包含敬语") String greeting) {

        log.info("发送问候邮件: emails = {}, subject = {}", emails, subject);
        Map<String, Object> result = new HashMap<>();

        List<String> recipients = normalizeEmails(emails);
        if (recipients.isEmpty()) {
            result.put("success", false);
            result.put("error", "收件人邮箱列表为空，请先通过 verifyRecipient 或 getAllUserEmails 获取可用邮箱");
            return result;
        }
        if (!StringUtils.hasText(greeting)) {
            result.put("success", false);
            result.put("error", "问候语内容不能为空");
            return result;
        }

        // 强校验：@qy.com 结尾的系统内部邮箱不允许发送
        List<String> internalEmails = recipients.stream()
                .filter(this::isInternalEmail)
                .toList();
        if (!internalEmails.isEmpty()) {
            result.put("success", false);
            result.put("error", "以下邮箱为系统内部邮箱（" + INTERNAL_EMAIL_SUFFIX + " 结尾），不允许发送: " + String.join(", ", internalEmails));
            return result;
        }

        // 强校验：所有邮箱必须在系统用户表中已注册且状态正常
        List<String> invalidEmails = findUnregisteredEmails(recipients);
        if (!invalidEmails.isEmpty()) {
            result.put("success", false);
            result.put("error", "以下邮箱格式不合法或未在系统中注册/状态异常，已拒绝发送: " + String.join(", ", invalidEmails));
            return result;
        }

        try {
            // 单个收件人使用其昵称称呼；群发时使用通用称呼「朋友」
            String nickName = "朋友";
            if (recipients.size() == 1) {
                User user = findActiveUserByEmail(recipients.get(0));
                if (user != null) {
                    nickName = user.getNickName();
                }
            }
            String html = renderGreetingHtml(nickName, salutation, greeting);
            sendHtmlEmail(recipients, normalizeSubject(subject), html);
            result.put("success", true);
            result.put("message", buildSuccessMessage(recipients, nickName));
            log.info("问候邮件发送成功: {} -> {} 位收件人", senderEmail, recipients.size());
        } catch (Exception e) {
            log.error("问候邮件发送失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", "邮件发送失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 发送系统通知邮件：使用 templates/system-notification.html 模版渲染正文。
     * 用于系统上线、功能发布、系统维护、系统公告等官方通知场景。
     * 主题、开头敬语与通知正文由大模型根据用户输入生成（主题不超过 50 个字，超长自动截断，未提供时使用默认主题；敬语未提供时使用默认称呼）。
     * 内部会再次强校验收件邮箱在系统中已注册，未注册直接拒绝，防止跳过校验步骤。
     * 以 @qy.com 结尾的系统内部邮箱不允许发送，一律拒绝。
     * 单个收件人使用 To 直发；多个收件人自动使用密送（Bcc），所有接收人互不可见对方的邮箱地址。
     */
    @Tool(name = "sendSystemNotification", description = "发送系统通知邮件（系统上线、功能发布、系统维护、系统公告等）。"
            + "参数 emails 必须是 verifyRecipient 或 getAllUserEmails 校验通过后返回的注册邮箱列表；"
            + "内部会再次校验每个邮箱是否已注册，未注册将拒绝发送；@qy.com 结尾的系统内部邮箱一律拒绝发送。"
            + "多个收件人时自动使用密送（Bcc）发送，接收人之间互不可见邮箱；只有 1 个收件人时使用普通发送（To）。"
            + "subject 为邮件主题、salutation 为邮件开头的敬语、content 为通知正文，均由你结合通知类型与用户输入生成，主题不超过 50 个字；"
            + "正文中不要再包含敬语。邮件正文使用系统通知模版渲染，模版内固定包含项目名称「青语」与发送人「聴夏」")
    public Map<String, Object> sendSystemNotification(
            @ToolParam(description = "通知类型，四选一：系统上线 / 功能发布 / 系统维护 / 系统公告") String notificationType,
            @ToolParam(description = "收件人邮箱列表（必须来自 verifyRecipient 或 getAllUserEmails 的校验结果，不得包含 @qy.com 结尾的邮箱）") List<String> emails,
            @ToolParam(description = "邮件主题，结合通知类型与内容生成，不超过 50 个字") String subject,
            @ToolParam(description = "邮件开头的敬语，由你生成，如「尊敬的用户：」，不超过 30 个字；未提供时使用默认称呼") String salutation,
            @ToolParam(description = "通知正文内容，正式清晰，包含通知关键信息（时间、影响范围、操作建议等），可分段表述；正文中不要再包含敬语") String content) {

        log.info("发送系统通知邮件: type = {}, emails = {}, subject = {}", notificationType, emails, subject);
        Map<String, Object> result = new HashMap<>();

        List<String> recipients = normalizeEmails(emails);
        if (recipients.isEmpty()) {
            result.put("success", false);
            result.put("error", "收件人邮箱列表为空，请先通过 verifyRecipient 或 getAllUserEmails 获取可用邮箱");
            return result;
        }
        if (!StringUtils.hasText(content)) {
            result.put("success", false);
            result.put("error", "通知正文内容不能为空");
            return result;
        }

        // 强校验：@qy.com 结尾的系统内部邮箱不允许发送
        List<String> internalEmails = recipients.stream()
                .filter(this::isInternalEmail)
                .toList();
        if (!internalEmails.isEmpty()) {
            result.put("success", false);
            result.put("error", "以下邮箱为系统内部邮箱（" + INTERNAL_EMAIL_SUFFIX + " 结尾），不允许发送: " + String.join(", ", internalEmails));
            return result;
        }

        // 强校验：所有邮箱必须在系统用户表中已注册且状态正常
        List<String> invalidEmails = findUnregisteredEmails(recipients);
        if (!invalidEmails.isEmpty()) {
            result.put("success", false);
            result.put("error", "以下邮箱格式不合法或未在系统中注册/状态异常，已拒绝发送: " + String.join(", ", invalidEmails));
            return result;
        }

        try {
            String html = renderSystemNotificationHtml(
                    salutation, normalizeNotificationType(notificationType), content);
            sendHtmlEmail(recipients, normalizeSystemSubject(subject), html);
            result.put("success", true);
            result.put("message", buildSystemSuccessMessage(recipients));
            log.info("系统通知邮件发送成功: {} -> {} 位收件人", senderEmail, recipients.size());
        } catch (Exception e) {
            log.error("系统通知邮件发送失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", "邮件发送失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 清洗收件邮箱列表：去空白、去空、去重
     */
    private List<String> normalizeEmails(List<String> emails) {
        if (emails == null) {
            return List.of();
        }
        return emails.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * 判断是否为系统内部邮箱（以 @qy.com 结尾，不区分大小写）
     */
    private boolean isInternalEmail(String email) {
        return email != null && email.toLowerCase().endsWith(INTERNAL_EMAIL_SUFFIX);
    }

    /**
     * 批量强校验收件邮箱：返回其中格式不合法或未注册/状态异常的邮箱列表
     */
    private List<String> findUnregisteredEmails(List<String> emails) {
        List<String> invalidFormat = emails.stream()
                .filter(e -> !EMAIL_PATTERN.matcher(e).matches())
                .toList();
        if (!invalidFormat.isEmpty()) {
            return invalidFormat;
        }
        Set<String> validEmails = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getEmail, emails)
                        .eq(User::getIsBanned, 0)
                        .eq(User::getIsDisable, 0))
                .stream()
                .map(User::getEmail)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return emails.stream()
                .filter(e -> !validEmails.contains(e))
                .toList();
    }

    /**
     * 归一化邮件主题：大模型未生成时使用默认主题；超过 20 个字时截断保留前 20 个字
     */
    private String normalizeSubject(String subject) {
        if (!StringUtils.hasText(subject)) {
            return GREETING_SUBJECT;
        }
        String trimmed = subject.trim();
        return trimmed.length() > SUBJECT_MAX_LENGTH
                ? trimmed.substring(0, SUBJECT_MAX_LENGTH)
                : trimmed;
    }

    /**
     * 组装发送成功提示信息
     */
    private String buildSuccessMessage(List<String> recipients, String nickName) {
        if (recipients.size() == 1) {
            return "问候邮件已成功发送至 " + recipients.get(0) + "（收件人: " + nickName + "）";
        }
        return "问候邮件已通过密送（Bcc）成功发送至 " + recipients.size() + " 位系统用户，收件人之间互不可见邮箱";
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
     * 基于 Thymeleaf 模版渲染问候邮件 HTML 正文。
     * 开头敬语由大模型生成（salutation），未提供时使用默认称呼「亲爱的 + 昵称 + ：」。
     */
    private String renderGreetingHtml(String nickName, String salutation, String greeting) {
        Context context = new Context();
        context.setVariable("salutation", resolveGreetingSalutation(salutation, nickName));
        context.setVariable("greeting", greeting);
        return templateEngine.process(GREETING_TEMPLATE, context);
    }

    /**
     * 归一化问候邮件开头敬语：大模型未生成时使用默认称呼「亲爱的 + 昵称 + ：」
     */
    private String resolveGreetingSalutation(String salutation, String nickName) {
        if (StringUtils.hasText(salutation)) {
            return salutation.trim();
        }
        return "亲爱的 " + nickName + "：";
    }

    /**
     * 基于 Thymeleaf 模版渲染系统通知邮件 HTML 正文。
     * 开头敬语由大模型生成（salutation），未提供时使用默认称呼「尊敬的用户：」。
     * 通知正文先做 HTML 转义防止注入，再将换行转换为 <br/> 保留分段效果。
     */
    private String renderSystemNotificationHtml(String salutation, String notificationType, String content) {
        Context context = new Context();
        context.setVariable("salutation", resolveSystemSalutation(salutation));
        context.setVariable("notificationType", notificationType);
        context.setVariable("content", escapeHtml(content).replace("\n", "<br/>"));
        return templateEngine.process(SYSTEM_NOTIFICATION_TEMPLATE, context);
    }

    /**
     * 归一化系统通知邮件开头敬语：大模型未生成时使用默认称呼「尊敬的用户：」
     */
    private String resolveSystemSalutation(String salutation) {
        if (StringUtils.hasText(salutation)) {
            return salutation.trim();
        }
        return "尊敬的用户：";
    }

    /**
     * HTML 转义：通知正文由大模型生成，模版以 th:utext 渲染，转义防止 XSS 注入
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 归一化通知类型：不在允许范围内时使用通用「系统通知」
     */
    private String normalizeNotificationType(String notificationType) {
        if (StringUtils.hasText(notificationType) && NOTIFICATION_TYPES.contains(notificationType.trim())) {
            return notificationType.trim();
        }
        return "系统通知";
    }

    /**
     * 归一化系统通知主题：大模型未生成时使用默认主题；超过 50 个字时截断保留前 50 个字
     */
    private String normalizeSystemSubject(String subject) {
        if (!StringUtils.hasText(subject)) {
            return SYSTEM_SUBJECT_DEFAULT;
        }
        String trimmed = subject.trim();
        return trimmed.length() > SYSTEM_SUBJECT_MAX_LENGTH
                ? trimmed.substring(0, SYSTEM_SUBJECT_MAX_LENGTH)
                : trimmed;
    }

    /**
     * 组装系统通知发送成功提示信息
     */
    private String buildSystemSuccessMessage(List<String> recipients) {
        if (recipients.size() == 1) {
            return "系统通知邮件已成功发送至 " + recipients.get(0);
        }
        return "系统通知邮件已通过密送（Bcc）成功发送至 " + recipients.size() + " 位系统用户，收件人之间互不可见邮箱";
    }

    /**
     * 发送 HTML 邮件：发件邮箱取配置 spring.mail.username，发件人名称固定「聴夏」。
     * 单个收件人使用 To 直发；多个收件人全部放入密送（Bcc）保护邮箱隐私，To 置为发件人自身。
     */
    private void sendHtmlEmail(List<String> recipients, String subject, String html) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(new InternetAddress(senderEmail, SENDER_NAME, "UTF-8"));
        if (recipients.size() == 1) {
            helper.setTo(recipients.get(0));
        } else {
            helper.setTo(senderEmail);
            helper.setBcc(recipients.toArray(new String[0]));
        }
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
    }
}
