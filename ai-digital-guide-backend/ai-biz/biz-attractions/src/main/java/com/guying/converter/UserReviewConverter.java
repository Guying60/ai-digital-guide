package com.guying.converter;

import com.guying.pojo.entity.UserReview;
import com.guying.pojo.vo.UserReviewVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserReviewConverter {

    UserReviewVO toVO(UserReview entity);

    List<UserReviewVO> toVOList(List<UserReview> entities);
}
