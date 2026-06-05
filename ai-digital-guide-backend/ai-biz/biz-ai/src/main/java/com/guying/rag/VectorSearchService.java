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
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.7)
                .filterExpression("attractionId == '" + attractionId + "' && type == " + RedisConstants.DOC_TAG )
                .build();

        return vectorStore.similaritySearch(request);
    }
}
