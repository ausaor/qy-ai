package com.qy.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 自然语言转SQL工具
 * 供 Text To Sql Agent 使用
 * <p>
 * 所有工具均为只读：仅允许执行 SELECT / WITH 查询，
 * 写操作、结构变更、系统库访问、多语句拼接一律拦截。
 */
@Slf4j
@Component
public class TextToSqlTools {

    /**
     * 单次查询最多返回的行数，防止大结果集撑爆上下文
     */
    private static final int MAX_ROWS = 200;

    /**
     * 单次查询超时时间（秒）
     */
    private static final int QUERY_TIMEOUT_SECONDS = 10;

    /**
     * 单个字段值输出的最大长度，超出部分截断
     */
    private static final int MAX_VALUE_LENGTH = 500;

    /**
     * 合法查询的起始关键字
     */
    private static final Pattern READ_ONLY_START = Pattern.compile("^(select|with)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * 禁止出现的写操作、结构变更及危险函数关键字
     */
    private static final Pattern FORBIDDEN_KEYWORD = Pattern.compile(
            "\\b(insert|update|delete|drop|truncate|alter|create|rename|grant|revoke|merge|call|execute|exec|"
                    + "set|lock|unlock|handler|commit|rollback|savepoint|use|prepare|deallocate|into|outfile|"
                    + "dumpfile|load_file|sleep|benchmark)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * 禁止访问的系统库
     */
    private static final Pattern SYSTEM_SCHEMA = Pattern.compile(
            "\\b(mysql|information_schema|performance_schema|sys)\\s*\\.",
            Pattern.CASE_INSENSITIVE);

    /**
     * 合法的表名字符，用于拦截表名参数中的注入
     */
    private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_$]{1,64}$");

    private final JdbcTemplate jdbcTemplate;

    /**
     * 基于数据源单独构建 JdbcTemplate，设置行数与超时上限，
     * 避免直接复用容器中的 JdbcTemplate 而影响其他业务
     */
    public TextToSqlTools(DataSource dataSource) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setMaxRows(MAX_ROWS);
        template.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        this.jdbcTemplate = template;
    }

    @Tool(name = "listTables", description = "列出当前数据库的所有表名、表注释和大致行数，用于确定用户问题涉及哪些表")
    public Map<String, Object> listTables() {
        log.info("查询数据库表清单");
        Map<String, Object> result = new HashMap<>();
        try {
            String sql = """
                    SELECT TABLE_NAME AS tableName, TABLE_COMMENT AS tableComment, TABLE_ROWS AS tableRows
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
                    ORDER BY TABLE_NAME
                    """;
            List<Map<String, Object>> tables = jdbcTemplate.queryForList(sql);
            result.put("database", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
            result.put("tableCount", tables.size());
            result.put("tables", tables);
        } catch (Exception e) {
            log.error("查询表清单失败: {}", e.getMessage());
            result.put("error", "查询表清单失败: " + rootCauseMessage(e));
        }
        log.info("表清单查询结果: {}", result.get("tableCount"));
        return result;
    }

    @Tool(name = "getTableStructure", description = "查询指定表的字段结构，包含字段名、数据类型、是否可空、是否主键、默认值和字段注释，"
            + "编写 SQL 前必须先调用此工具确认真实字段名")
    public Map<String, Object> getTableStructure(
            @ToolParam(description = "表名，多个表用英文逗号分隔，例如：ai_chat_session,ai_chat_message") String tableNames) {
        log.info("查询表结构: {}", tableNames);
        Map<String, Object> result = new HashMap<>();

        List<String> names = parseTableNames(tableNames);
        if (names.isEmpty()) {
            result.put("error", "表名不能为空，且只能包含字母、数字、下划线");
            return result;
        }

        try {
            String placeholders = String.join(",", names.stream().map(n -> "?").toList());
            String sql = """
                    SELECT TABLE_NAME AS tableName, COLUMN_NAME AS columnName, COLUMN_TYPE AS columnType,
                           IS_NULLABLE AS nullable, COLUMN_KEY AS columnKey, COLUMN_DEFAULT AS columnDefault,
                           COLUMN_COMMENT AS columnComment
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (%s)
                    ORDER BY TABLE_NAME, ORDINAL_POSITION
                    """.formatted(placeholders);

            List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql, names.toArray());
            if (columns.isEmpty()) {
                result.put("error", "未找到表 " + names + "，请先调用 listTables 确认表名");
                return result;
            }

            // 按表名分组，便于模型逐表理解结构
            Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
            for (Map<String, Object> column : columns) {
                String table = String.valueOf(column.remove("tableName"));
                grouped.computeIfAbsent(table, k -> new ArrayList<>()).add(column);
            }
            result.put("tables", grouped);

            List<String> notFound = names.stream().filter(name -> !grouped.containsKey(name)).toList();
            if (!notFound.isEmpty()) {
                result.put("notFound", notFound);
            }
        } catch (Exception e) {
            log.error("查询表结构失败: {}", e.getMessage());
            result.put("error", "查询表结构失败: " + rootCauseMessage(e));
        }
        return result;
    }

    @Tool(name = "explainQuerySql", description = "对查询 SQL 执行 EXPLAIN 查看执行计划，用于在正式查询前校验语法和索引使用情况，不会返回业务数据")
    public Map<String, Object> explainQuerySql(
            @ToolParam(description = "待校验的查询语句，必须以 SELECT 或 WITH 开头") String sql) {
        log.info("校验查询SQL: {}", sql);
        Map<String, Object> result = new HashMap<>();

        String checkedSql;
        try {
            checkedSql = validateReadOnly(sql);
        } catch (IllegalArgumentException e) {
            result.put("error", e.getMessage());
            return result;
        }

        try {
            List<Map<String, Object>> plan = explainSql(checkedSql);
            result.put("sql", checkedSql);
            result.put("plan", plan);
            result.put("planSummary", summarizePlan(plan));
            result.put("valid", true);
        } catch (Exception e) {
            log.error("SQL 校验失败: {}", e.getMessage());
            result.put("valid", false);
            result.put("error", "SQL 校验失败: " + rootCauseMessage(e));
        }
        return result;
    }

    @Tool(name = "executeQuerySql", description = "执行只读查询并返回数据。只接受 SELECT 或 WITH 开头的单条语句，"
            + "任何写操作（INSERT/UPDATE/DELETE/DROP/ALTER 等）都会被拒绝，执行前会自动做一次 EXPLAIN 预校验，最多返回 " + MAX_ROWS + " 行")
    public Map<String, Object> executeQuerySql(
            @ToolParam(description = "待执行的查询语句，必须以 SELECT 或 WITH 开头并带 LIMIT") String sql) {
        log.info("执行查询SQL: {}", sql);
        Map<String, Object> result = new HashMap<>();

        String executeSql;
        try {
            executeSql = appendLimitIfAbsent(validateReadOnly(sql));
        } catch (IllegalArgumentException e) {
            log.warn("SQL 校验未通过: {}", e.getMessage());
            result.put("error", e.getMessage());
            return result;
        }
        result.put("sql", executeSql);

        // 强制 EXPLAIN 预校验：不依赖模型是否主动调用 explainQuerySql，
        // 语法或表名字段名有误时在此拦截，不再真正执行查询
        List<Map<String, Object>> plan;
        try {
            plan = explainSql(executeSql);
        } catch (Exception e) {
            log.error("EXPLAIN 预校验未通过: {}", e.getMessage());
            result.put("error", "SQL 预校验（EXPLAIN）未通过: " + rootCauseMessage(e)
                    + "，请调用 getTableStructure 确认表名和字段名后修正 SQL 重试");
            return result;
        }
        // 摘要仅用于辅助模型判断，生成失败不影响正常查询
        try {
            result.put("planSummary", summarizePlan(plan));
        } catch (Exception e) {
            log.warn("执行计划摘要生成失败: {}", e.getMessage());
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(executeSql);
            result.put("rowCount", rows.size());
            result.put("rows", rows.stream().map(this::simplifyRow).toList());
            if (rows.size() >= MAX_ROWS) {
                result.put("truncated", true);
                result.put("tip", "结果已截断至 " + MAX_ROWS + " 行，如需完整统计请改用聚合查询");
            }
        } catch (Exception e) {
            log.error("执行查询失败: {}", e.getMessage());
            result.put("error", "执行查询失败: " + rootCauseMessage(e) + "，请检查表名、字段名和语法后重试");
        }
        log.info("查询返回行数: {}", result.get("rowCount"));
        return result;
    }

    /**
     * 执行 EXPLAIN 获取执行计划，同时充当 SQL 语法与表名字段名的校验手段
     *
     * @throws org.springframework.dao.DataAccessException SQL 非法时由驱动抛出
     */
    private List<Map<String, Object>> explainSql(String sql) {
        log.info("EXPLAIN 校验: {}", sql);
        // MySQL 9.x 起 explain_format 默认为 TREE（单列文本），显式指定 TRADITIONAL 以获得稳定的表格化列
        return jdbcTemplate.queryForList("EXPLAIN FORMAT=TRADITIONAL " + sql);
    }

    /**
     * 压缩执行计划为易读摘要，避免把完整 EXPLAIN 结果塞进模型上下文
     */
    private Map<String, Object> summarizePlan(List<Map<String, Object>> plan) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<String> steps = new ArrayList<>();
        boolean fullTableScan = false;
        long estimatedRows = 0L;

        for (Map<String, Object> row : plan) {
            Object table = getIgnoreCase(row, "table");
            Object type = getIgnoreCase(row, "type");
            Object key = getIgnoreCase(row, "key");
            Object rows = getIgnoreCase(row, "rows");

            // 非表格化格式（如 TREE）下拿不到 table/type 列，直接保留原始计划文本
            if (table == null && type == null) {
                steps.add(row.values().stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .collect(Collectors.joining(" ")));
                continue;
            }

            steps.add("表 %s: 访问方式=%s, 使用索引=%s, 预估行数=%s".formatted(table, type, key, rows));
            if ("ALL".equalsIgnoreCase(String.valueOf(type))) {
                fullTableScan = true;
            }
            if (rows instanceof Number number) {
                estimatedRows += number.longValue();
            }
        }

        summary.put("steps", steps);
        summary.put("fullTableScan", fullTableScan);
        summary.put("estimatedRows", estimatedRows);
        if (fullTableScan) {
            summary.put("warning", "存在全表扫描，如数据量大建议增加索引字段过滤条件");
        }
        return summary;
    }

    /**
     * 提取最具体的异常原因。
     * Spring 包装后的 message 只有 "bad SQL grammar [sql]"，
     * 不含“Unknown column xxx”这类关键信息，模型无法据此自行修正 SQL
     */
    private String rootCauseMessage(Exception e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    /**
     * EXPLAIN 结果列名大小写由驱动决定，做一次忽略大小写的取值。
     * 列值可以为 null（如未命中索引时 key 为 null），因此不能用 Stream.findFirst
     */
    private Object getIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 只读校验：剥离注释后校验语句类型、关键字、系统库和多语句拼接
     *
     * @return 去除注释和尾部分号后的可执行 SQL
     * @throws IllegalArgumentException 校验不通过时抛出，由调用方转为工具错误信息
     */
    private String validateReadOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        // MySQL 可执行注释会被数据库当作真实语句执行，直接拒绝
        if (sql.contains("/*!")) {
            throw new IllegalArgumentException("SQL 中不允许使用 MySQL 可执行注释");
        }

        // 去除注释，避免通过注释绕过关键字校验
        String cleaned = sql.replaceAll("/\\*[\\s\\S]*?\\*/", " ")
                .replaceAll("--[^\\n]*", " ")
                .replaceAll("#[^\\n]*", " ")
                .replaceAll("\\s+", " ")
                .trim();
        // 去除尾部分号
        cleaned = cleaned.replaceAll(";+\\s*$", "").trim();

        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("SQL 内容为空");
        }
        if (cleaned.contains(";")) {
            throw new IllegalArgumentException("一次只能执行一条语句，不允许用分号拼接多条 SQL");
        }
        if (!READ_ONLY_START.matcher(cleaned).find()) {
            throw new IllegalArgumentException("当前智能体只能执行查询，SQL 必须以 SELECT 或 WITH 开头");
        }

        // 去掉反引号和字符串字面量后再做关键字匹配，避免把数据内容误判为关键字
        String checkTarget = cleaned.replace("`", "")
                .replaceAll("'([^']|'')*'", "''")
                .replaceAll("\"([^\"]|\"\")*\"", "''");

        if (SYSTEM_SCHEMA.matcher(checkTarget).find()) {
            throw new IllegalArgumentException("禁止查询 mysql / information_schema / performance_schema / sys 系统库");
        }
        var matcher = FORBIDDEN_KEYWORD.matcher(checkTarget);
        if (matcher.find()) {
            throw new IllegalArgumentException("检测到禁止使用的关键字 [" + matcher.group() + "]，当前智能体只能执行查询");
        }
        return cleaned;
    }

    /**
     * 未显式指定 LIMIT 时补充行数上限
     */
    private String appendLimitIfAbsent(String sql) {
        if (Pattern.compile("\\blimit\\b", Pattern.CASE_INSENSITIVE).matcher(sql).find()) {
            return sql;
        }
        return sql + " LIMIT " + MAX_ROWS;
    }

    /**
     * 解析并校验表名参数，过滤非法标识符
     */
    private List<String> parseTableNames(String tableNames) {
        if (tableNames == null || tableNames.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tableNames.split("[,，]"))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> name.replace("`", ""))
                .filter(name -> VALID_IDENTIFIER.matcher(name).matches())
                .distinct()
                .toList();
    }

    /**
     * 结果值归一化：二进制字段脱敏、时间等对象转字符串、超长文本截断，
     * 便于模型理解且避免上下文膨胀
     */
    private Map<String, Object> simplifyRow(Map<String, Object> row) {
        Map<String, Object> simplified = new LinkedHashMap<>();
        row.forEach((key, value) -> simplified.put(key, simplifyValue(value)));
        return simplified;
    }

    private Object simplifyValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return "<binary:" + bytes.length + " bytes>";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        String text = String.valueOf(value);
        return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) + "...(已截断)" : text;
    }
}
