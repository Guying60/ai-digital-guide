-- ============================================================================
-- 数字人主键改为雪花 ID（与 Attraction 等实体一致，由 MyBatis-Plus ASSIGN_ID 生成）
--
-- 执行前请备份 tb_digital_human。
-- 若表中已有自增数据，需先迁移旧 ID 或清空后重建；新环境可直接执行。
-- ============================================================================

ALTER TABLE tb_digital_human
    MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键（雪花ID）';
