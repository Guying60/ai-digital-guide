package com.guying.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    // 直接用 MP 内置的雪花生成器
    private final IdentifierGenerator identifierGenerator = new DefaultIdentifierGenerator();

    @Override
    public void insertFill(MetaObject metaObject) {
        // 仅在字段为 null 时才填充
        this.strictInsertFill(metaObject, "conversationId", Long.class,
            identifierGenerator.nextId(null).longValue());
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "date", LocalDate.class, LocalDate.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 不需要更新时填充，留空即可
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}