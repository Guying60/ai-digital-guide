package com.guying.mq.consumer;

import com.guying.attractions.dto.AttractionDocumentDTO;
import com.guying.attractions.service.AdminAttractionsInternalService;
import com.guying.common.constants.MqConstants;
import com.guying.common.constants.RedisConstants;
import com.guying.common.enums.TaskStatusEnum;
import com.guying.message.MarkDownDocumentMessage;
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
                    log.debug("表格块保留, tokens≈{}, fileName={}",
                            content.length() / 2, msg.getFileName());
                    finalChunks.add(doc); // 是表格，受保护，整块保留！
                } else {
                    finalChunks.addAll(splitter.apply(List.of(doc))); // 纯文本，允许切碎
                }
            }
            finalChunks.forEach(doc -> {
                doc.getMetadata().put("attractionId", String.valueOf(msg.getAttractionId()));
                doc.getMetadata().put("fileName", msg.getFileName());
                doc.getMetadata().put("type", RedisConstants.DOC_TAG);
            });
            vectorStorageService.saveDocumentChunks(finalChunks, msg.getFileName());

            // 存入数据库MySQL
            docIds = finalChunks.stream().map(Document::getId).toList();
            attractionsInternalService.saveDocumentToMySql(
                    new AttractionDocumentDTO(msg.getOssUrl(), msg.getFileName(), msg.getFileType(), docIds, msg.getAttractionId(), msg.getAdminId())
            );
            stringRedisTemplate.opsForValue().set(RedisConstants.FILE_PARSING_KEY + msg.getTaskId(), TaskStatusEnum.SUCCESS.toString(), 10, TimeUnit.MINUTES);
            log.info("保存文档向量成功，文档名称：{}", msg.getFileName());
        } catch (Exception e) {
            //完整兜底，删除脏数据
            vectorStorageService.deleteDocumentChunk(docIds);
            log.error("docIds:{}", docIds);
            stringRedisTemplate.opsForValue().set(RedisConstants.FILE_PARSING_KEY + msg.getTaskId(), TaskStatusEnum.FAILED.toString(), 10, TimeUnit.MINUTES);
            log.error("解析文档出错,{}", e.getMessage());
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
