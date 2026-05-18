package com.guying.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AliyunVoiceEnum {

    XIAOYUN(0, "xiaoyun", "知性导游(小云)"),
    SIYUE(1, "siyue", "温柔解说(思悦)"),
    RUOXI(2, "ruoxi", "甜美活泼(若兮)"),
    XIAOXIAN(3, "xiaoxian", "亲切大姐姐(小仙)"),
    XIAOGANG(4, "xiaogang", "沉稳专家(小刚)"),
    AIDA(5, "aida", "阳光向导(艾达)"),
    LAOTIE(6, "laotie", "幽默大叔(老铁)"),
    SHANSHAN(7, "shanshan", "可爱童音(姗姗)");

    private final int code;
    private final String voiceName; // 传给阿里云 SDK 的纯字符串代号
    private final String desc;    // Admin 后台展示名

    // 供前端传展示名时转换用
    public static AliyunVoiceEnum fromDesc(String desc) {
        for (AliyunVoiceEnum e : values()) {
            if (e.desc.equals(desc)) return e;
        }
        return XIAOYUN; // 兜底，防止报错导致数字人变哑巴
    }

    // 供数据库存 int 状态码时转换用
    public static AliyunVoiceEnum fromCode(int code) {
        for (AliyunVoiceEnum e : values()) {
            if (e.code == code) return e;
        }
        return XIAOYUN;
    }

    // 供大模型业务直接传 voiceName 时转换用
    public static AliyunVoiceEnum fromVoiceName(String voiceName) {
        if (voiceName == null) return XIAOYUN;
        for (AliyunVoiceEnum e : values()) {
            if (e.voiceName.equals(voiceName)) return e;
        }
        return XIAOYUN;
    }
}