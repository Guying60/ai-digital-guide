package com.guying.rag;

import com.guying.common.constants.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class VectorStorageService {

    private final VectorStore vectorStore;
    /**
     * 1. 存入向量库
     */
    public void saveQuestionVectorWithFaqId(String newQuestion, Long attractionId, String faqId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("faqId", faqId);
        metadata.put("attractionId", String.valueOf(attractionId));
        metadata.put("type", RedisConstants.FAQ_TAG);

        Document newFaqDoc = new Document(newQuestion, metadata);
        vectorStore.add(List.of(newFaqDoc));
        log.info("意图扩充成功：新问法 [{}] 已存入向量库并绑定 faqId: {}", newQuestion, faqId);
    }

    /**
     * 2. 大文档入库：分批次安全存入文档切片
     */
    public void saveDocumentChunks(List<Document> chunks, String fileName) {
        int batchSize = 10;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(chunks.size(), i + batchSize);
            List<Document> batch = chunks.subList(i, end);
            try {
                vectorStore.add(batch);
                log.info("成功入库第 {} 到 {} 块切片, 文件: {}", i + 1, end, fileName);
            } catch (Exception e) {
                log.error("向量化第 {} 到 {} 块切片时失败！文件: {}", i + 1, end, fileName, e);
                throw e; // 向上抛出异常，触发外层 MQ 消费者的重试或兜底
            }
        }
    }

    /**
     * 3. 删除向量库中的文档切片
     * @param docIds
     */
    public void deleteDocumentChunk(List<String> docIds) {
        // 守卫：docIds 为空时直接返回，避免 vectorStore.delete(null) 触发 NPE 逃逸，
        // 掩盖 catch 块中后续的状态回写逻辑
        if (docIds == null || docIds.isEmpty()) {
            log.warn("docIds 为空，跳过删除向量库文档切片");
            return;
        }
        vectorStore.delete(docIds);
        log.info("成功删除向量库中的文档切片: {}", docIds);
    }
}
