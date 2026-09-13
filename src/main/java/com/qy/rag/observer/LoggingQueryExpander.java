package com.qy.rag.observer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;

import java.util.List;

/**
 * 查询扩展日志装饰器
 * 包装 QueryExpander，打印扩展后的所有查询变体，便于观察 RAG 管线中间结果
 */
@Slf4j
public class LoggingQueryExpander implements QueryExpander {

    private final QueryExpander delegate;

    public LoggingQueryExpander(QueryExpander delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Query> expand(Query query) {
        log.info("[RAG-查询扩展] 输入查询: {}", query.text());
        List<Query> expanded = delegate.expand(query);
        for (int i = 0; i < expanded.size(); i++) {
            log.info("[RAG-查询扩展] 变体[{}]: {}", i, expanded.get(i).text());
        }
        return expanded;
    }
}
