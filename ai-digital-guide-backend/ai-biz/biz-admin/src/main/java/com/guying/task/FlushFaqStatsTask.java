package com.guying.task;

import com.guying.pojo.entity.FaqDailyStats;
import com.guying.service.StatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.guying.common.constants.RedisConstants.HOT_FAQ_KEY;

@Component
@Slf4j
public class FlushFaqStatsTask {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private StatService statService;


    @Scheduled(cron = "0 5 0 * * ?") // 每天凌晨 00:05 执行
    public void flushFaqStatsToDb() {
        // 1. 计算【昨天】的日期字符串
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String yesterdayStr = yesterday.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 2. 用 SCAN 替代 KEYS，避免阻塞 Redis
        Set<String> keys = new HashSet<>();
        try (var cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(HOT_FAQ_KEY + "*:" + yesterdayStr).count(100).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (keys.isEmpty()) return;

        for (String key : keys) {
            // 每个 key 独立处理，单个失败不影响其它景点的落盘
            try {
                String[] parts = key.split(":");
                Long attractionId = Long.valueOf(parts[2]);

                // 3. 获取 ZSet 中所有的 faqId 及其点击得分
                Set<ZSetOperations.TypedTuple<String>> stats =
                        stringRedisTemplate.opsForZSet().rangeWithScores(key, 0, -1);

                if (stats != null && !stats.isEmpty()) {
                    List<FaqDailyStats> dbList = new ArrayList<>();
                    for (ZSetOperations.TypedTuple<String> tuple : stats) {
                        FaqDailyStats entity = new FaqDailyStats();
                        entity.setAttractionId(attractionId);
                        entity.setFaqId(Long.valueOf(tuple.getValue()));
                        entity.setCount(tuple.getScore().intValue());
                        entity.setDate(yesterday);
                        dbList.add(entity);
                    }

                    // 4. 批量插入数据库
                    statService.saveFaqDailyStats(dbList);
                }

                // 5. 落盘成功后，删除这个处理完的 Key
                stringRedisTemplate.delete(key);
            } catch (Exception e) {
                log.error("FAQ每日统计落库失败, key={}", key, e);
            }
        }
    }
}
