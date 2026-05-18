package com.guying.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guying.context.AdminContext;
import com.guying.converter.DigitalHumanConverter;
import com.guying.mapper.AdminDigitalHumanMapper;
import com.guying.pojo.dto.DigitalHumanCreateDTO;
import com.guying.pojo.dto.DigitalHumanUpdateDTO;
import com.guying.pojo.entity.DigitalHuman;
import com.guying.pojo.vo.DigitalHumanVO;
import com.guying.service.AdminDigitalHumanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminDigitalHumanServiceImpl implements AdminDigitalHumanService {
    @Autowired
    private AdminDigitalHumanMapper adminDigitalHumanMapper;
    @Autowired
    private DigitalHumanConverter digitalHumanConverter;

    @Override
    public DigitalHumanVO add(DigitalHumanCreateDTO dto) {
        DigitalHuman entity = digitalHumanConverter.toEntity(dto);
        entity.setAdminId(AdminContext.getAdminId());
        adminDigitalHumanMapper.insert(entity);
        return digitalHumanConverter.toVO(entity);
    }

    @Override
    public void deleteById(Long id) {
        LambdaQueryWrapper<DigitalHuman> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(DigitalHuman::getId, id)
                .eq(DigitalHuman::getAdminId, AdminContext.getAdminId());
        int delete = adminDigitalHumanMapper.delete(lambdaQueryWrapper);
        if (delete == 0) {
            throw new RuntimeException("删除失败，可能权限不符");
        }
    }

    @Override
    public DigitalHumanVO update(DigitalHumanUpdateDTO dto) {
        DigitalHuman entity = digitalHumanConverter.toEntity(dto);
        entity.setAdminId(AdminContext.getAdminId());
        LambdaQueryWrapper<DigitalHuman> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(DigitalHuman::getId, entity.getId())
                .eq(DigitalHuman::getAdminId, AdminContext.getAdminId())
                .eq(DigitalHuman::getAttractionId, entity.getAttractionId());
        int update = adminDigitalHumanMapper.update(entity, lambdaQueryWrapper);
        if (update == 0) {
            throw new RuntimeException("更新失败，可能权限不符");
        }
        return digitalHumanConverter.toVO(entity);
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
