package com.guying.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guying.pojo.entity.Attraction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AttractionsMapper extends BaseMapper<Attraction> {

    /**
     * 查询全部景点,按距离用户由近到远排序(Haversine),并基于(距离,id)游标分页。
     *
     * @param userLng      用户当前经度(GCJ-02)
     * @param userLat      用户当前纬度(GCJ-02)
     * @param keyWord      景点名称模糊匹配,可空
     * @param lastDistance 游标:上一页最后一条的距离(米),首页传 null
     * @param lastId       游标:上一页最后一条的景点ID,首页传 null
     * @param limit        取数条数(通常为 pageSize+1 用于判断 hasMore)
     * @return 含动态 distance 字段的景点列表
     */
    List<Attraction> selectAround(@Param("userLng") Double userLng,
                                  @Param("userLat") Double userLat,
                                  @Param("keyWord") String keyWord,
                                  @Param("lastDistance") Double lastDistance,
                                  @Param("lastId") Long lastId,
                                  @Param("limit") Integer limit);
}
