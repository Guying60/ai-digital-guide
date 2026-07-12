package com.guying.mq.consumer;

import com.guying.attractions.service.UserAttractionsInternalService;
import com.guying.message.UserTourHistoryMessage;
import com.guying.attractions.dto.AttractionDTO;
import com.guying.common.constants.MqConstants;
import com.guying.mapper.UserTourHistoryMapper;
import com.guying.pojo.entity.UserTourHistory;
import com.guying.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserTourHistoryListener {
    @Autowired
    private UserAttractionsInternalService attractionsInternalService;

    @Autowired
    private UserService userService;


    @RabbitListener(queues = MqConstants.USER_TOUR_HISTORY_QUEUE)
    public void saveUserTourHistory(UserTourHistoryMessage msg) {
        log.info("Received message: {}", msg);
        AttractionDTO attraction = attractionsInternalService.getAttraction(msg.getAttractionId());
        if (attraction == null){
            log.error("Attraction not found for id: {}", msg.getAttractionId());
            return;
        }
        UserTourHistory userTourHistory = new UserTourHistory();
        userTourHistory.setUserId(msg.getUserId());
        userTourHistory.setAttractionId(msg.getAttractionId());
        userTourHistory.setConversationId(msg.getConversationId());
        userTourHistory.setAttractionName(attraction.getAttractionName());
        userTourHistory.setCoverUrl(attraction.getCoverUrl());
        userTourHistory.setType(attraction.getType());
        userTourHistory.setCity(attraction.getCity());
        userTourHistory.setMessageCount(msg.getMessageCount() != null ? msg.getMessageCount() : 0);
        userService.saveUserTourHistory(userTourHistory);
    }

}
