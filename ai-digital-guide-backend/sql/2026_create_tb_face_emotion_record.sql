-- ============================================================================
-- 新建表：面部表情情感分析记录 (tb_face_emotion_record)
--
-- 背景：原"后置摄像头识别景象辅助问答"方案已弃用，改为前置摄像头低频采集
--   游客面部表情，调用支持视觉输入的大模型做情感分类（返回表情标签+置信度），
--   落库后供管理后台生成"游客面部表情趋势报告"（按时间/按景点维度统计）。
--   本表与现有文本情感分析表 tb_ai_experience_analysis 相互独立，不混淆两路数据。
--   原始人脸图像不入库、不落 OSS，仅存结构化结果（隐私合规）。
--
-- 字段说明：
--   expression: 表情(0喜悦 1惊讶 2中性 3困惑 4厌恶 5愤怒 6悲伤)，对应 ExpressionEnum
--   confidence: 大模型返回的置信度 0~1
--   detail:     额外结构化字段(JSON 串，如理由 reason)，可空，便于后续扩展多候选
--
-- ⚠️ 部署面部表情分析功能前必须先执行本脚本，否则 FaceEmotionRecordMapper 写入/查询会报缺表。
--
-- 执行前确认：
--   SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tb_face_emotion_record';
-- 执行后验证：
--   DESCRIBE tb_face_emotion_record;
-- ============================================================================

CREATE TABLE IF NOT EXISTS tb_face_emotion_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id         BIGINT       NOT NULL                COMMENT '游客ID',
    attraction_id   BIGINT       NOT NULL                COMMENT '景点ID',
    conversation_id VARCHAR(64)  NULL                    COMMENT '会话ID(取自 WebSocket ChatSessionContext)',
    expression      TINYINT      NOT NULL                COMMENT '表情(0喜悦 1惊讶 2中性 3困惑 4厌恶 5愤怒 6悲伤)',
    confidence      DOUBLE       NULL                    COMMENT '置信度 0~1',
    detail          VARCHAR(500) NULL                    COMMENT '额外结构化字段(JSON 串，如 reason)',
    create_time     DATETIME     NOT NULL                COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_attraction_time (attraction_id, create_time),
    KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面部表情情感分析记录';
