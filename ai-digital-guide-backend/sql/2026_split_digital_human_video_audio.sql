-- ============================================================================
-- 数字人配置拆分：驱动视频与音色音频分开维护
--
-- 背景：原 tb_digital_human.oss_url 同时承担“数字人视频地址”和
--   Python 端抽取音频来源。新流程由管理端分别上传视频与音频，后端保存
--   video_url/audio_url，并通过 MQ 同步给 AutoDL。
--
-- 执行前请确认已备份 tb_digital_human。
-- ============================================================================

ALTER TABLE tb_digital_human
    CHANGE COLUMN oss_url video_url VARCHAR(500) NOT NULL COMMENT '数字人驱动视频 OSS 地址',
    ADD COLUMN audio_url VARCHAR(500) NULL COMMENT '数字人音色样本音频 OSS 地址' AFTER video_url;

ALTER TABLE tb_digital_human
    ADD UNIQUE KEY uk_admin_attraction (admin_id, attraction_id);

-- 历史数字人记录没有独立音频，需要管理端重新上传音频并保存后才可用。
-- 确认历史数据已补齐后，可按需执行：
-- ALTER TABLE tb_digital_human
--     MODIFY COLUMN audio_url VARCHAR(500) NOT NULL COMMENT '数字人音色样本音频 OSS 地址';
