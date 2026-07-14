-- 游览历史记录增加「状态」字段：0-进行中 1-已结束 2-已评价
-- 存量数据默认为 0（进行中），不影响现有功能
-- 新建记录初始状态为进行中，结束对话后变为已结束，评价后变为已评价

ALTER TABLE tb_user_tour_history
    ADD COLUMN tour_status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-进行中 1-已结束 2-已评价' AFTER message_count;
