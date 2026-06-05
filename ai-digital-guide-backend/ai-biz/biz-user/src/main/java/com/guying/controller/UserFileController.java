package com.guying.controller;

import cn.xuyanwu.spring.file.storage.FileInfo;
import cn.xuyanwu.spring.file.storage.FileStorageService;
import com.guying.common.result.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/v1/users/file")
@Slf4j
@Tag(name = "用户文件上传")
public class UserFileController {

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * 上传头像
     */
    @PostMapping("/avatar")
    public Result uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("上传的文件不能为空");
        }

        String contentType = file.getContentType();

        if (contentType == null || !contentType.startsWith("image/")) {
            log.error("文件格式错误，并非图片类型，实际 Content-Type 为: {}", contentType);
            return Result.error("文件格式错误，请上传图片");
        }
        String path ="avatar/"+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        log.info("头像上传path:{}", path);
        FileInfo fileInfo = fileStorageService.of(file)
                .setPath(path) //保存到相对路径下，为了方便管理，不需要可以不写
                .upload();  //将文件上传到对应地方
        return fileInfo == null ? Result.error("上传失败！") : Result.success(fileInfo.getUrl());
    }
}
