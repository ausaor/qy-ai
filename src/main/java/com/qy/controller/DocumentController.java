package com.qy.controller;

import com.qy.annotation.RequireRoles;
import com.qy.enums.ResultCode;
import com.qy.enums.RoleEnum;
import com.qy.result.Result;
import com.qy.result.ResultUtils;
import com.qy.service.IDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 文档控制器
 * 支持 PDF/Word/Markdown/文本等多格式文档上传和问答
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/doc")
public class DocumentController {

    private final IDocumentService documentService;

    /**
     * 查询已索引的文档统计信息
     * 用于确认文档是否已成功写入向量库和 BM25 索引
     */
    @RequestMapping("/stats")
    public Result stats() {
        return ResultUtils.success(documentService.getDocumentStats());
    }

    /**
     * 上传文档（支持多格式）
     */
    @RequireRoles(value = {RoleEnum.SUPER_ADMIN})
    @PostMapping("/upload")
    public Result upload(@RequestParam("file") MultipartFile file) {
        try {
            String contentType = file.getContentType();
            String filename = file.getOriginalFilename();

            // 校验文件类型
            if (!isSupportedType(contentType, filename)) {
                return ResultUtils.error(ResultCode.PROGRAM_ERROR, "不支持的文件格式。支持: PDF、Word、Markdown、文本文件。");
            }

            // Tomcat 会在请求结束后清理 multipart 临时文件，
            // 异步任务仍持有 file.getResource() 时将读到已删除的 .tmp 文件（NoSuchFileException），
            // 因此先把上传内容转存到受控的本地临时文件，再交给异步任务处理
            Path tempFile = createTempFile(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            documentService.writeToVectorStoreAsync(new FileSystemResource(tempFile), filename);
            return ResultUtils.success();
        } catch (Exception e) {
            log.error("上传文档失败", e);
            return ResultUtils.error(ResultCode.PROGRAM_ERROR, "上传文件失败！");
        }
    }

    /**
     * 创建保留原始扩展名的本地临时文件（扩展名用于后续 MIME/格式识别）
     */
    private Path createTempFile(String filename) throws IOException {
        String suffix = ".tmp";
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot > 0 && dot < filename.length() - 1) {
                suffix = filename.substring(dot);
            }
        }
        return Files.createTempFile("qy-ai-upload-", suffix);
    }

    private boolean isSupportedType(String contentType, String filename) {
        if (contentType != null) {
            if (contentType.equals("application/pdf")) return true;
            if (contentType.equals("text/plain")) return true;
            if (contentType.equals("text/markdown")) return true;
            if (contentType.contains("word") || contentType.contains("document")) return true;
            if (contentType.equals("application/rtf")) return true;
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".pdf") || lower.endsWith(".txt")
                    || lower.endsWith(".md") || lower.endsWith(".markdown")
                    || lower.endsWith(".doc") || lower.endsWith(".docx")
                    || lower.endsWith(".rtf");
        }
        return false;
    }
}
