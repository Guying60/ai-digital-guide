package com.guying.service.impl;

import cn.xuyanwu.spring.file.storage.FileStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guying.common.constants.RedisConstants;
import com.guying.common.enums.TaskStatusEnum;
import com.guying.context.AdminContext;
import com.guying.converter.DigitalHumanConverter;
import com.guying.exception.ServiceException;
import com.guying.mapper.AdminDigitalHumanMapper;
import com.guying.message.VideoDeleteMessage;
import com.guying.message.VideoPreloadMessage;
import com.guying.message.VideoTestMessage;
import com.guying.pojo.dto.DigitalHumanCreateDTO;
import com.guying.pojo.entity.DigitalHuman;
import com.guying.pojo.vo.DigitalHumanVO;
import com.guying.service.AdminDigitalHumanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.guying.common.constants.MqConstants.*;

@Service
@Slf4j
public class AdminDigitalHumanServiceImpl implements AdminDigitalHumanService {
    @Autowired
    private AdminDigitalHumanMapper adminDigitalHumanMapper;
    @Autowired
    private DigitalHumanConverter digitalHumanConverter;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private FileStorageService fileStorageService;

    @Value("${spring.museTalk.http-url}")
    private String museTalkHttpUrl;

    private final RestClient restClient = RestClient.create();

    /**
     * 添加或更新数字人
     * @param dto
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public DigitalHumanVO addOrUpdate(DigitalHumanCreateDTO dto) {
        Long adminId = AdminContext.getAdminId();
        DigitalHuman oldEntity = getExistingDigitalHuman(dto.getId(), dto.getAttractionId(), adminId);
        DigitalHuman entity = digitalHumanConverter.toEntity(dto);
        if (oldEntity != null) {
            entity.setId(oldEntity.getId());
        }
        entity.setAdminId(adminId);
        adminDigitalHumanMapper.insertOrUpdate(entity);
        //异步发送视频预加载消息
        VideoPreloadMessage message = new VideoPreloadMessage(
                entity.getAttractionId(),
                entity.getVideoUrl(),
                entity.getAudioUrl());
        rabbitTemplate.convertAndSend(VIDEO_PRELOAD_QUEUE, message);
        // 写入 Redis 初始状态，供前端轮询
        stringRedisTemplate.opsForValue().set(
                RedisConstants.DIGITAL_HUMAN_PRELOAD_KEY + entity.getAttractionId(),
                TaskStatusEnum.PROCESSING.toString(),
                RedisConstants.DIGITAL_HUMAN_PRELOAD_EXPIRE_TIME,
                TimeUnit.MINUTES);
        deleteReplacedOssFiles(oldEntity, entity);
        return digitalHumanConverter.toVO(entity);
    }

    /**
     * 获取数字人详情
     * @param attractionId
     * @return
     */
    @Override
    public DigitalHumanVO getDetail(Long attractionId) {
        Long id = AdminContext.getAdminId();
        LambdaQueryWrapper<DigitalHuman> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(DigitalHuman::getAttractionId, attractionId)
                .eq(DigitalHuman::getAdminId, id);
        DigitalHuman entity = adminDigitalHumanMapper.selectOne(lambdaQueryWrapper);
        if (entity == null) {
            throw new ServiceException("数字人不存在");
        }
        return digitalHumanConverter.toVO(entity);
    }

    /**
     * 游客侧查询：仅按 attractionId 查询数字人（不带 adminId），查不到返回 null。
     * @param attractionId
     * @return DigitalHumanVO（含 videoUrl/audioUrl），无数字人时返回 null
     */
    @Override
    public DigitalHumanVO getByAttractionId(Long attractionId) {
        LambdaQueryWrapper<DigitalHuman> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(DigitalHuman::getAttractionId, attractionId)
                .orderByDesc(DigitalHuman::getUpdateTime)
                .last("LIMIT 1");
        DigitalHuman entity = adminDigitalHumanMapper.selectOne(lambdaQueryWrapper);
        return entity == null ? null : digitalHumanConverter.toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteDigitalHuman(Long id) {
        Long adminId = AdminContext.getAdminId();
        LambdaQueryWrapper<DigitalHuman> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(DigitalHuman::getId, id)
                .eq(DigitalHuman::getAdminId, adminId);
        DigitalHuman entity = adminDigitalHumanMapper.selectOne(lambdaQueryWrapper);
        if (entity == null) {
            throw new ServiceException("数字人不存在或无权限删除");
        }

        adminDigitalHumanMapper.delete(lambdaQueryWrapper);
        deleteOssFile(entity.getVideoUrl());
        deleteOssFile(entity.getAudioUrl());
        rabbitTemplate.convertAndSend(VIDEO_DELETE_QUEUE, new VideoDeleteMessage(entity.getAttractionId()));
        stringRedisTemplate.delete(RedisConstants.DIGITAL_HUMAN_PRELOAD_KEY + entity.getAttractionId());
        stringRedisTemplate.delete(RedisConstants.DIGITAL_HUMAN_TEST_VIDEO_KEY + entity.getAttractionId());
    }

    /**
     * 查询数字人预加载状态
     * @param attractionId
     * @return PROCESSING | SUCCESS | FAILED
     */
    @Override
    public String checkPreloadStatus(Long attractionId) {
        return stringRedisTemplate.opsForValue().get(
                RedisConstants.DIGITAL_HUMAN_PRELOAD_KEY + attractionId);
    }

    /**
     * 触发测试视频生成
     * @param attractionId
     * @param testText 测试文本，为空时由 Python 端使用默认文本
     * @return
     */
    @Override
    public String generateTestVideo(Long attractionId, String testText) {
        VideoTestMessage message = new VideoTestMessage(attractionId, testText);
        rabbitTemplate.convertAndSend(VIDEO_TEST_QUEUE, message);
        stringRedisTemplate.opsForValue().set(
                RedisConstants.DIGITAL_HUMAN_TEST_VIDEO_KEY + attractionId,
                TaskStatusEnum.PROCESSING.toString(),
                RedisConstants.DIGITAL_HUMAN_TEST_VIDEO_EXPIRE_TIME,
                TimeUnit.MINUTES);
        log.info("测试视频生成任务已提交: attractionId={}", attractionId);
        return "任务已提交";
    }

    /**
     * 查询测试视频生成状态
     * @param attractionId
     * @return PROCESSING | SUCCESS | FAILED
     */
    @Override
    public String checkTestVideoStatus(Long attractionId) {
        return stringRedisTemplate.opsForValue().get(
                RedisConstants.DIGITAL_HUMAN_TEST_VIDEO_KEY + attractionId);
    }

    /**
     * 代理获取 Python 端生成的测试视频文件
     * @param attractionId
     * @return
     */
    @Override
    public Resource proxyTestVideo(Long attractionId) {
        return restClient.get()
                .uri(museTalkHttpUrl + "/admin/test-video/" + attractionId)
                .retrieve()
                .body(Resource.class);
    }

    private DigitalHuman getExistingDigitalHuman(Long id, Long attractionId, Long adminId) {
        LambdaQueryWrapper<DigitalHuman> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(DigitalHuman::getAdminId, adminId);
        if (id != null) {
            lambdaQueryWrapper.eq(DigitalHuman::getId, id);
        } else {
            lambdaQueryWrapper.eq(DigitalHuman::getAttractionId, attractionId)
                    .orderByDesc(DigitalHuman::getUpdateTime)
                    .last("LIMIT 1");
        }
        return adminDigitalHumanMapper.selectOne(lambdaQueryWrapper);
    }

    private void deleteReplacedOssFiles(DigitalHuman oldEntity, DigitalHuman newEntity) {
        if (oldEntity == null) {
            return;
        }
        if (!Objects.equals(oldEntity.getVideoUrl(), newEntity.getVideoUrl())) {
            deleteOssFile(oldEntity.getVideoUrl());
        }
        if (!Objects.equals(oldEntity.getAudioUrl(), newEntity.getAudioUrl())) {
            deleteOssFile(oldEntity.getAudioUrl());
        }
    }

    private void deleteOssFile(String url) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        try {
            fileStorageService.delete(url);
        } catch (Exception e) {
            log.warn("删除数字人 OSS 文件失败: {}", url, e);
        }
    }

}
