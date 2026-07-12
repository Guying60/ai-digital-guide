-- 出游记录卡片展示「对话数」：WS 断开落库时写入，列表接口直接返回
-- message_count：本会话用户提问 + AI 回复的近似总条数（questionCount * 2）

ALTER TABLE tb_user_tour_history
    ADD COLUMN message_count INT NOT NULL DEFAULT 0 COMMENT '对话消息条数（用户提问+AI回复）' AFTER city;
