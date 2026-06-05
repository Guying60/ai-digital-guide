package com.guying.service.impl;

import cn.xuyanwu.spring.file.storage.FileStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guying.common.constants.MqConstants;
import com.guying.common.constants.RedisConstants;
import com.guying.common.enums.TaskStatusEnum;
import com.guying.context.AdminContext;
import com.guying.converter.DigitalHumanConverter;
import com.guying.mapper.AdminDigitalHumanMapper;
import com.guying.message.VideoDeleteMessage;
import com.guying.message.VideoPreloadMessage;
import com.guying.message.VideoTestMessage;
import com.guying.pojo.dto.DigitalHumanCreateDTO;
import com.guying.pojo.dto.DigitalHumanUpdateDTO;
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
import org.springframework.web.client.RestClient;

import java.util.concurrent.TimeUnit;

import static com.guying.common.constants.MqConstants.VIDEO_PRELOAD_QUEUE;
import static com.guying.common.constants.MqConstants.VIDEO_TEST_QUEUE;

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

    @Value("${spring.museTalk.http-url}")
    private String museTalkHttpUrl;

    private final RestClient restClient = RestClient.create();

    /**
     * 添加或更新数字人
     * @param dto
     * @return
     */
    @Override
    public DigitalHumanVO addOrUpdate(DigitalHumanCreateDTO dto) {
        DigitalHuman entity = digitalHumanConverter.toEntity(dto);
        entity.setAdminId(AdminContext.getAdminId());
        adminDigitalHumanMapper.insertOrUpdate(entity);
        //异步发送视频预加载消息
        VideoPreloadMessage message = new VideoPreloadMessage(entity.getAttractionId(), entity.getOssUrl());
        rabbitTemplate.convertAndSend(VIDEO_PRELOAD_QUEUE, message);
        // 写入 Redis 初始状态，供前端轮询
        stringRedisTemplate.opsForValue().set(
                RedisConstants.DIGITAL_HUMAN_PRELOAD_KEY + entity.getAttractionId(),
                TaskStatusEnum.PROCESSING.toString(),
                RedisConstants.DIGITAL_HUMAN_PRELOAD_EXPIRE_TIME,
                TimeUnit.MINUTES);
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
            throw new RuntimeException("数字人不存在");
        }
        return digitalHumanConverter.toVO(entity);
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

}
