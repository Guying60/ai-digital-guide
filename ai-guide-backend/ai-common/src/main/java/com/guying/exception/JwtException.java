package com.guying.exception;

public class JwtException extends RuntimeException {
    private Integer code;

    public JwtException(String message) {
        super(message);
        this.code = 401; // 默认值
    }

    public JwtException(Integer code, String message) {
        super(message);
        this.code = code;
    }


    public Integer getCode() {
        return code;
    }
}
