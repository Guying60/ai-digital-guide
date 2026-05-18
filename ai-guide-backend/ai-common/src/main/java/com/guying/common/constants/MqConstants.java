package com.guying.common.constants;

public class MqConstants {
    /**
     * 用户旅游历史记录
     */
    public static final String USER_TOUR_HISTORY_DIRECT = "user.tour.history.direct";
    public static final String USER_TOUR_HISTORY_QUEUE = "user.tour.history.queue";
    public static final String USER_TOUR_HISTORY_ROUTING_KEY = "user.tour.history";


    /**
     * 删除向量
     */
    public static final String DELETE_VECTOR_DIRECT = "delete.vector.direct";
    public static final String DELETE_VECTOR_QUEUE = "delete.vector.queue";
    public static final String DELETE_VECTOR_ROUTING_KEY = "delete.vector";


    public static final String REQUEST_QUEUE = "doc.convert.request";
    public static final String RESULT_QUEUE = "doc.convert.result";


    public static final String ADMIN_SAVE_FAQ_DIRECT = "admin.save.faq.direct";
    public static final String ADMIN_SAVE_FAQ_QUEUE = "admin.save.faq.queue";
    public static final String ADMIN_SAVE_FAQ_ROUTING_KEY = "admin.save.faq";

    /**
     * 文档转换死信队列
     */
    public static final String DLX_EXCHANGE = "doc.convert.dlx";
    public static final String DLQ_QUEUE = "doc.convert.dlq";
    public static final String DLQ_ROUTING_KEY = "doc.dlq.routing.key";

    /**
     * 预加载视频队列
     */
    public static final String VIDEO_PRELOAD_QUEUE = "video.preload.queue";

}

