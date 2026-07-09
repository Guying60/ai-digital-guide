package com.guying.mq.consumer;

import com.guying.attractions.dto.AttractionDocumentDTO;
import com.guying.attractions.service.AdminAttractionsInternalService;
import com.guying.common.constants.MqConstants;
import com.guying.common.constants.RedisConstants;
import com.guying.common.enums.TaskStatusEnum;
import com.guying.message.MarkDownDocumentMessage;
import com.guying.rag.MarkdownTableSplitter;
import com.guying.rag.VectorStorageService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.guying.common.constants.MqConstants.DLQ_QUEUE;

@Component
@Slf4j
public class DocumentVectorListener {
    @Autowired
    private AdminAttractionsInternalService attractionsInternalService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private VectorStorageService vectorStorageService;




    @RabbitListener(queues = MqConstants.RESULT_QUEUE)
    public void saveDocumentVector(MarkDownDocumentMessage msg) {
        List<String> docIds = null;
        try {
            if (!msg.isSuccess()) {
                log.error("文档解析失败，文档名称：{}", msg.getFileName());
                markFailed(msg);
                return;
            }

            String markdownText = msg.getMarkdownText();
            Resource mdResource = new ByteArrayResource(markdownText.getBytes(StandardCharsets.UTF_8));
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .build();
            MarkdownDocumentReader mdReader = new MarkdownDocumentReader(mdResource, config);
            List<Document> semanticDocs = mdReader.get();
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(800)
                    .withMinChunkSizeChars(350)
                    .withMaxNumChunks(10000)
                    .withKeepSeparator(true)
                    .build();
            List<Document> finalChunks = new ArrayList<>();
            for (Document doc : semanticDocs) {
                String content = doc.getText();
                if (content.contains("|---") || content.contains("| ---")) {
                    // 表格块：小表保留原样，大表按行切分（表头复用），保证不超 embedding token 上限
                    finalChunks.addAll(MarkdownTableSplitter.split(
                            doc, MarkdownTableSplitter.MAX_TABLE_CHUNK_CHARS, splitter));
                } else {
                    finalChunks.addAll(splitter.apply(List.of(doc))); // 纯文本，允许切碎
                }
            }
            finalChunks.forEach(doc -> {
                doc.getMetadata().put("attractionId", String.valueOf(msg.getAttractionId()));
                doc.getMetadata().put("fileName", msg.getFileName());
                doc.getMetadata().put("type", RedisConstants.DOC_TAG);
            });
            // 提前计算 docIds：即便后续入库失败，catch 块也能据此清理已写入的脏数据
            docIds = finalChunks.stream().map(Document::getId).toList();
            vectorStorageService.saveDocumentChunks(finalChunks, msg.getFileName());

            // 存入数据库MySQL
            attractionsInternalService.saveDocumentToMySql(
                    new AttractionDocumentDTO(msg.getOssUrl(), msg.getFileName(), msg.getFileType(), docIds, msg.getAttractionId(), msg.getAdminId())
            );
            stringRedisTemplate.opsForValue().set(RedisConstants.FILE_PARSING_KEY + msg.getTaskId(), TaskStatusEnum.SUCCESS.toString(), 10, TimeUnit.MINUTES);
            log.info("保存文档向量成功，文档名称：{}", msg.getFileName());
        } catch (Exception e) {
            // 兜底：先回写 FAILED 状态（最关键，保证前端能感知失败），再清理脏数据。
            // 清理单独 try-catch，避免其自身异常再次掩盖状态回写。
            log.error("解析文档出错, fileName={}, docIds={}", msg.getFileName(), docIds, e);
            markFailed(msg);
            try {
                vectorStorageService.deleteDocumentChunk(docIds);
            } catch (Exception ex) {
                log.error("清理脏数据失败, fileName={}, docIds={}", msg.getFileName(), docIds, ex);
            }
        }
    }

    /**
     * 将文件解析状态回写为 FAILED。
     * 抽出公共方法，确保 {@code msg.isSuccess()==false} 与异常分支都走到，避免状态卡在 PROCESSING。
     */
    private void markFailed(MarkDownDocumentMessage msg) {
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisConstants.FILE_PARSING_KEY + msg.getTaskId(),
                    TaskStatusEnum.FAILED.toString(), 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("回写 FAILED 状态失败, taskId={}", msg.getTaskId(), e);
        }
    }


    @RabbitListener(queues = MqConstants.DELETE_VECTOR_QUEUE)
    public void deleteDocument(List<String> docIds) {
        log.info("收到删除向量文档id: {}", docIds);
        vectorStorageService.deleteDocumentChunk(docIds);
        log.info("删除向量文档id: {}", docIds);
    }

    @RabbitListener(queues = DLQ_QUEUE)
    public void handleDeadLetter(Message message, Channel channel) {
        // 1. 解析出是哪个文档转换失败了
        String failedTask = new String(message.getBody());

        log.error("发现死信任务，需人工介入: {}", failedTask);

    }
}
