package com.guying.common.constants;

public class RedisConstants {
    //login token key
    public static final String USER_LOGIN_KEY = "user:login_token:";
    public static final Long LOGIN_TOKEN_EXPIRE_TIME = 1000 * 60 * 60 * 24 * 7L;

    public static final String ADMIN_LOGIN_KEY = "admin:login_token:";


    //
    public static final String USER_CONVERSATION_KEY = "user:conversation:";
    public static final String USER_INFO_KEY = "user:info:";
    public static final Long USER_INFO_EXPIRE_TIME = 6L;

    public static final Long CONVERSATION_EXPIRE_TIME = 36L;

    //热点问答
    public static final String HOT_FAQ_KEY = "hot:faq:";  //已经提炼出来的经常问的问题，即标准问题
    public static final String HOT_ANSWER_KEY = "hot:answer:";  //根据标准问题生成的标准答案
    public static final String HOT_QUESTION_KEY = "hot:question:"; //待处理的问题，以set集合进行存放
    public static final Long HOT_ANSWER_EXPIRE_TIME = 12L;

    public static final String FILE_PARSING_KEY = "file:parsing:";
    public static final Long FILE_PARSING_EXPIRE_TIME = 30L;

    public static final String FAQ_TAG = "1";
    public static final String DOC_TAG = "2";

    public static final String DIGITAL_HUMAN_PRELOAD_KEY = "digital_human:preload_status:";
    public static final Long DIGITAL_HUMAN_PRELOAD_EXPIRE_TIME = 30L;

    public static final String DIGITAL_HUMAN_TEST_VIDEO_KEY = "digital_human:test_video_status:";
    public static final Long DIGITAL_HUMAN_TEST_VIDEO_EXPIRE_TIME = 30L;

    //AI 路线推荐：缓存当前激活的路线时间轴，key = route:plan:{attractionId}:{userId}
    public static final String ROUTE_PLAN_KEY = "route:plan:";
    public static final Long ROUTE_PLAN_EXPIRE_TIME = 12L; //小时

    //高德坐标解析缓存：景点锚点(含 adcode/城市/中心点) 与 单个地标 POI
    public static final String AMAP_ANCHOR_KEY = "amap:anchor:"; //+attractionId
    public static final Long AMAP_ANCHOR_EXPIRE_TIME = 30L; //天
    public static final String AMAP_POI_KEY = "amap:poi:"; //+adcode:keyword
    public static final Long AMAP_POI_EXPIRE_TIME = 7L; //天
}
