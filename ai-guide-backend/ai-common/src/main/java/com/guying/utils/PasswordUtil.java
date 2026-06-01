package com.guying.utils;

import org.mindrot.jbcrypt.BCrypt;

/**
 * 密码加密工具（BCrypt）
 */
public class PasswordUtil {

    private PasswordUtil() {
    }

    /**
     * 加密明文密码
     */
    public static String encode(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    /**
     * 校验明文密码与密文是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return BCrypt.checkpw(rawPassword, encodedPassword);
    }
}
