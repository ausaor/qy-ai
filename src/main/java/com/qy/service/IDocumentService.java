package com.qy.service;

import org.springframework.core.io.Resource;

import java.util.Map;

public interface IDocumentService {

    /**
     * 同步将文档写入向量库与 BM25 索引
     * 调用方需自行管理临时文件的清理
     *
     * @param resource         本地临时文件资源
     * @param originalFilename 原始文件名（用于 MIME 识别与来源元数据）
     * @return 写入的 chunk 数量
     */
    int writeToVectorStore(Resource resource, String originalFilename);

    /**
     * 异步将文档写入向量库与 BM25 索引
     *
     * @param resource         本地临时文件资源（处理完成后由实现方负责删除）
     * @param originalFilename 原始文件名（用于 MIME 识别与来源元数据）
     */
    void writeToVectorStoreAsync(Resource resource, String originalFilename);

    /**
     * 获取已索引的文档统计信息
     *
     * @return 包含 documentCount 和 sourceFileCount 的 map
     */
    Map<String, Object> getDocumentStats();
}
