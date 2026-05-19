package com.guying.websocket.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 流式输出按标点切句。
 * 调用方持有同一个实例，连续投喂大模型增量 delta；
 * 每次返回到目前为止刚好结束的完整句子（可能 0~N 句）。
 */
public class StreamingSentenceSplitter {

    private static final Pattern SPLIT = Pattern.compile("(?<=[，。！？；,!?;])");
    private static final Pattern ENDS_WITH_PUNCT = Pattern.compile(".*[，。！？；,!?;]$");

    private final StringBuilder buffer = new StringBuilder();

    public List<String> consume(String delta) {
        if (delta == null || delta.isEmpty()) {
            return List.of();
        }
        buffer.append(delta);
        String text = buffer.toString();
        String[] parts = SPLIT.split(text);

        List<String> sentences = new ArrayList<>(parts.length);
        // 除最后一段外，全部是完整句子
        for (int i = 0; i < parts.length - 1; i++) {
            sentences.add(parts[i]);
        }
        // 最后一段：若以标点结尾也算完整句子；否则留待后续
        String tail = parts[parts.length - 1];
        if (ENDS_WITH_PUNCT.matcher(tail).matches()) {
            sentences.add(tail);
            buffer.setLength(0);
        } else {
            buffer.setLength(0);
            buffer.append(tail);
        }
        return sentences;
    }

    /** 流结束时取走 buffer 中残留内容（不带尾标点的尾段） */
    public String drain() {
        if (buffer.isEmpty()) return null;
        String tail = buffer.toString();
        buffer.setLength(0);
        return tail;
    }
}
