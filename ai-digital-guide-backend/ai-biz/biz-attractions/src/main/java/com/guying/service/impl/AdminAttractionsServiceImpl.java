package com.guying.service.impl;

import cn.xuyanwu.spring.file.storage.FileStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.guying.admin.service.StatInternalService;
import com.guying.common.constants.MqConstants;
import com.guying.common.constants.RedisConstants;
import com.guying.common.result.ScrollResult;
import com.guying.context.AdminContext;
import com.guying.converter.AdminAttractionsConverter;
import com.guying.converter.AttractionsConverter;
import com.guying.exception.ServiceException;
import com.guying.mapper.AdminDigitalHumanMapper;
import com.guying.mapper.AttractionDocumentMapper;
import com.guying.mapper.AttractionFaqMapper;
import com.guying.mapper.AttractionsMapper;
import com.guying.message.VideoDeleteMessage;
import com.guying.pojo.dto.AttractionCreateDTO;
import com.guying.pojo.dto.AttractionListQueryDTO;
import com.guying.pojo.dto.AttractionUpdateDTO;
import com.guying.pojo.entity.Attraction;
import com.guying.pojo.entity.AttractionDocument;
import com.guying.pojo.entity.AttractionFaq;
import com.guying.pojo.entity.DigitalHuman;
import com.guying.pojo.vo.AttractionAdditionVO;
import com.guying.pojo.vo.AttractionDetailVO;
import com.guying.pojo.vo.AttractionListVO;
import com.guying.pojo.vo.DocumentsQueryVO;
import com.guying.service.AdminAttractionsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
public class AdminAttractionsServiceImpl extends ServiceImpl<AttractionsMapper, Attraction> implements AdminAttractionsService {
    @Autowired
    private AttractionsConverter attractionsConverter;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private AttractionFaqMapper attractionFaqMapper;

    @Autowired
    private StatInternalService statInternalService;

    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private AttractionsMapper attractionsMapper;
    @Autowired
    private AttractionDocumentMapper attractionDocumentMapper;
    @Autowired
    private AdminAttractionsConverter adminAttractionsConverter;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AdminDigitalHumanMapper adminDigitalHumanMapper;



    /**
     * 添加景点
     * @param attractionCreateDTO
     * @return
     */
    @Override
    public AttractionAdditionVO addAttraction(AttractionCreateDTO attractionCreateDTO) {
        Long adminId = AdminContext.getAdminId();
        Attraction attraction = attractionsConverter.toAttraction(attractionCreateDTO);
        attraction.setAdminId(adminId);
        try {
            attractionsMapper.insert(attraction);
        } catch (Exception e) {
            log.error("景点名称: {} 添加景点失败: {}", attraction.getAttractionName(), e.getMessage());
            throw new ServiceException("添加景点失败,可能为景点名称重复");
        }
        return adminAttractionsConverter.toAttractionAdditionVO(attraction);
    }

    /**
     * 获取景点详情
     * @param attractionId
     * @return
     */
    @Override
    public AttractionDetailVO getAttractionDetail(Long attractionId) {
        Long adminId = AdminContext.getAdminId();
        LambdaQueryWrapper<Attraction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Attraction::getAdminId, adminId);
        queryWrapper.eq(Attraction::getId, attractionId);
        Attraction attraction = attractionsMapper.selectOne(queryWrapper);
        if (attraction == null) {
            throw new ServiceException("没有发现景点，快去添加吧~");
        }
        return attractionsConverter.toAttractionDetailVO(attraction);
    }

    /**
     * 获取景点文档
     * @return
     */
    @Override
    public List<DocumentsQueryVO> getDocuments(Long attractionId) {
        Long adminId = AdminContext.getAdminId();
        LambdaQueryWrapper<AttractionDocument> lqw = new LambdaQueryWrapper<>();
        lqw.eq(AttractionDocument::getAttractionId, attractionId);
        lqw.eq(AttractionDocument::getAdminId, adminId);
        List<AttractionDocument> attractionDocuments = attractionDocumentMapper.selectList(lqw);
        return adminAttractionsConverter.toDocumentsQueryVOList(attractionDocuments);
    }

    /**
     * 删除景点文档
     * @param fileId
     */
    @Override
    public void deleteDocument(Long fileId) {
        Long adminId = AdminContext.getAdminId();
        //获取景点文档docId
        LambdaQueryWrapper<AttractionDocument> lqw = new LambdaQueryWrapper<>();
        lqw.eq(AttractionDocument::getId, fileId);
        lqw.eq(AttractionDocument::getAdminId, adminId);
        AttractionDocument attractionDocument = attractionDocumentMapper.selectOne(lqw);
        List<String> docIds = attractionDocument.getDocIds();
        // 删除景点文档
        if (docIds == null || docIds.isEmpty()) {
            throw new ServiceException("删除景点文档失败,可能为文档不存在");
        }
        try {
            fileStorageService.delete(attractionDocument.getOssUrl());
            attractionDocumentMapper.deleteById(attractionDocument);
        } catch (Exception e) {
            throw new ServiceException("删除景点文档失败");
        }
        rabbitTemplate.convertAndSend(MqConstants.DELETE_VECTOR_DIRECT, MqConstants.DELETE_VECTOR_ROUTING_KEY, docIds);
        log.info("删除景点文档成功: {}", fileId);
    }

    /**
     * 检查文档状态（是否解析完成）
     * @param taskId
     * @return
     */
    @Override
    public String checkDocumentStatus(String taskId) {
        return stringRedisTemplate.opsForValue().get(RedisConstants.FILE_PARSING_KEY + taskId);
    }

    /**
     * 保存自动生成的faq
     * @param attractionFaq
     */
    @Override
    public void saveAutoGeneratedFaq(AttractionFaq attractionFaq) {
        attractionFaqMapper.insert(attractionFaq);
    }

    /**
     * 更新景点信息
     * @param attractionUpdateDTO
     * @return
     */
    @Override
    public AttractionDetailVO updateAttraction(AttractionUpdateDTO attractionUpdateDTO) {
        Long adminId = AdminContext.getAdminId();
        Attraction attraction = attractionsConverter.toAttraction(attractionUpdateDTO);
        int updatedRows = 0;
        try {
            LambdaQueryWrapper<Attraction> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Attraction::getId, attraction.getId());
            queryWrapper.eq(Attraction::getAdminId, adminId);
            updatedRows = attractionsMapper.update(attraction, queryWrapper);

        } catch (Exception e) {
            log.error("景点ID: {} 更新景点信息失败: {}", attraction.getId(), e.getMessage());
            throw new ServiceException("更新景点信息失败,可能为非法权限");
        }
        if (updatedRows == 0) {
            throw new ServiceException("更新景点信息失败,可能为景点不存在");
        }

        return attractionsConverter.toAttractionDetailVO(attraction);
    }

    /**
     * 删除景点
     * @param attractionId
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteAttraction(Long attractionId) {
        Long adminId = AdminContext.getAdminId();
        LambdaQueryWrapper<Attraction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Attraction::getId, attractionId);
        queryWrapper.eq(Attraction::getAdminId, adminId);
        deleteAboutAttraction(attractionId,adminId);
        int delete = attractionsMapper.delete(queryWrapper);
        if (delete == 0) {
            throw new ServiceException("删除景点信息失败,可能为景点不存在或权限不符");
        }

    }

    /**
     * 获取景点列表
     * @param attractionListQueryDTO
     * @return
     */
    @Override
    public ScrollResult<AttractionListVO> getAttractionList(AttractionListQueryDTO attractionListQueryDTO) {
        //TODO展示景点平均分和评论数？
        Long adminId = AdminContext.getAdminId();
        LambdaQueryWrapper<Attraction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Attraction::getAdminId, adminId)
                .like(StringUtils.hasText(attractionListQueryDTO.getKeyWord()), Attraction::getAttractionName, attractionListQueryDTO.getKeyWord())
                .eq(attractionListQueryDTO.getType() != null, Attraction::getType, attractionListQueryDTO.getType())
                .eq(StringUtils.hasText(attractionListQueryDTO.getCity()), Attraction::getCity, attractionListQueryDTO.getCity())
                .lt(StringUtils.hasText(attractionListQueryDTO.getLastId()), Attraction::getId, attractionListQueryDTO.getLastId())
                .orderByDesc(Attraction::getCreateTime)
                .last("limit " + (attractionListQueryDTO.getPageSize()+1));

        List<Attraction> attractionList = attractionsMapper.selectList(queryWrapper);
        boolean hasMore = attractionList.size() > attractionListQueryDTO.getPageSize();
        if (hasMore) {
            attractionList.removeLast();
        }
        Long nextLastId = attractionList.isEmpty() ? null : attractionList.getLast().getId();

        List<AttractionListVO> attractionListVOList =
                attractionsConverter.toAttractionListVOList(attractionList);

        return new ScrollResult<>(attractionListVOList, nextLastId, hasMore);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteAttractions(List<Long> attractionIds) {
        Long adminId = AdminContext.getAdminId();
        for (Long attractionId : attractionIds) {
            LambdaQueryWrapper<Attraction> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Attraction::getId, attractionId);
            queryWrapper.eq(Attraction::getAdminId, adminId);
            deleteAboutAttraction(attractionId, adminId);
            int delete = attractionsMapper.delete(queryWrapper);
            if (delete == 0) {
                throw new ServiceException("删除景点信息失败,可能为景点不存在或权限不符, attractionId: " + attractionId);
            }
        }
    }

    protected void deleteAboutAttraction(Long attractionId, Long adminId) {
        //删除相关文档信息
        LambdaQueryWrapper<AttractionDocument> lqw = new LambdaQueryWrapper<>();
        lqw.eq(AttractionDocument::getAttractionId, attractionId);
        lqw.eq(AttractionDocument::getAdminId, adminId);
        List<AttractionDocument> attractionDocuments = attractionDocumentMapper.selectList(lqw);
        if (!attractionDocuments.isEmpty()) {
            attractionDocuments.forEach(attractionDocument -> {
                    deleteDocument(attractionDocument.getId());
            });
        }

        //删除FAQ数据显示
        statInternalService.deleteFaqDailyStats(attractionId);
        //删除相关faq信息
        LambdaQueryWrapper<AttractionFaq> fqLqw = new LambdaQueryWrapper<>();
        fqLqw.eq(AttractionFaq::getAttractionId, attractionId);
        attractionFaqMapper.delete(fqLqw);

        //删除数字人并通知 MuseTalk 清理缓存
        LambdaQueryWrapper<DigitalHuman> dhLqw = new LambdaQueryWrapper<>();
        dhLqw.eq(DigitalHuman::getAttractionId, attractionId);
        dhLqw.eq(DigitalHuman::getAdminId, adminId);
        DigitalHuman digitalHuman = adminDigitalHumanMapper.selectOne(dhLqw);
        if (digitalHuman != null) {
            if (StringUtils.hasText(digitalHuman.getVideoUrl())) {
                fileStorageService.delete(digitalHuman.getVideoUrl());
            }
            if (StringUtils.hasText(digitalHuman.getAudioUrl())) {
                fileStorageService.delete(digitalHuman.getAudioUrl());
            }
            adminDigitalHumanMapper.delete(dhLqw);
            try {
                rabbitTemplate.convertAndSend(
                        MqConstants.VIDEO_DELETE_QUEUE,
                        new VideoDeleteMessage(digitalHuman.getId())
                );
            } catch (Exception e) {
                log.error("删除景点时发送数字人删除消息失败, attractionId: {}", attractionId, e);
            }
        }
    }
}
