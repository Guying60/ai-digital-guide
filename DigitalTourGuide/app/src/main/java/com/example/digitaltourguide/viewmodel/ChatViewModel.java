package com.example.digitaltourguide.viewmodel;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.digitaltourguide.model.user.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatViewModel extends ViewModel {

    private final MutableLiveData<List<ChatMessage>> messageList=new MutableLiveData<>();
    //主线程handler，用来做流式
    private final Handler mainHandler=new Handler(Looper.getMainLooper());

    public ChatViewModel(){
        //初始化空列表
        messageList.setValue(new ArrayList<>());
    }

    public MutableLiveData<List<ChatMessage>> getMessageList(){
        return messageList;
    }

    //发送问题
    public void sendMessage(String content){
        if(content==null || content.trim().isEmpty()) return;

        List<ChatMessage> list=messageList.getValue();
        if(list==null){
            list=new ArrayList<>();
        }
        //1.添加用户信息
        list.add(new ChatMessage(content,true));
        messageList.postValue(list);

        //2.模拟AI回复
        String aiReply="AI数字人：你说的是->"+content;
        //先加一个空的ai消息占位
        ChatMessage aiMsg=new ChatMessage("",false);
        list.add(aiMsg);
        messageList.postValue(list);

        //流式打字
        typeWriterEffect(aiMsg,aiReply,0,50);
    }
    /**
     * 流式打字机效果
     * @param aiMsg 要更新的AI消息对象
     * @param fullText 完整的AI回复
     * @param index 当前打字到第几个字
     * @param delay 每个字的延迟（毫秒，越小越快）
     */
    private void typeWriterEffect(ChatMessage aiMsg,String fullText,int index,long delay){
        if(index<fullText.length()){
            String currentText=fullText.substring(0,index+1);
            //直接修改ai消息的内容
            aiMsg.setContent(currentText);
            messageList.postValue(messageList.getValue());

            mainHandler.postDelayed(()->{
                typeWriterEffect(aiMsg, fullText, index+1, delay);
            },delay);
        }
    }


}
