package com.guying.task;

import com.guying.pojo.entity.FaqDailyStats;
import com.guying.service.StatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.guying.common.constants.RedisConstants.HOT_FAQ_KEY;

@Component
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
        //获取所有key
        Set<String> keys = stringRedisTemplate.keys(HOT_FAQ_KEY + "*:" + yesterdayStr);
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {

            String[] parts = key.split(":");
            Long attractionId = Long.valueOf(parts[2]);

            // 3. 获取 ZSet 中所有的 faqId 及其点击得分
            Set<ZSetOperations.TypedTuple<String>> stats = stringRedisTemplate.opsForZSet().rangeWithScores(key, 0, -1);

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
        }
    }
}
