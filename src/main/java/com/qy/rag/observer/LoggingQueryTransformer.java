package com.qy.rag.observer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

/**
 * 查询改写日志装饰器
 * 包装 QueryTransformer，打印改写前后的查询内容，便于观察 RAG 管线中间结果
 */
@Slf4j
public class LoggingQueryTransformer implements QueryTransformer {

    private final QueryTransformer delegate;

    public LoggingQueryTransformer(QueryTransformer delegate) {
        this.delegate = delegate;
    }

    @Override
    public Query transform(Query query) {
        log.info("[RAG-查询改写] 原始查询: {}", query.text());
        Query transformed = delegate.transform(query);
        log.info("[RAG-查询改写] 改写结果: {}", transformed.text());
        return transformed;
    }
}
