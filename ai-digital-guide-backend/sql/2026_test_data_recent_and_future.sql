-- ============================================================================
-- 测试数据：覆盖「最近一个月 + 未来一个月」
--
-- 景点/管理员/用户沿用旧测试脚本的 ID：
--   attractionId = 2046935279750139906  (故宫博物院)
--   adminId      = 2073348474148573186  (testadmin)
--   userId       = 1001 ~ 1010          (testuser01 ~ testuser10)
--
-- 时间窗口（以执行日 CURDATE() 为锚）：
--   游览记录 / 评价            : [今天-30, 今天+30]  → 近1月已发生 + 未来1月待发生
--   FAQ每日命中 / 体验分析 / 面部表情 : [今天-30, 今天+30]  → 同上
--
-- ⚠️ 关于「未来日期」对仪表盘的影响（务必读）：
--   后端聚合查询大多形如 create_time >= DATE_SUB(CURDATE(), INTERVAL days DAY)
--   且【没有上界】，因此未来日期的行会被一起计入 total / 正面率 / 热点FAQ 等
--   「汇总卡片」。趋势折线不受影响（前端横轴只取过去 N 天，未来日期会被丢弃）。
--   - tb_user_review 已用 status=0(待评价) 标记未来记录，满意度趋势过滤 status=1，不受影响。
--   - 若只想看「干净的近月汇总」，执行文末【可选清理：删除未来日期数据】片段即可。
--
-- 幂等：脚本开头按 ID 范围/景点删除旧测试数据后再插入，可重复执行。
-- 依赖：MySQL 8+ 的递归 CTE 与 RAND()。顶部已 SET cte_max_recursion_depth = 2000。
-- ============================================================================

SET cte_max_recursion_depth = 2000;

SET @attraction_id = 2046935279750139906;
SET @admin_id      = 2073348474148573186;
SET @today         = CURDATE();
SET @past_start    = DATE_SUB(@today, INTERVAL 30 DAY);   -- 最近一个月起点
SET @future_end    = DATE_ADD(@today,  INTERVAL 30 DAY);  -- 未来一个月终点

-- ============================================
-- 0. 清理已有测试数据（可重复执行）
-- ============================================
DELETE FROM tb_user_tour_history     WHERE id BETWEEN 710000000 AND 710002999;
DELETE FROM tb_user_review           WHERE id BETWEEN 720000000 AND 720000999;
DELETE FROM tb_ai_experience_analysis WHERE attraction_id = @attraction_id AND user_id BETWEEN 1001 AND 1010;
DELETE FROM tb_face_emotion_record   WHERE attraction_id = @attraction_id AND user_id BETWEEN 1001 AND 1010;
DELETE FROM tb_faq_daily_stats       WHERE attraction_id = @attraction_id AND `date` BETWEEN @past_start AND @future_end;
DELETE FROM tb_attraction_faq        WHERE attraction_id = @attraction_id AND id BETWEEN 60001 AND 60010;
DELETE FROM tb_attraction_document   WHERE attraction_id = @attraction_id AND admin_id = @admin_id;
DELETE FROM tb_digital_human         WHERE attraction_id = @attraction_id AND admin_id = @admin_id;
DELETE FROM tb_ai_service_suggestion  WHERE attraction_id = @attraction_id;
DELETE FROM SPRING_AI_CHAT_MEMORY     WHERE conversation_id LIKE CONCAT(@attraction_id, ':%');

-- ============================================
-- 1. tb_admin / 2. tb_attraction / 3. tb_user（基础数据，INSERT IGNORE）
-- ============================================
INSERT IGNORE INTO tb_admin (id, username, password) VALUES
(@admin_id, 'testadmin', '123456');

INSERT IGNORE INTO tb_attraction (id, attraction_name, cover_url, type,
    longitude, latitude, province, city, district, adcode,
    admin_id, create_time, update_time) VALUES
(@attraction_id, '故宫博物院', 'https://oss-cn-guangzhou.aliyuncs.com/guying60/cover/gugong.jpg', 4,
    116.397026, 39.916345, '北京市', '北京市', '东城区', '110101',
    @admin_id, NOW(), NOW());

INSERT IGNORE INTO tb_user (id, username, password, avatar_url, gender, age, nickname, create_time, update_time) VALUES
(1001, 'testuser01', '123456', NULL, 1, 25, '旅行达人小王',  NOW(), NOW()),
(1002, 'testuser02', '123456', NULL, 0, 30, '文艺小张',      NOW(), NOW()),
(1003, 'testuser03', '123456', NULL, 1, 28, '背包客老李',    NOW(), NOW()),
(1004, 'testuser04', '123456', NULL, 2, 22, '摄影爱好者',    NOW(), NOW()),
(1005, 'testuser05', '123456', NULL, 0, 35, '带娃妈妈',      NOW(), NOW()),
(1006, 'testuser06', '123456', NULL, 1, 27, '历史迷小赵',    NOW(), NOW()),
(1007, 'testuser07', '123456', NULL, 2, 40, '退休老陈',      NOW(), NOW()),
(1008, 'testuser08', '123456', NULL, 1, 33, '吃货一枚',      NOW(), NOW()),
(1009, 'testuser09', '123456', NULL, 0, 29, '自由行阿琳',    NOW(), NOW()),
(1010, 'testuser10', '123456', NULL, 1, 26, '周末出游',      NOW(), NOW());

-- ============================================
-- 4. tb_attraction_faq（景点常见问答，id 60001~60010）
-- ============================================
INSERT INTO tb_attraction_faq (id, question, answer, attraction_id, create_time, update_time) VALUES
(60001, '故宫的开放时间是什么时候？', '旺季（4月-10月）8:30-17:00，淡季（11月-3月）8:30-16:30，周一闭馆（法定节假日除外）。', @attraction_id, NOW(), NOW()),
(60002, '故宫门票多少钱？',         '旺季成人票60元，淡季成人票40元。学生票半价，60岁以上老人凭证半价。',                  @attraction_id, NOW(), NOW()),
(60003, '故宫怎么预约？',           '请通过"故宫博物院"官方微信公众号或官网提前预约，现场不售票。建议提前7天预约。',          @attraction_id, NOW(), NOW()),
(60004, '故宫附近有停车场吗？',     '故宫午门、神武门外均设有停车场，但车位紧张，建议乘坐地铁1号线到天安门东站出行。',        @attraction_id, NOW(), NOW()),
(60005, '故宫有没有讲解服务？',     '我们提供AI智能语音导览、人工讲解和自助讲解器三种方式，您可以在入口处租用。',              @attraction_id, NOW(), NOW()),
(60006, '故宫内可以拍照吗？',       '文物展厅内请勿使用闪光灯拍照，室外区域可以自由拍摄。部分特展禁止拍照请留意现场提示。',      @attraction_id, NOW(), NOW()),
(60007, '故宫有餐厅吗？',           '故宫内设有故宫餐厅和冰窖餐厅，提供简餐和特色小吃，也可自带干粮在指定区域用餐。',          @attraction_id, NOW(), NOW()),
(60008, '故宫轮椅/婴儿车能推进去吗？','可以，故宫主要通道均具备无障碍通行条件，入口处可免费借用轮椅和婴儿车（数量有限）。',      @attraction_id, NOW(), NOW()),
(60009, '故宫游览需要多长时间？',   '建议预留3-4小时游览中轴线主要宫殿，深度游览需要6小时以上。',                            @attraction_id, NOW(), NOW()),
(60010, '故宫可以寄存行李吗？',     '午门入口处设有免费行李寄存处，大件行李可寄存并在神武门出口处取回。',                      @attraction_id, NOW(), NOW());

-- ============================================
-- 5. tb_attraction_document（上传的文档记录，id AUTO_INCREMENT）
-- ============================================
INSERT INTO tb_attraction_document (oss_url, file_name, file_type, doc_ids, create_time, update_time, attraction_id, admin_id) VALUES
('https://oss-cn-guangzhou.aliyuncs.com/guying60/docs/gugong_intro.md',      '故宫介绍.md',       'md',  '["doc_gugong_001","doc_gugong_002"]', NOW(), NOW(), @attraction_id, @admin_id),
('https://oss-cn-guangzhou.aliyuncs.com/guying60/docs/gugong_history.md',    '故宫历史沿革.md',   'md',  '["doc_gugong_003"]',                  NOW(), NOW(), @attraction_id, @admin_id),
('https://oss-cn-guangzhou.aliyuncs.com/guying60/docs/gugong_guide.pdf',     '游览指南.pdf',      'pdf', '["doc_gugong_004","doc_gugong_005"]', NOW(), NOW(), @attraction_id, @admin_id),
('https://oss-cn-guangzhou.aliyuncs.com/guying60/docs/gugong_collection.md', '馆藏文物介绍.md',   'md',  '["doc_gugong_006"]',                  NOW(), NOW(), @attraction_id, @admin_id),
('https://oss-cn-guangzhou.aliyuncs.com/guying60/docs/gugong_faq_extra.md',  '常见问题补充.md',   'md',  '["doc_gugong_007"]',                  NOW(), NOW(), @attraction_id, @admin_id);

-- ============================================
-- 6. tb_digital_human（数字人配置：video_url/audio_url 拆分版）
-- ============================================
INSERT INTO tb_digital_human (id, video_url, audio_url, attraction_id, admin_id, create_time, update_time) VALUES
(80001, 'https://oss-cn-guangzhou.aliyuncs.com/guying60/digital_human/gugong_host.mp4',
        'https://oss-cn-guangzhou.aliyuncs.com/guying60/digital_human/gugong_voice.wav',
        @attraction_id, @admin_id, NOW(), NOW());

-- ============================================
-- 7. tb_faq_daily_stats（FAQ 每日命中统计：61天 × 10条FAQ = 610行）
--    日期由外层 DATE_ADD(@past_start, INTERVAL seq.idx DAY) 现算，避免列别名
-- ============================================
INSERT INTO tb_faq_daily_stats (faq_id, attraction_id, `date`, count)
WITH RECURSIVE seq(idx) AS (
    SELECT 0
    UNION ALL
    SELECT idx + 1 FROM seq WHERE idx < 60
)
SELECT f.id,
       @attraction_id,
       DATE_ADD(@past_start, INTERVAL seq.idx DAY),
       CASE f.id
           WHEN 60001 THEN 40 + FLOOR(RAND()*30)   -- 开放时间（高频）
           WHEN 60002 THEN 35 + FLOOR(RAND()*25)   -- 门票价格
           WHEN 60003 THEN 25 + FLOOR(RAND()*20)   -- 预约方式
           WHEN 60004 THEN 12 + FLOOR(RAND()*15)   -- 停车场
           WHEN 60005 THEN 20 + FLOOR(RAND()*18)   -- 讲解服务
           WHEN 60006 THEN 18 + FLOOR(RAND()*15)   -- 拍照
           WHEN 60007 THEN 15 + FLOOR(RAND()*12)   -- 餐厅
           WHEN 60008 THEN  8 + FLOOR(RAND()*10)   -- 轮椅/婴儿车
           WHEN 60009 THEN 22 + FLOOR(RAND()*16)   -- 游览时长
           WHEN 60010 THEN 10 + FLOOR(RAND()*10)   -- 行李寄存
       END
FROM seq
JOIN tb_attraction_faq f ON f.attraction_id = @attraction_id AND f.id BETWEEN 60001 AND 60010;

-- ============================================
-- 8. tb_user_tour_history（游览记录：每天服务次数随机 12~35 次，不再固定20条）
--    先按天生成随机当日次数 cnt，再用 nums 展开成行，日期 = @past_start + day_idx 天
-- ============================================
INSERT INTO tb_user_tour_history (id, user_id, attraction_id, conversation_id, create_time,
    attraction_name, cover_url, type, city, message_count, update_time)
WITH RECURSIVE days(idx, r) AS (
    SELECT 0, RAND()
    UNION ALL
    SELECT idx + 1, RAND() FROM days WHERE idx < 60
),
day_counts AS (
    SELECT idx, (12 + FLOOR(r * 24)) AS cnt FROM days       -- 每天 12 ~ 35 次
),
nums(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM nums WHERE n < 34
),
rows_cte AS (
    SELECT d.idx AS day_idx, RAND() AS r1, RAND() AS r2, RAND() AS r3, RAND() AS r4, RAND() AS r5
    FROM day_counts d
    JOIN nums ON nums.n < d.cnt
),
numbered AS (
    SELECT day_idx, r1, r2, r3, r4, r5,
           ROW_NUMBER() OVER (ORDER BY day_idx, r1) AS rn
    FROM rows_cte
)
SELECT
    710000000 + rn,
    1001 + FLOOR(r1 * 10),
    @attraction_id,
    CONCAT(@attraction_id, ':', 1001 + FLOOR(r1 * 10), ':', DATE_FORMAT(DATE_ADD(@past_start, INTERVAL day_idx DAY), '%Y%m%d'), rn),
    DATE_ADD(@past_start, INTERVAL day_idx DAY) + INTERVAL FLOOR(r2 * 24) HOUR + INTERVAL FLOOR(r3 * 60) MINUTE,
    '故宫博物院',
    'https://oss-cn-guangzhou.aliyuncs.com/guying60/cover/gugong.jpg',
    4, '北京市',
    (4 + FLOOR(r4 * 28)) * 2,                                   -- 8 ~ 60
    DATE_ADD(@past_start, INTERVAL day_idx DAY) + INTERVAL FLOOR(r2 * 24) HOUR + INTERVAL FLOOR(r3 * 60) MINUTE
        + INTERVAL (5 + FLOOR(r5 * 40)) MINUTE
FROM numbered;

-- ============================================
-- 9. tb_user_review（游客评价：600行，每天10条）
--    近1月：status=1 已评价、rating 1~5；未来1月：status=0 待评价、rating NULL
--    满意度趋势只取 status=1 AND rating IS NOT NULL，未来记录不污染仪表盘
-- ============================================
INSERT INTO tb_user_review (id, user_id, attraction_id, conversation_id, rating, content, tags,
    status, create_time, update_time, is_deleted)
WITH RECURSIVE seq(idx, r1, r2, r3, r4, r5) AS (
    SELECT 0, RAND(), RAND(), RAND(), RAND(), RAND()
    UNION ALL
    SELECT idx + 1, RAND(), RAND(), RAND(), RAND(), RAND() FROM seq WHERE idx < 599
)
SELECT
    720000000 + seq.idx,
    1001 + FLOOR(seq.r1 * 10),
    @attraction_id,
    CONCAT(@attraction_id, ':', 1001 + FLOOR(seq.r1 * 10), ':', DATE_FORMAT(DATE_ADD(@past_start, INTERVAL FLOOR(seq.idx / 10) DAY), '%Y%m%d'), seq.idx),
    CASE
        WHEN DATE_ADD(@past_start, INTERVAL FLOOR(seq.idx / 10) DAY) > @today THEN NULL
        WHEN seq.r4 < 0.10 THEN 1
        WHEN seq.r4 < 0.20 THEN 2
        WHEN seq.r4 < 0.35 THEN 3
        WHEN seq.r4 < 0.65 THEN 4
        ELSE 5
    END,
    CASE
        WHEN DATE_ADD(@past_start, INTERVAL FLOOR(seq.idx / 10) DAY) > @today THEN NULL
        WHEN seq.r4 < 0.20 THEN ELT(1 + FLOOR(seq.r5 * 10),
            '排队太久体验很差','预约系统太慢耽误行程','卫生间卫生状况堪忧','餐厅饭菜质量堪忧',
            '纪念品价格偏高','导览设备经常没电','无障碍设施不足','工作人员态度一般',
            '人流拥挤影响观感','标识指引不清晰')
        ELSE ELT(1 + FLOOR(seq.r5 * 10),
            '龙椅讲解非常精彩','国宝级展品令人震撼','志愿者讲解很有意思','无障碍路线很方便',
            '整体体验不错值得推荐','AI导览很智能贴心','游览动线设计合理','文物保存非常用心',
            '环境整洁服务热情','值得二刷的景点')
    END,
    CASE
        WHEN DATE_ADD(@past_start, INTERVAL FLOOR(seq.idx / 2) DAY) > @today THEN NULL
        ELSE ELT(1 + FLOOR(seq.r1 * 5),
            '["讲解服务","环境设施"]','["票务流程","排队体验"]','["餐饮服务","价格合理性"]',
            '["无障碍设施","亲子友好"]','["展品质量","动线设计"]')
    END,
    CASE WHEN DATE_ADD(@past_start, INTERVAL FLOOR(seq.idx / 10) DAY) > @today THEN 0 ELSE 1 END,
    DATE_ADD(@past_start, INTERVAL FLOOR(seq.idx / 10) DAY) + INTERVAL FLOOR(seq.r2 * 24) HOUR + INTERVAL FLOOR(seq.r3 * 60) MINUTE,
    DATE_ADD(@past_start, INTERVAL FLOOR(seq.idx / 10) DAY) + INTERVAL FLOOR(seq.r2 * 24) HOUR + INTERVAL FLOOR(seq.r3 * 60) MINUTE,
    0
FROM seq;

-- ============================================
-- 10. tb_ai_experience_analysis（AI 情感/关注点分析：720行，每天12条）
--     emotion: 0=正面 1=中性 2=负面   focus: 0~8（见 FocusEnum）
--     message：仅负面(emotion=2)填充原话，供负面样本查询直接取用
-- ============================================
INSERT INTO tb_ai_experience_analysis (focus, emotion, message, create_time, conversation_id, user_id, attraction_id)
WITH RECURSIVE seq(idx, r1, r2, r3, r4, r5) AS (
    SELECT 0, RAND(), RAND(), RAND(), RAND(), RAND()
    UNION ALL
    SELECT idx + 1, RAND(), RAND(), RAND(), RAND(), RAND() FROM seq WHERE idx < 719
)
SELECT
    FLOOR(seq.r4 * 9),                                              -- focus 0~8
    CASE WHEN seq.r5 < 0.55 THEN 0 WHEN seq.r5 < 0.80 THEN 1 ELSE 2 END,  -- emotion
    CASE
        WHEN seq.r5 >= 0.80 THEN ELT(1 + FLOOR(seq.r3 * 12),
            '排队排了一个小时才进来，预约系统也太慢了吧',
            '冰窖餐厅的炸酱面太咸了，价格也不便宜',
            '厕所太脏了，保洁阿姨去哪了',
            '纪念品商店的东西太贵了，外面十块钱的书签这里卖五十',
            '故宫太大了，从午门走到神武门腿都快断了',
            '导览设备租了半天就没电，体验很差',
            '节假日人挤人，根本没法好好看展品',
            '工作人员态度冷漠，问路都不耐烦',
            '无障碍通道找不到，带老人很不方便',
            '部分展厅灯光太暗，文物看不清',
            '钟表馆表演时间太短，没看够',
            '御花园休息座椅太少，走累了没地方坐')
        ELSE NULL
    END,
    DATE_ADD(@past_start, INTERVAL FLOOR(seq.idx / 12) DAY) + INTERVAL FLOOR(seq.r1 * 24) HOUR + INTERVAL FLOOR(seq.r2 * 60) MINUTE,
    CONCAT(@attraction_id, ':', 1001 + FLOOR(seq.r4 * 10), ':', DATE_FORMAT(DATE_ADD(@past_start, INTERVAL FLOOR(seq.idx / 12) DAY), '%Y%m%d'), seq.idx),
    1001 + FLOOR(seq.r4 * 10),
    @attraction_id
FROM seq;

-- ============================================
-- 11. tb_face_emotion_record（面部表情分析：540行，每天9条）
--     expression: 0喜悦 1惊讶 2中性 3困惑 4厌恶 5愤怒 6悲伤
-- ============================================
INSERT INTO tb_face_emotion_record (user_id, attraction_id, conversation_id, expression, confidence, detail, create_time)
WITH RECURSIVE seq(idx, r1, r2, r3, r4) AS (
    SELECT 0, RAND(), RAND(), RAND(), RAND()
    UNION ALL
    SELECT idx + 1, RAND(), RAND(), RAND(), RAND() FROM seq WHERE idx < 539
)
SELECT
    1001 + FLOOR(seq.r1 * 10),
    @attraction_id,
    CONCAT(@attraction_id, ':', 1001 + FLOOR(seq.r1 * 10), ':', DATE_FORMAT(DATE_ADD(@past_start, INTERVAL FLOOR(seq.idx / 9) DAY), '%Y%m%d'), seq.idx),
    CASE
        WHEN seq.r2 < 0.35 THEN 0   -- 喜悦
        WHEN seq.r2 < 0.55 THEN 2   -- 中性
        WHEN seq.r2 < 0.70 THEN 1   -- 惊讶
        WHEN seq.r2 < 0.80 THEN 3   -- 困惑
        WHEN seq.r2 < 0.88 THEN 4   -- 厌恶
        WHEN seq.r2 < 0.94 THEN 5   -- 愤怒
        ELSE 6                       -- 悲伤
    END,
    ROUND(0.60 + seq.r3 * 0.39, 2),
    CONCAT('{"reason":"', ELT(1 + FLOOR(seq.r4 * 5), '表情自然','微笑','略显疲惫','皱眉','面无表情'), '"}'),
    DATE_ADD(@past_start, INTERVAL FLOOR(seq.idx / 9) DAY) + INTERVAL FLOOR(seq.r1 * 24) HOUR + INTERVAL FLOOR(seq.r3 * 60) MINUTE
FROM seq;

-- ============================================
-- 12. SPRING_AI_CHAT_MEMORY（代表性对话记录，近7天）
-- ============================================
INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, type, content, timestamp) VALUES
(CONCAT(@attraction_id, ':1001'), 'USER',      '故宫太大了，从午门走到神武门腿都快断了',                         DATE_SUB(NOW(), INTERVAL 6 DAY)),
(CONCAT(@attraction_id, ':1001'), 'ASSISTANT', '故宫确实很大呢！建议您可以在中途的御花园休息一下，那里有很多长椅。', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(CONCAT(@attraction_id, ':1002'), 'USER',      '排队排了一个小时才进来，预约系统也太慢了吧',                       DATE_SUB(NOW(), INTERVAL 5 DAY)),
(CONCAT(@attraction_id, ':1002'), 'ASSISTANT', '非常抱歉给您带来不便，节假日游客较多，我们会优化核验流程。',         DATE_SUB(NOW(), INTERVAL 5 DAY)),
(CONCAT(@attraction_id, ':1003'), 'USER',      '太和殿的龙椅真霸气，能讲讲这个椅子有什么讲究吗',                   DATE_SUB(NOW(), INTERVAL 4 DAY)),
(CONCAT(@attraction_id, ':1003'), 'ASSISTANT', '太和殿龙椅是明清两代皇帝举行重大典礼时使用的御座，髹金雕龙。',     DATE_SUB(NOW(), INTERVAL 4 DAY)),
(CONCAT(@attraction_id, ':1005'), 'USER',      '冰窖餐厅的炸酱面太咸了，价格也不便宜',                           DATE_SUB(NOW(), INTERVAL 3 DAY)),
(CONCAT(@attraction_id, ':1005'), 'ASSISTANT', '感谢您的反馈，我们会将您的意见转交给餐厅管理方。',                 DATE_SUB(NOW(), INTERVAL 3 DAY)),
(CONCAT(@attraction_id, ':1006'), 'USER',      '珍宝馆的展品太震撼了，每一件都是国宝级',                         DATE_SUB(NOW(), INTERVAL 2 DAY)),
(CONCAT(@attraction_id, ':1006'), 'ASSISTANT', '很高兴您喜欢！珍宝馆确实集中了故宫最精美的文物。',               DATE_SUB(NOW(), INTERVAL 2 DAY)),
(CONCAT(@attraction_id, ':1007'), 'USER',      '厕所太脏了，保洁阿姨去哪了',                                   DATE_SUB(NOW(), INTERVAL 1 DAY)),
(CONCAT(@attraction_id, ':1007'), 'ASSISTANT', '非常抱歉！我们立即通知保洁人员加强打扫，节假日也会增派人手。',     DATE_SUB(NOW(), INTERVAL 1 DAY)),
(CONCAT(@attraction_id, ':1009'), 'USER',      '带老人来玩，有没有什么省力的路线推荐',                           NOW()),
(CONCAT(@attraction_id, ':1009'), 'ASSISTANT', '建议走中轴线精简路线：午门→太和门→太和殿→御花园→神武门，约2小时。', NOW());

-- ============================================
-- 13. tb_ai_service_suggestion（AI 服务建议，type: 0=近7天 1=近30天）
-- ============================================
INSERT INTO tb_ai_service_suggestion (attraction_id, type, summary, suggestion, create_time, update_time) VALUES
(@attraction_id, 0,
 '近7天游客对票务流程和排队体验负面反馈较多，建议优化预约核验效率',
 '根据最近7天数据分析，票务类投诉占比约35%，主要集中在：1）现场排队时间长；2）预约码核验设备反应慢；3）部分游客反映门票信息不透明。建议：(1)增加临时核验通道；(2)升级核验终端设备；(3)在公众号显著位置公示票价及优惠政策。',
 NOW(), NOW()),
(@attraction_id, 1,
 '近30天整体满意度呈上升趋势，但餐饮和卫生设施仍需改进',
 '近30天数据表明游客整体满意度较上月提升约12%。餐饮类负面反馈下降8%，但仍有游客反映餐厅价格偏高、菜品单一。卫生间清洁问题在节假日尤为突出。建议：(1)引入更多餐饮选择；(2)节假日增派保洁人员；(3)增设移动卫生间。',
 NOW(), NOW());

-- ============================================================================
-- 【可选清理：删除未来日期数据】
--   若只想要「干净的近月汇总」（避免未来行被无上界聚合查询计入 total），
--   执行以下片段即可只保留 [今天-30, 今天] 的数据。
-- ----------------------------------------------------------------------------
-- DELETE FROM tb_faq_daily_stats        WHERE attraction_id = @attraction_id AND `date` > @today;
-- DELETE FROM tb_user_tour_history       WHERE attraction_id = @attraction_id AND DATE(create_time) > @today;
-- DELETE FROM tb_ai_experience_analysis  WHERE attraction_id = @attraction_id AND DATE(create_time) > @today;
-- DELETE FROM tb_face_emotion_record      WHERE attraction_id = @attraction_id AND DATE(create_time) > @today;
-- DELETE FROM tb_user_review             WHERE attraction_id = @attraction_id AND DATE(create_time) > @today;
-- ============================================================================

-- ============================================================================
-- 验证（可手动执行）
-- ----------------------------------------------------------------------------
-- SELECT MIN(`date`), MAX(`date`), COUNT(*) FROM tb_faq_daily_stats         WHERE attraction_id=@attraction_id;
-- SELECT MIN(DATE(create_time)), MAX(DATE(create_time)), COUNT(*) FROM tb_user_tour_history     WHERE attraction_id=@attraction_id;
-- SELECT MIN(DATE(create_time)), MAX(DATE(create_time)), COUNT(*) FROM tb_ai_experience_analysis WHERE attraction_id=@attraction_id;
-- SELECT MIN(DATE(create_time)), MAX(DATE(create_time)), COUNT(*) FROM tb_face_emotion_record   WHERE attraction_id=@attraction_id;
-- SELECT MIN(DATE(create_time)), MAX(DATE(create_time)), COUNT(*), SUM(status=1) FROM tb_user_review WHERE attraction_id=@attraction_id;
-- ============================================================================
