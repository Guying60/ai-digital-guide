package com.guying.service.impl;

import cn.xuyanwu.spring.file.storage.FileStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guying.common.constants.MqConstants;
import com.guying.context.AdminContext;
import com.guying.converter.DigitalHumanConverter;
import com.guying.mapper.AdminDigitalHumanMapper;
import com.guying.message.VideoDeleteMessage;
import com.guying.pojo.dto.DigitalHumanCreateDTO;
import com.guying.pojo.dto.DigitalHumanUpdateDTO;
import com.guying.pojo.entity.DigitalHuman;
import com.guying.pojo.vo.DigitalHumanVO;
import com.guying.service.AdminDigitalHumanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AdminDigitalHumanServiceImpl implements AdminDigitalHumanService {
    @Autowired
    private AdminDigitalHumanMapper adminDigitalHumanMapper;
    @Autowired
    private DigitalHumanConverter digitalHumanConverter;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public DigitalHumanVO addOrUpdate(DigitalHumanCreateDTO dto) {
        DigitalHuman entity = digitalHumanConverter.toEntity(dto);
        entity.setAdminId(AdminContext.getAdminId());
        adminDigitalHumanMapper.insertOrUpdate(entity);
        return digitalHumanConverter.toVO(entity);
    }

    @Override
    public void deleteById(Long id) {
        LambdaQueryWrapper<DigitalHuman> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(DigitalHuman::getId, id)
                .eq(DigitalHuman::getAdminId, AdminContext.getAdminId());
        DigitalHuman entity = adminDigitalHumanMapper.selectOne(lambdaQueryWrapper);
        if (entity == null) {
            return;
        }
        fileStorageService.delete(entity.getOssUrl());
        adminDigitalHumanMapper.delete(lambdaQueryWrapper);

        // 通知 MuseTalk 清理本地视频/音频缓存
        Long attractionId = entity.getAttractionId();
        if (attractionId != null) {
            try {
                rabbitTemplate.convertAndSend(
                        MqConstants.VIDEO_DELETE_QUEUE,
                        new VideoDeleteMessage(attractionId)
                );
                log.info("已发送数字人删除消息, attractionId: {}", attractionId);
            } catch (Exception e) {
                log.error("发送数字人删除消息失败, attractionId: {}", attractionId, e);
            }
        }
    }


    @Override
    public DigitalHumanVO getDetail(Long attractionId) {
        Long id = AdminContext.getAdminId();
        LambdaQueryWrapper<DigitalHuman> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(DigitalHuman::getAttractionId, attractionId)
                .eq(DigitalHuman::getAdminId, id);
        DigitalHuman entity = adminDigitalHumanMapper.selectOne(lambdaQueryWrapper);
        return digitalHumanConverter.toVO(entity);
    }

}
