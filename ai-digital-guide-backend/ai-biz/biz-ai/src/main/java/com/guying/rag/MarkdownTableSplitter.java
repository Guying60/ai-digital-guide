package com.guying.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Markdown 表格切块器。
 * <p>
 * 解决问题：{@code DocumentVectorListener} 早期对含 {@code |---} 的 Markdown 块整块保留，
 * 当单个表格超过 Embedding 模型输入 token 上限（DashScope {@code text-embedding-v4} 为 8192）时，
 * Spring AI 的 {@code TokenCountBatchingStrategy} 会抛出
 * "Tokens in a single document exceeds the maximum number of allowed input tokens"。
 * <p>
 * 本工具按「行累积 + 表头复用」把大表格切成若干完整 Markdown 表格子块，每个子块都带表头，
 * 既保证不超限，又保证 RAG 检索时拿到的仍是结构完整的表格行。
 */
@Slf4j
public final class MarkdownTableSplitter {

    private MarkdownTableSplitter() {
    }

    /**
     * 单个表格子块的最大字符数。
     * <p>
     * 取 3000：纯中文约对应 3000 token，远低于 8192 上限，留足安全余量；
     * 对中英混排也安全。若后续发现切分过碎可调大，上限约 6000 仍安全。
     */
    public static final int MAX_TABLE_CHUNK_CHARS = 3000;

    /**
     * 把单个 Markdown 表格 {@link Document} 切成多个完整表格子块。
     * <ul>
     *   <li>小表格（整块 ≤ maxCharsPerChunk）原样返回，不拆分。</li>
     *   <li>大表格按数据行累积切分，每个子块复用表头行，保证仍是完整 Markdown 表格。</li>
     *   <li>无法识别为规整表格、或表头+首行即超限等情况，回退到 {@code fallbackSplitter} 兜底。</li>
     * </ul>
     *
     * @param tableDoc          待切的表格文档块（调用方应已确认其包含 {@code |---}）
     * @param maxCharsPerChunk  单个子块的最大字符数（不含表头），建议用 {@link #MAX_TABLE_CHUNK_CHARS}
     * @param fallbackSplitter  兜底切分器，用于无法按表格语义切分的情况
     * @return 切分后的子块列表（每个子块均为新的 {@link Document}，带独立 id 与 metadata 副本）
     */
    public static List<Document> split(Document tableDoc,
                                       int maxCharsPerChunk,
                                       TokenTextSplitter fallbackSplitter) {
        String content = tableDoc.getText();

        // 小表格：整块保留即可，无需切分
        if (content.length() <= maxCharsPerChunk) {
            return List.of(cloneWithText(tableDoc, content));
        }

        // 解析为行
        List<String> lines = splitLines(content);

        // 定位分隔行（首个含 |--- 或 | --- 的行）
        int separatorIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("|---") || line.contains("| ---")) {
                separatorIdx = i;
                break;
            }
        }
        // 无分隔行：不是规整表格，回退兜底
        if (separatorIdx < 0) {
            log.debug("表格块无分隔行，回退兜底切分, fileName={}", tableDoc.getMetadata().get("fileName"));
            return fallbackSplitter.apply(List.of(tableDoc));
        }

        // 表头 = 分隔行及其之前的所有行（含分隔行本身）
        List<String> headerLines = lines.subList(0, separatorIdx + 1);
        // 数据行 = 分隔行之后以 | 开头的非空行
        List<String> dataRows = new ArrayList<>();
        for (int i = separatorIdx + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            if (line.trim().startsWith("|")) {
                dataRows.add(line);
            } else {
                // 分隔行后出现非表格行，视为表格已结束；后续内容不并入本表
                break;
            }
        }

        // 无数据行：回退兜底
        if (dataRows.isEmpty()) {
            log.debug("表格块无数据行，回退兜底切分, fileName={}", tableDoc.getMetadata().get("fileName"));
            return fallbackSplitter.apply(List.of(tableDoc));
        }

        String header = String.join("\n", headerLines);
        int headerLen = header.length();

        // 表头 + 首条数据行即超限：表格本身表头过长或首行过长，回退兜底（兜底可能切在行内，属可接受降级）
        if (headerLen + dataRows.get(0).length() + 1 > maxCharsPerChunk) {
            log.debug("表格块表头+首行即超限({}+{})，回退兜底切分, fileName={}",
                    headerLen, dataRows.get(0).length(), tableDoc.getMetadata().get("fileName"));
            return fallbackSplitter.apply(List.of(tableDoc));
        }

        // 按数据行累积切分
        List<Document> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String row : dataRows) {
            int addLen = row.length() + 1; // +1 为换行符
            // 当前行加入后超限且当前块非空：封口当前块，开启新块
            if (current.length() > 0 && current.length() + addLen > maxCharsPerChunk) {
                result.add(buildChunk(tableDoc, header, current.toString()));
                current.setLength(0);
            }

            // 单行自身超限（巨型单元格）：对该行单独兜底，避免单块超限
            if (headerLen + addLen > maxCharsPerChunk) {
                if (current.length() > 0) {
                    result.add(buildChunk(tableDoc, header, current.toString()));
                    current.setLength(0);
                }
                // 把该行包成独立小表格再兜底切（保证仍是表格语义）
                Document singleRowDoc = cloneWithText(tableDoc, header + "\n" + row);
                result.addAll(fallbackSplitter.apply(List.of(singleRowDoc)));
                continue;
            }

            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(row);
        }
        // 收尾块
        if (current.length() > 0) {
            result.add(buildChunk(tableDoc, header, current.toString()));
        }

        log.debug("表格块切分完成: {} 行 -> {} 个子块, fileName={}",
                dataRows.size(), result.size(), tableDoc.getMetadata().get("fileName"));
        return result;
    }

    /**
     * 构造一个表格子块：表头 + 数据行，新 Document（独立 id + metadata 副本）。
     */
    private static Document buildChunk(Document template, String header, String dataRows) {
        String text = header + "\n" + dataRows;
        return cloneWithText(template, text);
    }

    /**
     * 基于 template 的 metadata 副本，构造一个带新随机 id 的纯文本 Document。
     * <p>
     * 关键：metadata 用新 Map 副本，避免后续监听器往各子块 put 时互相污染；
     * 不复用 template 的 id，保证每个子块在 Redis/MySQL 中可独立按 id 删除。
     */
    private static Document cloneWithText(Document template, String text) {
        return new Document(text, new HashMap<>(template.getMetadata()));
    }

    /**
     * 按行切分（保留每行原始内容，不含换行符）。
     * 同时兼容 \n 与 \r\n。
     */
    private static List<String> splitLines(String content) {
        String[] arr = content.split("\r?\n", -1);
        return new ArrayList<>(List.of(arr));
    }
}
