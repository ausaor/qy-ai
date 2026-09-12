package com.qy.rag.reader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 多格式文档读取器
 * 根据文件类型自动选择合适的 Spring AI Reader
 */
@Slf4j
@Component
public class MultiFormatDocumentReader {

    /**
     * 读取文档，自动识别格式
     *
     * @param resource    文件资源（本地临时文件）
     * @param filename    原始文件名（用于格式识别与来源元数据）
     * @param contentType MIME 类型（如 application/pdf, text/plain 等）
     * @return 文档列表
     */
    public List<Document> read(Resource resource, String filename, String contentType) {
        log.info("读取文档: {}, MIME: {}", filename, contentType);

        List<Document> documents = switch (resolveType(contentType, filename)) {
            case "pdf" -> readPdf(resource);
            case "markdown" -> readMarkdown(resource);
            case "text" -> readText(resource);
            default -> readWithTika(resource);
        };

        // 为所有文档添加来源元数据
        documents.forEach(doc -> {
            doc.getMetadata().put("source_file", filename);
            doc.getMetadata().put("file_type", resolveType(contentType, filename));
        });

        log.info("文档 {} 解析完成，共 {} 个片段", filename, documents.size());
        return documents;
    }

    private List<Document> readPdf(Resource resource) {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                resource,
                PdfDocumentReaderConfig.builder()
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                        .withPagesPerDocument(1)
                        .build()
        );
        return reader.read();
    }

    private List<Document> readMarkdown(Resource resource) {
        MarkdownDocumentReader reader = new MarkdownDocumentReader(
                resource,
                MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(true)
                        .withIncludeBlockquote(true)
                        .build()
        );
        return reader.read();
    }

    private List<Document> readText(Resource resource) {
        TextReader reader = new TextReader(resource);
        return reader.read();
    }

    private List<Document> readWithTika(Resource resource) {
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        return reader.read();
    }

    /**
     * 根据 MIME 类型和文件名解析实际类型
     */
    private String resolveType(String contentType, String filename) {
        if (contentType == null && filename == null) {
            return "tika";
        }

        // 优先按 MIME 类型判断
        if (contentType != null) {
            return switch (contentType) {
                case "application/pdf" -> "pdf";
                case "text/markdown" -> "markdown";
                case "text/plain" -> "text";
                case "application/msword",
                     "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                     "application/rtf" -> "tika";
                default -> {
                    // 按文件扩展名兜底
                    if (filename != null) {
                        String lower = filename.toLowerCase();
                        if (lower.endsWith(".pdf")) yield "pdf";
                        if (lower.endsWith(".md") || lower.endsWith(".markdown")) yield "markdown";
                        if (lower.endsWith(".txt")) yield "text";
                    }
                    yield "tika";
                }
            };
        }

        // 仅按文件名判断
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "markdown";
        if (lower.endsWith(".txt")) return "text";
        return "tika";
    }

    /**
     * 判断是否为支持的文件类型
     */
    public boolean isSupported(String contentType, String filename) {
        String type = resolveType(contentType, filename);
        return !type.equals("tika") || isTikaSupported(contentType, filename);
    }

    private boolean isTikaSupported(String contentType, String filename) {
        if (contentType != null) {
            return contentType.startsWith("application/") || contentType.startsWith("text/");
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".rtf");
        }
        return false;
    }
}
