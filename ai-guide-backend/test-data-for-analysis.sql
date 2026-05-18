-- ============================================
-- 测试数据 SQL
-- attractionId = 2046935279750139906
-- adminId      = 2046169420387631105
-- ============================================

SET @attraction_id = 2046935279750139906;
SET @admin_id = 2046169420387631105;
SET @today = CURDATE();

-- ============================================
-- 0. 清理已有测试数据（按 ID 范围删除）
-- ============================================
DELETE FROM tb_user_tour_history WHERE user_id BETWEEN 1001 AND 1010;
DELETE FROM tb_ai_experience_analysis WHERE user_id BETWEEN 1001 AND 1010;
DELETE FROM tb_faq_daily_stats WHERE attraction_id = @attraction_id;
DELETE FROM tb_attraction_faq WHERE attraction_id = @attraction_id;
DELETE FROM tb_attraction_document WHERE attraction_id = @attraction_id;
DELETE FROM tb_digital_human WHERE attraction_id = @attraction_id;
DELETE FROM tb_ai_service_suggestion WHERE attraction_id = @attraction_id;
DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id LIKE CONCAT(@attraction_id, ':%');

-- ============================================
-- 1. tb_admin（确保管理员存在）
-- ============================================
INSERT IGNORE INTO tb_admin (id, username, password) VALUES
(@admin_id, 'testadmin', '123456');

-- ============================================
-- 2. tb_attraction（确保景点存在）
-- ============================================
INSERT IGNORE INTO tb_attraction (id, attraction_name, cover_url, type, admin_id, create_time, update_time) VALUES
(@attraction_id, '故宫博物院', 'https://oss-cn-guangzhou.aliyuncs.com/guying60/cover/gugong.jpg', 4, @admin_id, NOW(), NOW());

-- ============================================
-- 3. tb_user（测试用户，用于外键关联）
--    user_id 范围：1001 ~ 1010
-- ============================================
INSERT IGNORE INTO tb_user (id, username, password, gender, age, nickname, create_time, update_time) VALUES
(1001, 'testuser01', '123456', 1, 25, '旅行达人小王', NOW(), NOW()),
(1002, 'testuser02', '123456', 0, 30, '文艺小张',     NOW(), NOW()),
(1003, 'testuser03', '123456', 1, 28, '背包客老李',   NOW(), NOW()),
(1004, 'testuser04', '123456', 2, 22, '摄影爱好者',   NOW(), NOW()),
(1005, 'testuser05', '123456', 0, 35, '带娃妈妈',     NOW(), NOW()),
(1006, 'testuser06', '123456', 1, 27, '历史迷小赵',   NOW(), NOW()),
(1007, 'testuser07', '123456', 2, 40, '退休老陈',     NOW(), NOW()),
(1008, 'testuser08', '123456', 1, 33, '吃货一枚',     NOW(), NOW()),
(1009, 'testuser09', '123456', 0, 29, '自由行阿琳',   NOW(), NOW()),
(1010, 'testuser10', '123456', 1, 26, '周末出游',     NOW(), NOW());

-- ============================================
-- 4. tb_attraction_faq（景点常见问答）
--    id 使用 Snowflake 风格（ASSIGN_ID）
-- ============================================
INSERT INTO tb_attraction_faq (id, question, answer, attraction_id, create_time, update_time) VALUES
(60001, '故宫的开放时间是什么时候？',         '旺季（4月-10月）8:30-17:00，淡季（11月-3月）8:30-16:30，周一闭馆（法定节假日除外）。', @attraction_id, NOW(), NOW()),
(60002, '故宫门票多少钱？',                   '旺季成人票60元，淡季成人票40元。学生票半价，60岁以上老人凭证半价。',                 @attraction_id, NOW(), NOW()),
(60003, '故宫怎么预约？',                     '请通过"故宫博物院"官方微信公众号或官网提前预约，现场不售票。建议提前7天预约。',         @attraction_id, NOW(), NOW()),
(60004, '故宫附近有停车场吗？',               '故宫午门、神武门外均设有停车场，但车位紧张，建议乘坐地铁1号线到天安门东站出行。',         @attraction_id, NOW(), NOW()),
(60005, '故宫有没有讲解服务？',               '我们提供AI智能语音导览、人工讲解和自助讲解器三种方式，您可以在入口处租用。',               @attraction_id, NOW(), NOW()),
(60006, '故宫内可以拍照吗？',                 '文物展厅内请勿使用闪光灯拍照，室外区域可以自由拍摄。部分特展禁止拍照请留意现场提示。',       @attraction_id, NOW(), NOW()),
(60007, '故宫有餐厅吗？',                     '故宫内设有故宫餐厅和冰窖餐厅，提供简餐和特色小吃，也可自带干粮在指定区域用餐。',           @attraction_id, NOW(), NOW()),
(60008, '故宫轮椅/婴儿车能推进去吗？',        '可以，故宫主要通道均具备无障碍通行条件，入口处可免费借用轮椅和婴儿车（数量有限）。',       @attraction_id, NOW(), NOW()),
(60009, '故宫游览需要多长时间？',             '建议预留3-4小时游览中轴线主要宫殿，深度游览需要6小时以上。',                          @attraction_id, NOW(), NOW()),
(60010, '故宫可以寄存行李吗？',               '午门入口处设有免费行李寄存处，大件行李可寄存并在神武门出口处取回。',                    @attraction_id, NOW(), NOW());

-- ============================================
-- 5. tb_attraction_document（上传的文档记录）
--    id AUTO_INCREMENT
-- ============================================
INSERT INTO tb_attraction_document (oss_url, file_name, file_type, doc_ids, create_time, update_time, attraction_id, admin_id) VALUES
('https://oss-cn-guangzhou.aliyuncs.com/guying60/docs/gugong_intro.md',       '故宫介绍.md',       'md',  '["doc_gugong_001","doc_gugong_002"]', NOW(), NOW(), @attraction_id, @admin_id),
('https://oss-cn-guangzhou.aliyuncs.com/guying60/docs/gugong_history.md',     '故宫历史沿革.md',   'md',  '["doc_gugong_003"]',                  NOW(), NOW(), @attraction_id, @admin_id),
('https://oss-cn-guangzhou.aliyuncs.com/guying60/docs/gugong_guide.pdf',      '游览指南.pdf',      'pdf', '["doc_gugong_004","doc_gugong_005"]', NOW(), NOW(), @attraction_id, @admin_id),
('https://oss-cn-guangzhou.aliyuncs.com/guying60/docs/gugong_collection.md',  '馆藏文物介绍.md',   'md',  '["doc_gugong_006"]',                  NOW(), NOW(), @attraction_id, @admin_id),
('https://oss-cn-guangzhou.aliyuncs.com/guying60/docs/gugong_faq_extra.md',   '常见问题补充.md',   'md',  '["doc_gugong_007"]',                  NOW(), NOW(), @attraction_id, @admin_id);

-- ============================================
-- 6. tb_digital_human（数字人配置）
--    voice_id: 0=小云(知性导游) 1=思悦(温柔解说) 2=若兮(甜美活泼)
-- ============================================
INSERT INTO tb_digital_human (oss_url, voice_id, attraction_id, admin_id, create_time, update_time) VALUES
('https://oss-cn-guangzhou.aliyuncs.com/guying60/digital_human/gugong_host.png', 0, @attraction_id, @admin_id, NOW(), NOW());

-- ============================================
-- 7. tb_faq_daily_stats（FAQ 每日命中统计）
--    id AUTO_INCREMENT，近7天数据
-- ============================================
INSERT INTO tb_faq_daily_stats (faq_id, attraction_id, date, count) VALUES
-- 开放时间 (60001) — 高频问题
(60001, @attraction_id, DATE_SUB(@today, INTERVAL 6 DAY), 45),
(60001, @attraction_id, DATE_SUB(@today, INTERVAL 5 DAY), 52),
(60001, @attraction_id, DATE_SUB(@today, INTERVAL 4 DAY), 38),
(60001, @attraction_id, DATE_SUB(@today, INTERVAL 3 DAY), 61),
(60001, @attraction_id, DATE_SUB(@today, INTERVAL 2 DAY), 48),
(60001, @attraction_id, DATE_SUB(@today, INTERVAL 1 DAY), 55),
(60001, @attraction_id, DATE_SUB(@today, INTERVAL 0 DAY), 42),
-- 门票价格 (60002)
(60002, @attraction_id, DATE_SUB(@today, INTERVAL 6 DAY), 38),
(60002, @attraction_id, DATE_SUB(@today, INTERVAL 5 DAY), 41),
(60002, @attraction_id, DATE_SUB(@today, INTERVAL 4 DAY), 35),
(60002, @attraction_id, DATE_SUB(@today, INTERVAL 3 DAY), 44),
(60002, @attraction_id, DATE_SUB(@today, INTERVAL 2 DAY), 39),
(60002, @attraction_id, DATE_SUB(@today, INTERVAL 1 DAY), 50),
(60002, @attraction_id, DATE_SUB(@today, INTERVAL 0 DAY), 47),
-- 预约方式 (60003)
(60003, @attraction_id, DATE_SUB(@today, INTERVAL 6 DAY), 28),
(60003, @attraction_id, DATE_SUB(@today, INTERVAL 5 DAY), 32),
(60003, @attraction_id, DATE_SUB(@today, INTERVAL 4 DAY), 25),
(60003, @attraction_id, DATE_SUB(@today, INTERVAL 3 DAY), 36),
(60003, @attraction_id, DATE_SUB(@today, INTERVAL 2 DAY), 30),
(60003, @attraction_id, DATE_SUB(@today, INTERVAL 1 DAY), 33),
(60003, @attraction_id, DATE_SUB(@today, INTERVAL 0 DAY), 29),
-- 停车场 (60004)
(60004, @attraction_id, DATE_SUB(@today, INTERVAL 6 DAY), 15),
(60004, @attraction_id, DATE_SUB(@today, INTERVAL 5 DAY), 18),
(60004, @attraction_id, DATE_SUB(@today, INTERVAL 4 DAY), 12),
(60004, @attraction_id, DATE_SUB(@today, INTERVAL 3 DAY), 20),
(60004, @attraction_id, DATE_SUB(@today, INTERVAL 2 DAY), 22),
(60004, @attraction_id, DATE_SUB(@today, INTERVAL 1 DAY), 19),
(60004, @attraction_id, DATE_SUB(@today, INTERVAL 0 DAY), 17),
-- 讲解服务 (60005)
(60005, @attraction_id, DATE_SUB(@today, INTERVAL 6 DAY), 22),
(60005, @attraction_id, DATE_SUB(@today, INTERVAL 5 DAY), 25),
(60005, @attraction_id, DATE_SUB(@today, INTERVAL 4 DAY), 20),
(60005, @attraction_id, DATE_SUB(@today, INTERVAL 3 DAY), 28),
(60005, @attraction_id, DATE_SUB(@today, INTERVAL 2 DAY), 23),
(60005, @attraction_id, DATE_SUB(@today, INTERVAL 1 DAY), 26),
(60005, @attraction_id, DATE_SUB(@today, INTERVAL 0 DAY), 24);

-- ============================================
-- 8. tb_ai_service_suggestion（AI 服务建议）
--    type: 0=近7天 1=近30天
-- ============================================
INSERT INTO tb_ai_service_suggestion (attraction_id, type, summary, suggestion, create_time, update_time) VALUES
(@attraction_id, 0, '近7天游客对票务流程和排队体验负面反馈较多，建议优化预约核验效率',
 '根据最近7天数据分析，票务类投诉占比35%，主要集中在：1）现场排队时间长；2）预约码核验设备反应慢；3）部分游客反映门票信息不透明。建议：(1)增加临时核验通道；(2)升级核验终端设备；(3)在公众号显著位置公示票价及优惠政策。',
 NOW(), NOW()),
(@attraction_id, 1, '近30天整体满意度呈上升趋势，但餐饮和卫生设施仍需改进',
 '近30天数据表明游客整体满意度较上月提升12%。餐饮类负面反馈下降8%，但仍有游客反映餐厅价格偏高、菜品单一。卫生间清洁问题在节假日尤为突出。建议：(1)引入更多餐饮选择；(2)节假日增派保洁人员；(3)增设移动卫生间。',
 NOW(), NOW());

-- ============================================
-- 9. SPRING_AI_CHAT_MEMORY（对话记录）
--    conversation_id 格式: attractionId:userId
-- ============================================
INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, type, content, timestamp) VALUES
(CONCAT(@attraction_id, ':1001'), 'USER', '故宫太大了，从午门走到神武门腿都快断了', NOW()),
(CONCAT(@attraction_id, ':1001'), 'ASSISTANT', '故宫确实很大呢！建议您可以在中途的御花园休息一下，那里有很多长椅可以歇脚。', NOW()),
(CONCAT(@attraction_id, ':1002'), 'USER', '排队排了一个小时才进来，预约系统也太慢了吧', NOW()),
(CONCAT(@attraction_id, ':1002'), 'ASSISTANT', '非常抱歉给您带来不便，节假日游客较多，我们会优化核验流程。', NOW()),
(CONCAT(@attraction_id, ':1003'), 'USER', '太和殿的龙椅真霸气，能讲讲这个椅子有什么讲究吗', NOW()),
(CONCAT(@attraction_id, ':1003'), 'ASSISTANT', '太和殿龙椅是明清两代皇帝举行重大典礼时使用的御座，髹金雕龙，象征着至高无上的皇权。', NOW()),
(CONCAT(@attraction_id, ':1004'), 'USER', '故宫里可以飞无人机吗', NOW()),
(CONCAT(@attraction_id, ':1004'), 'ASSISTANT', '不可以哦！故宫及周边区域是禁飞区，为了文物安全和游客隐私，严禁使用无人机。', NOW()),
(CONCAT(@attraction_id, ':1005'), 'USER', '冰窖餐厅的炸酱面太咸了，价格也不便宜', NOW()),
(CONCAT(@attraction_id, ':1005'), 'ASSISTANT', '感谢您的反馈，我们会将您的意见转交给餐厅管理方，努力改进菜品品质。', NOW()),
(CONCAT(@attraction_id, ':1006'), 'USER', '珍宝馆的展品太震撼了，每一件都是国宝级', NOW()),
(CONCAT(@attraction_id, ':1006'), 'ASSISTANT', '很高兴您喜欢！珍宝馆确实集中了故宫最精美的文物，建议您下次还可以去看看钟表馆。', NOW()),
(CONCAT(@attraction_id, ':1006'), 'USER', '钟表馆在哪？需要另外买票吗', NOW()),
(CONCAT(@attraction_id, ':1006'), 'ASSISTANT', '钟表馆在奉先殿，需要单独购买10元的参观票，每天11点和14点有钟表表演，非常值得一看！', NOW()),
(CONCAT(@attraction_id, ':1007'), 'USER', '厕所太脏了，保洁阿姨去哪了', NOW()),
(CONCAT(@attraction_id, ':1007'), 'ASSISTANT', '非常抱歉给您带来不好的体验！我们立即通知保洁人员加强打扫，也会在节假日增派人手。', NOW()),
(CONCAT(@attraction_id, ':1008'), 'USER', '故宫博物院的志愿者讲解得真好，比电子导览有意思多了', NOW()),
(CONCAT(@attraction_id, ':1008'), 'ASSISTANT', '感谢您对志愿者工作的认可！我们的志愿者都经过严格培训，希望能为每位游客带来最好的参观体验。', NOW()),
(CONCAT(@attraction_id, ':1009'), 'USER', '带老人来玩，有没有什么省力的路线推荐', NOW()),
(CONCAT(@attraction_id, ':1009'), 'ASSISTANT', '建议您走中轴线精简路线：午门→太和门→太和殿→中和殿→保和殿→乾清宫→御花园→神武门，全程约2小时，比较轻松。', NOW()),
(CONCAT(@attraction_id, ':1010'), 'USER', '纪念品商店的东西太贵了，外面十块钱的书签这里卖五十', NOW()),
(CONCAT(@attraction_id, ':1010'), 'ASSISTANT', '我们的文创产品都是正版授权的故宫联名款，设计和品质都有保障。也感谢您的反馈，我们会考虑推出更多亲民价位的产品。', NOW());

-- ============================================
-- 10. tb_ai_experience_analysis（AI 情感/关注点分析）
--     emotion: 0=正面  1=中性  2=负面
--     focus:   0=餐饮 1=票务 2=导览 3=交通 4=停车 5=住宿 6=景点体验 7=投诉建议 8=其他
--     id AUTO_INCREMENT
-- ============================================
INSERT INTO tb_ai_experience_analysis (focus, emotion, create_time, conversation_id, user_id, attraction_id) VALUES
-- 近7天数据（会被统计为 total）
(1, 2, DATE_SUB(@today, INTERVAL 0 DAY), CONCAT(@attraction_id, ':1001'), 1001, @attraction_id),  -- 票务+负面（排队）
(1, 2, DATE_SUB(@today, INTERVAL 1 DAY), CONCAT(@attraction_id, ':1002'), 1002, @attraction_id),  -- 票务+负面（预约慢）
(2, 0, DATE_SUB(@today, INTERVAL 2 DAY), CONCAT(@attraction_id, ':1003'), 1003, @attraction_id),  -- 导览+正面（讲解好）
(8, 2, DATE_SUB(@today, INTERVAL 2 DAY), CONCAT(@attraction_id, ':1004'), 1004, @attraction_id),  -- 其他+负面（禁飞）
(0, 2, DATE_SUB(@today, INTERVAL 3 DAY), CONCAT(@attraction_id, ':1005'), 1005, @attraction_id),  -- 餐饮+负面（太咸）
(6, 0, DATE_SUB(@today, INTERVAL 0 DAY), CONCAT(@attraction_id, ':1006'), 1006, @attraction_id),  -- 景点体验+正面
(2, 0, DATE_SUB(@today, INTERVAL 1 DAY), CONCAT(@attraction_id, ':1006'), 1006, @attraction_id),  -- 导览+正面（再问钟表馆）
(7, 2, DATE_SUB(@today, INTERVAL 3 DAY), CONCAT(@attraction_id, ':1007'), 1007, @attraction_id),  -- 投诉建议+负面
(2, 0, DATE_SUB(@today, INTERVAL 5 DAY), CONCAT(@attraction_id, ':1008'), 1008, @attraction_id),  -- 导览+正面
(3, 0, DATE_SUB(@today, INTERVAL 4 DAY), CONCAT(@attraction_id, ':1009'), 1009, @attraction_id),  -- 交通+正面（问无障碍路线）
(6, 2, DATE_SUB(@today, INTERVAL 5 DAY), CONCAT(@attraction_id, ':1010'), 1010, @attraction_id),  -- 景点体验+负面（纪念品贵）
(6, 1, DATE_SUB(@today, INTERVAL 2 DAY), CONCAT(@attraction_id, ':1003_f2'), 1003, @attraction_id), -- 中性
(0, 1, DATE_SUB(@today, INTERVAL 4 DAY), CONCAT(@attraction_id, ':1008_f2'), 1008, @attraction_id), -- 中性

-- 8~14天前数据（会被统计为 preTotal）
(6, 0, DATE_SUB(@today, INTERVAL 8 DAY),  CONCAT(@attraction_id, ':old1'), 1001, @attraction_id),
(2, 2, DATE_SUB(@today, INTERVAL 9 DAY),  CONCAT(@attraction_id, ':old2'), 1002, @attraction_id),
(4, 2, DATE_SUB(@today, INTERVAL 10 DAY), CONCAT(@attraction_id, ':old3'), 1003, @attraction_id),
(0, 0, DATE_SUB(@today, INTERVAL 12 DAY), CONCAT(@attraction_id, ':old4'), 1004, @attraction_id),
(6, 1, DATE_SUB(@today, INTERVAL 13 DAY), CONCAT(@attraction_id, ':old5'), 1005, @attraction_id);

-- ============================================
-- 11. tb_user_tour_history（游览记录 + 用户评价反馈）
--     id 使用 Snowflake 风格
-- ============================================
INSERT INTO tb_user_tour_history (id, user_id, attraction_id, conversation_id, create_time, update_time, attraction_name, cover_url, type, score, feedback_text) VALUES
-- 近7天有评价的记录（满足：TIMESTAMPDIFF >= 5min, 5 <= LENGTH(feedback_text) <= 18, score 有值）
(70001, 1001, @attraction_id, CONCAT(@attraction_id, ':1001'),
        DATE_SUB(NOW(), INTERVAL 0 DAY), DATE_SUB(NOW(), INTERVAL 0 DAY) + INTERVAL 30 MINUTE, '故宫博物院', 'https://oss-cn-guangzhou.aliyuncs.com/guying60/cover/gugong.jpg', 4, 2, '排队太久体验很差'),
(70002, 1002, @attraction_id, CONCAT(@attraction_id, ':1002'),
        DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 20 MINUTE, '故宫博物院', 'https://oss-cn-guangzhou.aliyuncs.com/guying60/cover/gugong.jpg', 4, 1, '预约系统太慢耽误行程'),
(70003, 1003, @attraction_id, CONCAT(@attraction_id, ':1003'),
        DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 15 MINUTE, '故宫博物院', 'https://oss-cn-guangzhou.aliyuncs.com/guying60/cover/gugong.jpg', 4, 5, '龙椅讲解非常精彩'),
(70004, 1005, @attraction_id, CONCAT(@attraction_id, ':1005'),
        DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 10 MINUTE, '故宫博物院', 'https://oss-cn-guangzhou.aliyuncs.com/guying60/cover/gugong.jpg', 4, 1, '餐厅饭菜质量堪忧'),
(70005, 1006, @attraction_id, CONCAT(@attraction_id, ':1006'),
        DATE_SUB(NOW(), INTERVAL 0 DAY), DATE_SUB(NOW(), INTERVAL 0 DAY) + INTERVAL 8 MINUTE,  '故宫博物院', 'https://oss-cn-guangzhou.aliyuncs.com/guying60/cover/gugong.jpg', 4, 5, '国宝级展品令人震撼'),
(70006, 1007, @attraction_id, CONCAT(@attraction_id, ':1007'),
        DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 18 MINUTE, '故宫博物院', 'https://oss-cn-guangzhou.aliyuncs.com/guying60/cover/gugong.jpg', 4, 1, '卫生间卫生状况堪忧'),
(70007, 1008, @attraction_id, CONCAT(@attraction_id, ':1008'),
        DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY) + INTERVAL 12 MINUTE, '故宫博物院', 'https://oss-cn-guangzhou.aliyuncs.com/guying60/cover/gugong.jpg', 4, 4, '志愿者讲解很有意思'),
(70008, 1009, @attraction_id, CONCAT(@attraction_id, ':1009'),
        DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY) + INTERVAL 25 MINUTE, '故宫博物院', 'https://oss-cn-guangzhou.aliyuncs.com/guying60/cover/gugong.jpg', 4, 4, '无障碍路线很方便'),
(70009, 1010, @attraction_id, CONCAT(@attraction_id, ':1010'),
        DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY) + INTERVAL 7 MINUTE,  '故宫博物院', 'https://oss-cn-guangzhou.aliyuncs.com/guying60/cover/gugong.jpg', 4, 3, '纪念品价格偏高'),
(70010, 1004, @attraction_id, CONCAT(@attraction_id, ':1004'),
        DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 10 MINUTE, '故宫博物院', 'https://oss-cn-guangzhou.aliyuncs.com/guying60/cover/gugong.jpg', 4, 4, '整体体验不错值得推荐');
