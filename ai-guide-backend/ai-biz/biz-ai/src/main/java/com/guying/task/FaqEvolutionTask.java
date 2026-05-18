package com.guying.task;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.guying.common.constants.MqConstants;
import com.guying.message.AttractionFqaMessage;
import com.guying.rag.VectorSearchService;
import com.guying.rag.VectorStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;
import java.util.stream.Collectors;

import static com.guying.common.constants.RedisConstants.HOT_QUESTION_KEY;

@Component
@Slf4j
public class FaqEvolutionTask {
    @Autowired
    @Qualifier("etlChatClient")
    private ChatClient etlChatClient;

    @Autowired
    @Qualifier("expertChatClient")
    private ChatClient expertChatClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private VectorStorageService vectorStorageService;

    @Autowired
    private VectorSearchService vectorSearchService;


    @Scheduled(cron = "0 0 2 * * ?")
    public void execute() {
        log.info("开始执行FAQ进化任务");
        //获取所有热点问题的所属景点Id
        Set<String> keys = redisTemplate.keys(HOT_QUESTION_KEY + "*");
        if(keys == null || keys.isEmpty()){
            log.info("昨日没有热点问题");
            return;
        }
        //遍历所有热点问题的所属景点
        for(String key : keys){
            if (key.endsWith(":processing")) continue;
            //获取景点Id
            String attractionIdStr = key.replace(HOT_QUESTION_KEY, "");
            Long attractionId = Long.valueOf(attractionIdStr);
            String processingKey = key + ":processing";
            // 截流改名，防止新问题乱入
            Boolean renamed = redisTemplate.renameIfAbsent(key, processingKey);
            if (Boolean.FALSE.equals(renamed)) continue;
            Set<String> queries = redisTemplate.opsForSet().members(processingKey);
            log.info("开始处理景点{}的热点问题", attractionId);
            // 如果没有热点问题，则删除处理键并继续下一个景点
            if (queries == null || queries.isEmpty()) {
                redisTemplate.delete(processingKey);
                continue;
            }
            try {
                //聚类问题
                QueryPreClusterer clusterer = new QueryPreClusterer(0.45, 2);
                Map<String, List<String>> preClusteredGroups = clusterer.cluster(queries);
                int savedTokensPercent = (int)((1.0 - (double)preClusteredGroups.size() / queries.size()) * 100);
                log.info("本地预聚类完成：{} 条原始问题被压缩为 {} 个代表意图，为 LLM 节省约 {}% 算力消耗",
                        queries.size(), preClusteredGroups.size(), savedTokensPercent);
                // 获取代表问题
                Set<String> representativeQuestions = preClusteredGroups.keySet();
                log.info("代表问题：{}", representativeQuestions);
                if(representativeQuestions.size() > 50){
                    log.warn("代表问题数量（{}）依然庞大，仅截取前 50 个高频问题送往 LLM 分析", representativeQuestions.size());
                    representativeQuestions = representativeQuestions.stream().limit(50).collect(Collectors.toSet());
                }
                List<String> standardQuestions = extractStandardQuestions(representativeQuestions);
                log.info("LLM 深度语义聚类完成，提炼出 {} 个终极标准问题: {}", standardQuestions.size(), standardQuestions);
                for(String standardQuestion : standardQuestions){
                    log.info("标准问题：{}", standardQuestion);
                    List<Document> existingFaqs = vectorSearchService.searchSimilarQuestion(standardQuestion, attractionId,0.85);
                    if (existingFaqs != null && !existingFaqs.isEmpty()) {
                        try {
                            log.info("已存在相关FAQ，跳过标准问题：{}", standardQuestion);
                            //意图扩充,存入向量库里
                            Document existingFaq = existingFaqs.getFirst();
                            String faqId = existingFaq.getMetadata().get("faqId").toString();
                            vectorStorageService.saveQuestionVectorWithFaqId(standardQuestion, attractionId, faqId);
                            continue;
                        } catch (Exception e) {
                            log.error("处理标准问题 [{}] 时发生异常，跳过该问题继续处理下一个", standardQuestion, e);
                        }
                    }
                    List<Document> docs = vectorSearchService.searchDocByAttraction(standardQuestion, attractionId, 5);
                    if (docs == null || docs.isEmpty()) {
                        log.warn("问题 [{}] 无法在内部文档库中找到相关上下文，放弃生成答案。", standardQuestion);
                        continue;
                    }
                    String context = docs.stream()
                            .map(Document::getText)
                            .collect(Collectors.joining("\n\n"));
                    String stdA = generateStandardAnswer(standardQuestion, context);
                    if ("未找到匹配事实".equals(stdA)) {
                        log.warn("文档库中有相关段落，但无法直接回答问题 [{}]，已跳过。", standardQuestion);
                        continue;
                    }
                    log.info("诞生全新 FAQ！\n标准问题: {}\n标准答案: {}", standardQuestion, stdA);
                    //同时存入数据库和向量数据库
                    Long faqId = IdWorker.getId();
                    vectorStorageService.saveQuestionVectorWithFaqId(standardQuestion, attractionId, faqId.toString());
                    AttractionFqaMessage message = new AttractionFqaMessage(faqId,standardQuestion, stdA, attractionId );
                    rabbitTemplate.convertAndSend(MqConstants.ADMIN_SAVE_FAQ_DIRECT, MqConstants.ADMIN_SAVE_FAQ_ROUTING_KEY, message);
                }
            } catch (Exception e) {
                log.error("景点 [{}] 跑批处理异常", attractionId, e);
            } finally {
                redisTemplate.delete(processingKey);
            }
        }
        log.info("凌晨 FAQ 自进化批处理任务执行完毕。");
    }

    /**
     * 生成标准答案
     * @param standardQuestion
     * @param context
     * @return
     */
    private String generateStandardAnswer(String standardQuestion, String context) {
        String prompt = String.format("""
            请根据以下【景区内部文档】，为游客的【提问】编写一个干瘪、极其客观的【标准答案】。
            要求：不要寒暄，直接给事实。如果文档中无法明确推导出该问题的答案，请严格回复四个字：“未找到匹配事实”。
            
            【提问】：%s
            
            【景区内部文档】：
            %s
            """, standardQuestion, context);

        // 💡 这里调用 expertChatClient！
        return expertChatClient.prompt()
                .user(prompt)
                .call()
                .content().trim();
    }


    /**
     * 从游客提问中提取标准问题
     * @param queries
     * @return
     */
    private List<String> extractStandardQuestions(Set<String> queries) {
        String promptText = "以下是游客今日的零散提问，请找出出现 3 次以上（或者语义高度一致）的共性意图，" +
                "并提炼为标准的书面化提问。\n\n" +
                "游客提问列表：\n" + String.join("\n", queries);

        try {
            List<String> standardQuestions = etlChatClient.prompt()
                    .system("你是一个专业的景区后台数据清洗引擎。严格遵守输出格式。")
                    .user(promptText)
                    .call()
                    .entity(new ParameterizedTypeReference<List<String>>() {});

            return standardQuestions != null ? standardQuestions : Collections.emptyList();

        } catch (Exception e) {
            log.error("结构化提取标准问题失败", e);
            return Collections.emptyList();
        }
    }


    private static class QueryPreClusterer {

        private final double threshold;
        private final int ngramSize;


        QueryPreClusterer(double threshold, int ngramSize) {
            this.threshold = threshold;
            this.ngramSize = ngramSize;
        }

        /**
         * 主入口：将 N 条原始问题聚类为若干组，每组返回一个真实的"代表问题"(群众基础最高的那条)
         *
         * @return key=代表问题, value=该组所有原始问题（方便后续日志/审计）
         */
        Map<String, List<String>> cluster(Set<String> rawQueries) {
            if (rawQueries == null || rawQueries.isEmpty()) {
                return new LinkedHashMap<>();
            }

            // Step 1: 规范化并统计频次（绝对保留热度权重）
            Map<String, Integer> freqMap = new HashMap<>();
            // 映射关系：规范化后的短句 -> 属于该短句的所有原始提问
            Map<String, List<String>> rawGroupMap = new HashMap<>();

            for (String raw : rawQueries) {
                if (raw == null) continue;
                String norm = normalize(raw);
                if (norm.length() < 2) continue; // 过滤掉太短的无意义噪点（比如单个字）

                freqMap.put(norm, freqMap.getOrDefault(norm, 0) + 1);
                rawGroupMap.computeIfAbsent(norm, k -> new ArrayList<>()).add(raw);
            }

            // 按真实热度（频次）从高到低排序，确立“话语权”
            List<String> sortedNorms = freqMap.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();

            // Step 2: 贪心聚类（阻断语义传染）
            // 记录：簇中心(规范化句子) -> 该簇包含的所有原始问题
            Map<String, List<String>> clusters = new LinkedHashMap<>();

            for (String norm : sortedNorms) {
                boolean foundCenter = false;
                // 尝试加入已有的高频簇
                for (String center : clusters.keySet()) {
                    if (jaccard(norm, center) >= threshold) {
                        clusters.get(center).addAll(rawGroupMap.get(norm));
                        foundCenter = true;
                        break;
                    }
                }
                // 找不到相似的簇，自己原地建一个新簇，成为新中心
                if (!foundCenter) {
                    clusters.put(norm, new ArrayList<>(rawGroupMap.get(norm)));
                }
            }

            // Step 3: 提取结果，挑选最原汁原味的“高频代表问题”
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : clusters.entrySet()) {
                List<String> originalQuestions = entry.getValue();
                // 因为是按降序遍历建簇的，这个 List 里的第一批数据，必然是该簇频次最高的那种原始问法
                // 直接取它的原话作为送给 LLM 的代表，极度精准
                String representative = originalQuestions.getFirst();
                result.put(representative, originalQuestions);
            }

            return result;
        }

        // ---------- 规范化 ----------
        private String normalize(String s) {
            return s
                    // 去掉常见语气词、标点、特殊符号、空格
                    .replaceAll("[啊呀哦嗯吧呢吗呗哈嘿？?！!，,、。.；;：:\\s]+", "")
                    // 统一全角/半角数字和字母
                    .replace("０","0").replace("１","1").replace("２","2")
                    .replace("３","3").replace("４","4").replace("５","5")
                    .replace("６","6").replace("７","7").replace("８","8").replace("９","9")
                    .toLowerCase();
        }

        // ---------- 字符 n-gram Jaccard ----------
        private double jaccard(String a, String b) {
            Set<String> ngA = ngrams(a);
            Set<String> ngB = ngrams(b);
            if (ngA.isEmpty() && ngB.isEmpty()) return 1.0;
            if (ngA.isEmpty() || ngB.isEmpty()) return 0.0;

            long intersection = ngA.stream().filter(ngB::contains).count();
            long union = ngA.size() + ngB.size() - intersection;
            return (double) intersection / union;
        }

        private Set<String> ngrams(String s) {
            Set<String> result = new HashSet<>();
            for (int i = 0; i <= s.length() - ngramSize; i++) {
                result.add(s.substring(i, i + ngramSize));
            }
            return result;
        }
    }
}


