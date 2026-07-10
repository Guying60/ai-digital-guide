package com.guying.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.guying.attractions.dto.AttractionReviewAggregateDTO;
import com.guying.common.result.ScrollResult;
import com.guying.converter.UserAttractionsConverter;
import com.guying.mapper.AttractionsMapper;
import com.guying.mapper.UserReviewMapper;
import com.guying.pojo.dto.AttractionsPageQueryDTO;
import com.guying.pojo.entity.Attraction;
import com.guying.pojo.vo.AttractionsAroundPageQueryVO;
import com.guying.service.UserAttractionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserAttractionsServiceImpl extends ServiceImpl<AttractionsMapper, Attraction> implements UserAttractionsService {
    @Autowired
    private AttractionsMapper attractionsMapper;
    @Autowired
    private UserAttractionsConverter userAttractionsConverter;
    @Autowired
    private UserReviewMapper userReviewMapper;


    /**
     * 获取周围景点列表
     * @param attractionsPageQueryDTO
     * @return
     */
    @Override
    public ScrollResult<AttractionsAroundPageQueryVO> getAttractionsAround(AttractionsPageQueryDTO attractionsPageQueryDTO) {
        int pageSize = attractionsPageQueryDTO.getPageSize();
        Long lastId = StringUtils.hasText(attractionsPageQueryDTO.getLastId())
                ? Long.valueOf(attractionsPageQueryDTO.getLastId())
                : null;

        // 不限城市,按距离用户由近到远排序,基于(距离,id)游标分页
        List<Attraction> attractions = attractionsMapper.selectAround(
                attractionsPageQueryDTO.getUserLongitude(),
                attractionsPageQueryDTO.getUserLatitude(),
                attractionsPageQueryDTO.getKeyWord(),
                attractionsPageQueryDTO.getLastDistance(),
                lastId,
                pageSize + 1);

        boolean hasMore = attractions.size() > pageSize;
        if (hasMore) {
            attractions.removeLast();
        }
        Attraction last = attractions.isEmpty() ? null : attractions.getLast();
        Long nextLastId = last == null ? null : last.getId();
        Double nextDistance = last == null ? null : last.getDistance();

        List<AttractionsAroundPageQueryVO> attractionsAroundPageQueryVOList =
                userAttractionsConverter.toAttractionsAroundPageQueryVOList(attractions);

        // 批量回填平均分与评论数(全量口径)
        enrichReviewAggregates(attractionsAroundPageQueryVOList);

        return new ScrollResult<>(attractionsAroundPageQueryVOList, nextLastId, hasMore, nextDistance);
    }

    /**
     * 回填景点平均分与评论数(全量口径,单次批量查询避免 N+1)。
     */
    private void enrichReviewAggregates(List<AttractionsAroundPageQueryVO> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        List<Long> ids = voList.stream().map(AttractionsAroundPageQueryVO::getId).toList();
        Map<Long, AttractionReviewAggregateDTO> aggMap = userReviewMapper
                .selectBatchAggregates(ids).stream()
                .collect(Collectors.toMap(AttractionReviewAggregateDTO::getAttractionId, a -> a));
        for (AttractionsAroundPageQueryVO vo : voList) {
            AttractionReviewAggregateDTO agg = aggMap.get(vo.getId());
            if (agg != null) {
                vo.setRating(agg.getAvgScore());
                vo.setReviewCount(agg.getReviewCount().intValue());
            } else {
                vo.setRating(null);
                vo.setReviewCount(0);
            }
        }
    }


}
