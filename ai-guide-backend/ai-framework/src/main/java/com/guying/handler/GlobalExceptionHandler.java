package com.guying.handler;

import com.guying.common.result.Result;
import com.guying.exception.JwtException;
import com.guying.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        String msg = Objects.requireNonNull(e.getBindingResult().getFieldError()).getDefaultMessage();
        log.warn("数据输入错误:"+msg);
        return Result.fail(msg);
    }

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error("运行时异常:"+e.getMessage());
        return Result.error("服务器未知错误，请稍后再试");
    }

    @ExceptionHandler(ServiceException.class)
    public Result handleServiceException(ServiceException e) {
        log.warn("业务异常:"+e.getMessage());
        return Result.fail(e.getMessage());
    }

}