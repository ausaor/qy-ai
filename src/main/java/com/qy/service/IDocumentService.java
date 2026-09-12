package com.qy.service;

import org.springframework.core.io.Resource;

public interface IDocumentService {

    /**
     * 异步将文档写入向量库与 BM25 索引
     *
     * @param resource         本地临时文件资源（处理完成后由实现方负责删除）
     * @param originalFilename 原始文件名（用于 MIME 识别与来源元数据）
     */
    void writeToVectorStoreAsync(Resource resource, String originalFilename);
}
