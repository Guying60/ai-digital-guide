-- ============================================================================
-- 体验分析表新增 message 列：冗余存储「被分析的游客原话」
--
-- 背景：原先 tb_ai_experience_analysis 只存 emotion/focus 标签，不存原话；
--   生成运营建议时取负面样本需 JOIN SPRING_AI_CHAT_MEMORY 反查 content，
--   会产生 (分析行 × 会话内USER消息) 笛卡尔积，且 focus 与原话错配。
--   冗余存储 message 后，负面样本查询可直接取用，focus 与原话一一对应。
--
-- ⚠️ 部署/运行 AI 建议生成（ExperienceAnalysisTask / generateSuggestion）前必须先执行本脚本，
--    否则 getNegativeSample 会因 Unknown column 'message' 报错。
-- 历史数据该列为 NULL，查询已用 message IS NOT NULL 过滤，新数据写入后即生效。
-- ============================================================================

ALTER TABLE tb_ai_experience_analysis
    ADD COLUMN message VARCHAR(1000) NULL COMMENT '被分析的游客原话（冗余，供负面样本直接取用）' AFTER attraction_id;
