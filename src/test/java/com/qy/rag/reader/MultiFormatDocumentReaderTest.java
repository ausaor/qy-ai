package com.qy.rag.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultiFormatDocumentReader 单元测试
 * 验证修复后的 Markdown 读取器能正确包含代码块和引用块内容
 */
@DisplayName("多格式文档读取器")
class MultiFormatDocumentReaderTest {

    private MultiFormatDocumentReader reader;

    @BeforeEach
    void setUp() {
        reader = new MultiFormatDocumentReader();
    }

    // ==================== Markdown 读取 ====================

    @Test
    @DisplayName("Markdown 读取应包含常规段落内容")
    void markdownReaderShouldIncludeParagraphs(@TempDir Path tempDir) throws IOException {
        String md = """
                # 项目简介
                                
                这是一个基于 Spring Boot 的 AI 聊天服务项目。
                支持多种 AI 模型和流式消息回复。
                """;
        Path file = tempDir.resolve("test.md");
        Files.writeString(file, md);

        List<Document> docs = reader.read(
                new FileSystemResource(file), "test.md", "text/markdown");

        assertFalse(docs.isEmpty(), "应读取到文档内容");
        String allText = docs.stream().map(Document::getText).reduce("", String::concat);
        assertTrue(allText.contains("Spring Boot"), "应包含 'Spring Boot'");
        assertTrue(allText.contains("AI"), "应包含 'AI'");
    }

    @Test
    @DisplayName("修复后 Markdown 读取器应包含代码块内容")
    void markdownReaderWithCodeBlocksShouldIncludeThem(@TempDir Path tempDir) throws IOException {
        String md = """
                # 项目结构
                                
                ```
                src/
                ├── main/
                │   └── java/
                │       └── com/qy/
                │           ├── config/
                │           ├── controller/
                │           └── service/
                ```
                                
                以上是项目目录结构。
                """;
        Path file = tempDir.resolve("readme.md");
        Files.writeString(file, md);

        List<Document> docs = reader.read(
                new FileSystemResource(file), "readme.md", "text/markdown");

        assertFalse(docs.isEmpty(), "应读取到文档内容");
        String allText = docs.stream().map(Document::getText).reduce("", String::concat);

        // 修复前 withIncludeCodeBlock(false) 会排除代码块，
        // 修复后应包含 controller/ 和 service/ 等代码块内容
        assertTrue(allText.contains("controller"), "应包含代码块中的 'controller'");
        assertTrue(allText.contains("service"), "应包含代码块中的 'service'");
    }

    @Test
    @DisplayName("修复后 Markdown 读取器应包含引用块内容")
    void markdownReaderWithBlockquotesShouldIncludeThem(@TempDir Path tempDir) throws IOException {
        String md = """
                # 重要说明
                                
                > 此项目采用 MIT 许可证
                > 欢迎贡献代码
                                
                详情请参阅 LICENSE 文件。
                """;
        Path file = tempDir.resolve("license.md");
        Files.writeString(file, md);

        List<Document> docs = reader.read(
                new FileSystemResource(file), "license.md", "text/markdown");

        String allText = docs.stream().map(Document::getText).reduce("", String::concat);

        // 修复前 withIncludeBlockquote(false) 会排除引用块，
        // 修复后应包含
        assertTrue(allText.contains("MIT"), "应包含引用块中的 'MIT 许可证'");
        assertTrue(allText.contains("贡献"), "应包含引用块中的 '贡献代码'");
    }

    @Test
    @DisplayName("README.md 风格文档读取后所有关键信息应不丢失")
    void readmeStyleMarkdownShouldPreserveAllContent(@TempDir Path tempDir) throws IOException {
        String md = """
                # QyAI 项目说明
                                
                ## 项目简介
                QyAI 是一个基于 Spring Boot 的 AI 聊天服务项目。
                                
                ## 主要功能
                - 支持多种 AI 模型
                - 提供流式消息回复接口
                - 支持 MCP 协议
                                
                ## 技术架构
                - **后端框架**：Spring Boot 3.x
                - **缓存**：Redis
                - **认证机制**：JWT
                                
                ## 使用方式
                ```
                mvn spring-boot:run
                ```
                                
                > 项目默认启动端口为 8080
                """;
        Path file = tempDir.resolve("README.md");
        Files.writeString(file, md);

        List<Document> docs = reader.read(
                new FileSystemResource(file), "README.md", "text/markdown");

        assertFalse(docs.isEmpty(), "应读取到文档内容");
        String allText = docs.stream().map(Document::getText).reduce("", String::concat);

        // 关键信息不丢失
        assertTrue(allText.contains("QyAI"), "应包含项目名");
        assertTrue(allText.contains("Spring Boot"), "应包含后端框架");
        assertTrue(allText.contains("Redis"), "应包含缓存技术");
        assertTrue(allText.contains("JWT"), "应包含认证机制");

        // 修复验证：代码块和引用块内容都应存在
        assertTrue(allText.contains("mvn spring-boot:run"),
                "修复后应包含代码块中的 mvn 命令");
        assertTrue(allText.contains("8080"),
                "修复后应包含引用块中的端口号");
    }

    // ==================== 元数据验证 ====================

    @Test
    @DisplayName("读取的文档应包含正确的元数据")
    void documentsShouldHaveCorrectMetadata(@TempDir Path tempDir) throws IOException {
        String md = "# 测试文档\n\n测试内容。";
        Path file = tempDir.resolve("test.md");
        Files.writeString(file, md);

        List<Document> docs = reader.read(
                new FileSystemResource(file), "test.md", "text/markdown");

        assertFalse(docs.isEmpty());
        for (Document doc : docs) {
            assertEquals("test.md", doc.getMetadata().get("source_file"),
                    "来源文件应为 test.md");
            assertEquals("markdown", doc.getMetadata().get("file_type"),
                    "文件类型应为 markdown");
        }
    }

    // ==================== 文本文件读取 ====================

    @Test
    @DisplayName("纯文本文件应正确读取")
    void plainTextShouldBeReadCorrectly(@TempDir Path tempDir) throws IOException {
        String content = "这是纯文本文件内容。\n包含多行文字。";
        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, content);

        List<Document> docs = reader.read(
                new FileSystemResource(file), "notes.txt", "text/plain");

        assertFalse(docs.isEmpty());
        String allText = docs.stream().map(Document::getText).reduce("", String::concat);
        assertTrue(allText.contains("纯文本文件"), "应包含原始文本内容");
    }

    // ==================== 文件类型识别 ====================

    @Test
    @DisplayName("无 contentType 时通过 .md 扩展名识别为 Markdown")
    void shouldFallbackToExtensionForMarkdown(@TempDir Path tempDir) throws IOException {
        String md = "# 测试\n\n内容。";
        Path file = tempDir.resolve("doc.md");
        Files.writeString(file, md);

        // contentType = null, 全靠扩展名兜底
        List<Document> docs = reader.read(
                new FileSystemResource(file), "doc.md", null);

        assertFalse(docs.isEmpty(), "应通过 .md 扩展名识别并读取");
    }

    @Test
    @DisplayName("无 contentType 时通过 .txt 扩展名识别为文本")
    void shouldFallbackToExtensionForText(@TempDir Path tempDir) throws IOException {
        String content = "纯文本内容";
        Path file = tempDir.resolve("log.txt");
        Files.writeString(file, content);

        List<Document> docs = reader.read(
                new FileSystemResource(file), "log.txt", null);

        assertFalse(docs.isEmpty(), "应通过 .txt 扩展名识别并读取");
    }
}