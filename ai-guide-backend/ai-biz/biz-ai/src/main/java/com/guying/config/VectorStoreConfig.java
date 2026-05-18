package com.guying.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

import java.util.List;

@Configuration
public class VectorStoreConfig {

    // 1. 读取 yml 配置文件中的 Redis 信息
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    // 2. 手动接管 JedisPooled 的创建，把密码塞进去
    @Bean
    public JedisPooled jedisPooled() {
        // 参数顺序：host, port, user(这里填null使用默认用户), password
        return new JedisPooled(redisHost, redisPort, null, redisPassword);
    }

    @Bean
    public VectorStore vectorStore(JedisPooled jedisPooled,
                                   EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                // 向量索引算法：HNSW（高性能近似搜索） 或 FLAT（精确但慢）
                .vectorAlgorithm(RedisVectorStore.Algorithm.HNSW)
                // 声明可用于过滤的 metadata 字段
                .metadataFields(
                        MetadataField.tag("attractionId"),
                        MetadataField.tag("fileId"),
                        MetadataField.tag("faqId"),
                        MetadataField.tag("type")
                )
                .initializeSchema(true)
                .batchingStrategy(new TokenCountBatchingStrategy()) // 批量写入优化
                .build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                // Add your custom Chinese and English punctuation marks here
                .withPunctuationMarks(List.of('\n', '\r', '。', '？', '！', '.', '?', '!'))
                .build();
    }
}