-- ============================================================
-- 修复: tb_faq_daily_stats 缺少 date 列
-- 背景: Java 实体 FaqDailyStats 有 date 字段，Mapper XML 已改为
--       按 s.date 过滤，但数据库表缺少该列，导致:
--       1. 热点FAQ查询报 Unknown column 's.date'
--       2. FlushFaqStatsTask 每日落库静默失败（被 try/catch 吞掉）
-- 执行前确认:
--   SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
--   WHERE TABLE_NAME = 'tb_faq_daily_stats' AND COLUMN_NAME = 'date';
-- 执行后验证:
--   DESCRIBE tb_faq_daily_stats;
-- ============================================================

-- 1. 添加 date 列（先不加 NOT NULL，避免已有数据行报错）
ALTER TABLE tb_faq_daily_stats
    ADD COLUMN `date` DATE NULL COMMENT '业务日期（该统计数据所属日期）'
    AFTER `attraction_id`;

-- 2. 为已有数据回填 date（从 create_time 推断业务日期）
--    FlushFaqStatsTask 在凌晨 00:05 跑，写入的是昨天的数据
UPDATE tb_faq_daily_stats
SET `date` = DATE_SUB(DATE(create_time), INTERVAL 1 DAY)
WHERE `date` IS NULL;

-- 3. 如果还有 NULL，填入 create_time 的日期作为兜底
UPDATE tb_faq_daily_stats
SET `date` = DATE(create_time)
WHERE `date` IS NULL;

-- 4. 回填完成后加 NOT NULL 约束
ALTER TABLE tb_faq_daily_stats
    MODIFY COLUMN `date` DATE NOT NULL COMMENT '业务日期（该统计数据所属日期）';

-- 5. 添加索引（提升按日期查询性能）
ALTER TABLE tb_faq_daily_stats
    ADD INDEX idx_attraction_date (`attraction_id`, `date`);
