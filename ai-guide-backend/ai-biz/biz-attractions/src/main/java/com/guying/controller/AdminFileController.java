package com.guying.controller;

import cn.xuyanwu.spring.file.storage.FileInfo;
import cn.xuyanwu.spring.file.storage.FileStorageService;
import com.guying.common.enums.TaskStatusEnum;
import com.guying.common.result.Result;
import com.guying.context.AdminContext;
import com.guying.message.VectorIngestMessage;
import com.guying.pojo.vo.DocumentUploadVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.guying.common.constants.MqConstants.REQUEST_QUEUE;
import static com.guying.common.constants.RedisConstants.FILE_PARSING_EXPIRE_TIME;
import static com.guying.common.constants.RedisConstants.FILE_PARSING_KEY;

@RestController
@RequestMapping("/v1/admins/file")
@Slf4j
@Tag(name = "管理文件上传")
public class AdminFileController {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostMapping("/cover")
    public Result uploadAttractionCover(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("上传的文件不能为空");
        }

        String contentType = file.getContentType();

        if (contentType == null || !contentType.startsWith("image/")) {
            log.error("文件格式错误，并非图片类型，实际 Content-Type 为: {}", contentType);
            return Result.error("文件格式错误，请上传图片");
        }
        String path ="cover/"+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        log.info("封面上传path:{}", path);
        FileInfo fileInfo = fileStorageService.of(file)
                .setPath(path) //保存到相对路径下，为了方便管理，不需要可以不写
                .upload();  //将文件上传到对应地方
        return fileInfo == null ? Result.error("上传失败！") : Result.success(fileInfo.getUrl());
    }

    @PostMapping(value  = "/doc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocumentUploadVO> uploadDoc(@RequestPart("file") MultipartFile file, Long attractionId) {
        log.info("上传文件，attractionId: {}", attractionId);
        if (attractionId == null ){
            return Result.error("当前还没有景点，不能添加文件");
        }
        if (file == null || file.isEmpty()) {
            return Result.error("上传的文件不能为空");
        }
        Long adminId = AdminContext.getAdminId();
        String contentType = file.getContentType();
        // 定义合法的文档 MIME 类型
        boolean isValidDoc = "application/pdf".equals(contentType) ||
                "application/msword".equals(contentType) || // .doc
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType); // .docx
        if (!isValidDoc) {
            log.error("文件格式错误，实际 Content-Type 为: {}", contentType);
            return Result.error("文件格式错误，仅支持 doc、docx、pdf 格式");
        }

        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.lastIndexOf(".") >= 0) {
            // 如果文件名自带后缀，安全截取
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        } else {
            // 如果前端传来的文件名没有后缀，根据判断合法的 Content-Type 帮它智能补齐
            suffix = switch (contentType) {
                case "application/pdf" -> ".pdf";
                case "application/msword" -> ".doc";
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
                default -> suffix;
            };
        }
        String path ="doc/"+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        log.info("文件上传path:{}", path);
        FileInfo fileInfo = fileStorageService.of(file)
                .setPath(path) //保存到相对路径下，为了方便管理，不需要可以不写
                .upload();  //将文件上传到对应地方
        //设置文件解析任务的taskId
        if (fileInfo == null) {
            return Result.error("文件上传失败");
        }
        String taskId = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(FILE_PARSING_KEY+taskId, TaskStatusEnum.PROCESSING.toString(),FILE_PARSING_EXPIRE_TIME, TimeUnit.MINUTES);
        //发送消息,向量化文件
        VectorIngestMessage vectorIngestMessage = new VectorIngestMessage(fileInfo.getUrl(),
                file.getOriginalFilename(),suffix, attractionId,adminId,taskId);
        rabbitTemplate.convertAndSend(REQUEST_QUEUE, vectorIngestMessage);

        return Result.success(new DocumentUploadVO(fileInfo.getUrl(), taskId));
    }

    @PostMapping("/video")
    public Result uploadVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("上传的文件不能为空");
        }

        String contentType = file.getContentType();

        if (contentType == null || !contentType.startsWith("video/")) {
            log.error("文件格式错误，并非视频类型，实际 Content-Type 为: {}", contentType);
            return Result.error("文件格式错误，请上传视频");
        }
        String path = "video/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        log.info("视频上传path:{}", path);
        FileInfo fileInfo = fileStorageService.of(file)
                .setPath(path)
                .upload();
        if (fileInfo != null) {
            return Result.success(fileInfo.getUrl());
        }
        return Result.error("上传失败！");
    }

}
