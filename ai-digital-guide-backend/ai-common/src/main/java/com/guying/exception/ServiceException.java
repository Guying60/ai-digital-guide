package com.guying.exception;

public class ServiceException extends RuntimeException {

    private Integer code;

    public ServiceException(String message) {
        super(message);
        this.code = 500; // 默认值
    }

    public ServiceException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

}