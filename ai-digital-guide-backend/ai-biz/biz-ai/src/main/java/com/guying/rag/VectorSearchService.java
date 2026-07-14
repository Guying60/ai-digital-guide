package com.guying.rag;

import com.guying.common.constants.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VectorSearchService {


    private final VectorStore vectorStore;

    public List<Document> searchSimilarQuestion(String query, Long attractionId,double similarityThreshold) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(1)
                .similarityThreshold(similarityThreshold)
                .filterExpression("attractionId == '" + attractionId + "' && type == " + RedisConstants.FAQ_TAG)
                .build();

        return vectorStore.similaritySearch(request);
    }

    public List<Document> searchDocByAttraction(String query, Long attractionId, int topK) {
        return searchDocByAttraction(query, attractionId, topK, 0.7);
    }

    /**
     * 按指定相似度阈值检索某景点的 DOC 资料。
     * 供兜底降级使用：当默认阈值(0.7)检索为空时，调用方可传更低的阈值(或负值表示不限阈值)
     * 重新召回，避免因 query 个性化字段劣化(如 interests 未设置)导致整条路线生成中断。
     * @param similarityThreshold 相似度阈值；传负值则不设阈值(召回 topK 篇)
     */
    public List<Document> searchDocByAttraction(String query, Long attractionId, int topK, double similarityThreshold) {
        SearchRequest.Builder b = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression("attractionId == '" + attractionId + "' && type == " + RedisConstants.DOC_TAG);
        if (similarityThreshold >= 0) {
            b.similarityThreshold(similarityThreshold);
        }
        SearchRequest request = b.build();

        return vectorStore.similaritySearch(request);
    }
}
