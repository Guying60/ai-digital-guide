# AI 数字人音视频接口文档

> 版本: 1.0 | 更新日期: 2026-06-09

---

## 目录

1. [架构概览](#1-架构概览)
2. [客户端 ↔ Java 后端（WebSocket）](#2-客户端--java-后端websocket)
3. [Java 后端 ↔ MuseTalk（WebSocket）](#3-java-后端--musetalkwebsocket)
4. [Java 后端 ↔ CosyVoice（WebSocket）](#4-java-后端--cosyvoicewebsocket)
5. [MuseTalk 视频推理服务（Python HTTP/WS）](#5-musetalk-视频推理服务)
6. [CosyVoice TTS 语音合成服务（Python HTTP/WS）](#6-cosyvoice-tts-语音合成服务)
7. [REST API 接口](#7-rest-api-接口)
8. [RabbitMQ 异步任务接口](#8-rabbitmq-异步任务接口)
9. [二进制帧协议](#9-二进制帧协议)
10. [附录：配置项](#10-附录配置项)

---

## 1. 架构概览

```
┌─────────────────┐
│   客户端 (Android)  │
└────────┬────────┘
         │ ws://host:8080/ai-project/chat
         ▼
┌─────────────────────────────────────────┐
│         Java 后端 (Spring Boot)          │
│                                         │
│  AiChatHandler                          │
│    ├─→ NLS 语音识别 (阿里云)              │
│    ├─→ LLM/VL 大模型                     │
│    ├─→ CosyVoiceConnector ──┐           │
│    └─→ MuseTalkConnector ──┐│           │
│                            ││           │
│  (透传音频/视频给客户端)    ││           │
└────────────────────────────┼┼───────────┘
                             ││
         ┌───────────────────┘│
         ▼                    ▼
┌─────────────────┐  ┌─────────────────┐
│ CosyVoice (Python) │  │ MuseTalk (Python)  │
│ 端口 6008         │  │ 端口 6006         │
│ TTS 语音合成      │  │ 唇形视频推理       │
└─────────────────┘  └─────────────────┘
```

**数据流：**

1. 用户语音/文字 → Java 后端 → LLM 生成回复文本
2. 回复文本 → CosyVoice → PCM 音频（透传给客户端播放）
3. PCM 音频 → MuseTalk → H.264 视频帧（透传给客户端渲染）

音视频数据采用**纯透传**模式，后端不做排队和音画同步，Jitter Buffer 职责下放给客户端。

---

## 2. 客户端 ↔ Java 后端（WebSocket）

### 2.1 连接信息

| 项目 | 值 |
|------|-----|
| 端点 | `ws://host:8080/ai-project/chat` |
| 认证 | URL 参数 `token=<JWT>` 或 Header `Authorization: Bearer <JWT>` |
| 连接参数 | `attractionId`（必填，景点 ID） |
| 空闲超时 | 30 分钟无活动自动断开 |

**连接示例：**

```
ws://host:8080/ai-project/chat?token=eyJhbGci...&attractionId=42
```

### 2.2 客户端 → 服务端（JSON 文本帧）

统一格式：`{"type": "<type>", ...}`

| type | 附加字段 | 说明 |
|------|----------|------|
| `text` | `text: string` — 用户输入文本 | 文本聊天 |
| `micOn` | 无 | 开启麦克风，创建 NLS 语音识别器 |
| `micOff` | 无 | 关闭麦克风，停止 NLS |
| `photo` | `photo: string` — Base64 编码图片 | 发送照片（供 VL 模型视觉分析） |
| `camera` | `status: "off"` | 关闭摄像头，清除待处理图片 |
| `ping` | 无 | 心跳 |
| `interrupt` | 无 | 主动打断数字人（停止当前 TTS + MuseTalk 推理） |

**示例 — 文本聊天：**

```json
{"type": "text", "text": "你好，请介绍一下这个景点"}
```

**示例 — 打断：**

```json
{"type": "interrupt"}
```

### 2.3 客户端 → 服务端（二进制帧）

- 格式：裸 PCM 音频数据
- 参数：16kHz 采样率，16bit 有符号小端（s16le），单声道
- 用途：麦克风采集的实时音频，服务端转发给阿里云 NLS 语音识别

### 2.4 服务端 → 客户端（JSON 文本帧）

统一格式：`{"type": "<type>", "text": "<text>"}`

| type | text 字段 | 说明 |
|------|-----------|------|
| `allDone` | `null` | 所有初始化完成（MuseTalk + CosyVoice 连接就绪） |
| `ready` | `null` | MuseTalk 端发送 ready，数字人可以开始 |
| `speechStarted` | `null` | NLS 检测到一句话开始 |
| `interimText` | `string` — 实时识别文本 | NLS 中间识别结果（实时字幕） |
| `userInput` | `string` — 最终识别文本 | NLS 一句话识别完成 |
| `aiOutput` | `string` — AI 回复的一个句子 | AI 回复（按标点切句，逐句下发） |
| `responseDone` | `null` | AI 整个回复完成 |
| `done` | `null` | MuseTalk 视频生成完成（单句） |
| `pong` | `null` | 心跳响应 |
| `error` | `string` — 错误消息 | 错误通知 |
| `connectionError` | `"连接发生错误"` | 传输层错误 |

**示例 — AI 回复流程：**

```json
{"type": "aiOutput", "text": "欢迎来到故宫博物院，"}
{"type": "aiOutput", "text": "这是中国最大的古代宫殿建筑群。"}
{"type": "responseDone"}
```

### 2.5 服务端 → 客户端（二进制帧）

二进制帧分为**音频帧**和**视频帧**，通过首字节区分：

| 首字节 | 类型 | 说明 |
|--------|------|------|
| 非 `0x03` | 音频帧 | CosyVoice 原始包，含 7 字节 header + PCM 数据 |
| `0x03` | 视频帧 | MuseTalk 视频帧，含 1 字节标识 + 7 字节 header + H.264 AU |

---

## 3. Java 后端 ↔ MuseTalk（WebSocket）

### 3.1 连接信息

| 项目 | 值 |
|------|-----|
| 端点 | 配置项 `spring.museTalk.ws-url` |
| 角色 | Java 后端作为 WebSocket **客户端**连接 Python 服务 |

### 3.2 Java 后端 → MuseTalk（JSON 文本帧）

| type | 附加字段 | 说明 |
|------|----------|------|
| `init` | `attraction_id: string` | 连接建立后立即发送，初始化数字人 |
| `audio_end` | `sentence_id: int` | 通知一个句子的音频发送完毕，触发推理 |
| `interrupt` | `attraction_id: string`, `session_id: string` | 用户打断，停止当前生成并清空队列 |
| `pong` | 无 | 心跳响应 |

### 3.3 Java 后端 → MuseTalk（二进制帧）

- 格式：裸 PCM 音频数据（从 CosyVoice 响应中剥离 header 后得到）
- 参数：16kHz 采样率，单声道，s16le
- 流程：一个句子的 PCM 数据发送完毕后，发送 `audio_end` 文本消息

### 3.4 MuseTalk → Java 后端（JSON 文本帧）

| type | 附加字段 | 说明 |
|------|----------|------|
| `ready` | 无 | 数字人 avatar 初始化完成 |
| `done` | `sentence_id: int`（可选） | 单句视频生成完成 |
| `ping` | 无 | 心跳（每 20 秒），需回复 `pong` |
| `error` | `message: string` | 错误信息 |

### 3.5 MuseTalk → Java 后端（二进制帧）

- 格式：7 字节 header + H.264 access unit
- Java 后端收到后组装为 `[0x03][header + H.264 AU]` 透传给客户端

---

## 4. Java 后端 ↔ CosyVoice（WebSocket）

### 4.1 连接信息

| 项目 | 值 |
|------|-----|
| 端点 | 配置项 `spring.cosyVoice.ws-url` |
| 角色 | Java 后端作为 WebSocket **客户端**连接 Python 服务 |
| 二进制缓冲区 | 5MB |

### 4.2 Java 后端 → CosyVoice（JSON 文本帧）

| type | 附加字段 | 说明 |
|------|----------|------|
| `init` | `attraction_id: string`, `session_id: string` | 连接建立后立即发送，初始化 TTS 会话 |
| `synthesize` | `text: string`, `attraction_id?: string`, `session_id?: string` | 请求合成语音 |
| `interrupt` | `attraction_id: string`, `session_id: string` | 用户打断，停止当前合成并清空缓冲 |

**示例 — 合成请求：**

```json
{"type": "synthesize", "text": "欢迎来到故宫博物院", "attraction_id": "42", "session_id": "abc123"}
```

### 4.3 CosyVoice → Java 后端（JSON 文本帧）

| type | 附加字段 | 说明 |
|------|----------|------|
| `chunk_end` | `sentence_id: int`, `session_id?: string` | 一句合成完毕 |
| `ping` | 无 | 心跳，需回复 `pong` |
| `error` | `message: string`, `session_id?: string` | 错误信息 |

### 4.4 CosyVoice → Java 后端（二进制帧）

- 格式：7 字节 header + PCM 音频数据
- Java 后端处理：
  1. **完整包（含 header）直接透传给客户端**（用于音频播放）
  2. **剥离 header 后的裸 PCM 缓冲起来**，等 `chunk_end` 时整句发给 MuseTalk（用于口型同步）

### 4.5 CosyVoice → MuseTalk 转发流程

当 Java 后端收到 CosyVoice 的 `chunk_end` 消息时：

1. 将累积的裸 PCM 数据作为二进制帧发送给 MuseTalk
2. 发送 `{"type": "audio_end", "sentence_id": <id>}` 通知 MuseTalk 该句音频结束

---

## 5. MuseTalk 视频推理服务

> Python FastAPI 服务，端口 **6006**

### 5.1 WebSocket 接口：`/ws/infer`

核心的实时唇形视频推理接口。

#### 连接流程

```
客户端连接
    │
    ├─→ 发送 {"type": "init", "attraction_id": "42"}
    │
    ◄── 服务端返回 {"type": "ready"}
    │
    ├─→ 流式发送二进制帧（PCM 音频数据）
    │
    ├─→ 发送 {"type": "audio_end", "sentence_id": 0}
    │
    ◄── 服务端流式返回二进制帧（H.264 视频）
    │
    ◄── 服务端返回 {"type": "done", "sentence_id": 0}
    │
    ├─→ 发送 {"type": "interrupt"}  （可选，打断）
    │
    ◄── 服务端每 20 秒发 {"type": "ping"}
    ├─→ 客户端回复 {"type": "pong"}
```

#### 客户端 → 服务端（JSON 文本帧）

| type | 参数 | 说明 |
|------|------|------|
| `init` | `attraction_id: string` | 初始化指定 avatar，触发模型加载 |
| `audio_end` | `sentence_id: int` | 通知音频发送完毕，触发推理 |
| `interrupt` | 无 | 中断当前推理，清空队列和音频缓冲区 |
| `pong` | 无 | 心跳响应 |

#### 服务端 → 客户端（JSON 文本帧）

| type | 参数 | 说明 |
|------|------|------|
| `ready` | 无 | avatar 加载完成，可以开始发送音频 |
| `ping` | 无 | 心跳（每 20 秒发一次） |
| `done` | `sentence_id: int` | 该句推理完成 |
| `error` | `message: string` | 错误信息 |

#### 心跳机制

- 服务端每 20 秒发送 `ping`
- 客户端需回复 `pong`
- 45 秒未收到 `pong` 则服务端主动断开连接

### 5.2 HTTP 接口：`GET /admin/test-video/{attraction_id}`

下载指定景点的测试视频文件。

| 项目 | 值 |
|------|-----|
| 路径参数 | `attraction_id: string` — 景点/数字人 ID |
| 成功响应 | `200`，`Content-Type: video/mp4`，MP4 文件流 |
| 失败响应 | `404`，`{"detail": "测试视频不存在"}` |

---

## 6. CosyVoice TTS 语音合成服务

> Python FastAPI 服务，端口 **6008**

### 6.1 HTTP 接口

| 方法 | 路径 | 说明 | 响应 |
|------|------|------|------|
| `GET` | `/` | 服务状态 | `{"service": "cosyvoice-tts", "status": "ok"}` |
| `GET` | `/health` | 健康检查 | `{"status": "ok"\|"loading", "voices": [...]}` |
| `GET` | `/voices` | 列出可用音色 | `{"voices": ["voice1", "voice2", ...]}` |
| `POST` | `/voices/reload` | 热加载音色文件 | `{"reloaded": <report>}` |
| `POST` | `/tts/offline` | 离线 TTS | `audio/wav` 文件流 |

#### `POST /tts/offline` 请求体

```json
{
  "text": "要合成的文本",
  "attraction_id": "景点ID（可选）"
}
```

- 响应：`audio/wav` 二进制文件（16kHz 单声道 s16le）
- 文本为空时返回 `400`

### 6.2 WebSocket 接口：`/ws/tts`

流式 TTS 语音合成，实时返回 PCM 音频流。

#### 客户端 → 服务端（JSON 文本帧）

| type | 参数 | 说明 |
|------|------|------|
| `init` | `attraction_id?: string` | 初始化会话，设置默认音色 |
| `synthesize` | `text: string`, `attraction_id?: string`, `session_id?: string` | 发起一次 TTS 合成 |
| `interrupt` | 无 | 中断当前合成，清空队列 |
| `ping` | 无 | 心跳请求 |
| `pong` | 无 | 心跳响应 |

#### 服务端 → 客户端（JSON 文本帧）

| type | 参数 | 说明 |
|------|------|------|
| `pong` | 无 | 心跳响应 |
| `chunk_end` | `sentence_id: int`, `session_id?: string` | 一句合成完毕 |
| `error` | `message: string`, `session_id?: string` | 错误信息 |

---

## 7. REST API 接口

> 基础路径：`http://host:8080/ai-project`

### 7.1 数字人管理（Admin）

**基础路径：** `/v1/admins/attractions/digital-human`

#### `POST /` — 新增/更新数字人

**请求体：**

```json
{
  "id": 123,
  "ossUrl": "https://oss.example.com/avatar.png",
  "attractionId": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | `long` | 否 | 有则更新，无则新增 |
| `ossUrl` | `string` | 是 | 数字人图片/视频 URL |
| `attractionId` | `long` | 是 | 关联景点 ID |

**响应：**

```json
{
  "code": 0,
  "data": {
    "id": 123,
    "ossUrl": "https://oss.example.com/avatar.png"
  }
}
```

**副作用：** 通过 RabbitMQ 发送预加载消息，触发 MuseTalk 预加载视频模型。

#### `GET /{attractionId}` — 查询数字人详情

| 路径参数 | 类型 | 说明 |
|----------|------|------|
| `attractionId` | `long` | 景点 ID |

**响应：** `Result<DigitalHumanVO>`

#### `GET /preload-status/{attractionId}` — 检查预加载状态

**响应：**

```json
{
  "code": 0,
  "data": "SUCCESS"
}
```

| 状态值 | 说明 |
|--------|------|
| `PROCESSING` | 预加载进行中 |
| `SUCCESS` | 预加载成功 |
| `FAILED` | 预加载失败 |

#### `POST /test-video/{attractionId}` — 触发测试视频生成

**请求体：**

```json
{
  "text": "这是一段测试文本"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `text` | `string` | 否 | 为空时使用默认文本 |

**响应：** `{"code": 0, "data": "任务已提交"}`

#### `GET /test-video-status/{attractionId}` — 检查测试视频状态

**响应：**

```json
{
  "code": 0,
  "data": {
    "status": "SUCCESS",
    "videoUrl": "/ai-project/v1/admins/attractions/digital-human/test-video-file/42"
  }
}
```

#### `GET /test-video-file/{attractionId}` — 获取测试视频文件

| 项目 | 值 |
|------|-----|
| 响应 | `200`，`Content-Type: video/mp4`，MP4 文件流 |
| 代理 | 实际转发到 Python 端 `GET /admin/test-video/{attractionId}` |

### 7.2 聊天历史管理

**基础路径：** `/v1/users/chat-history`

#### `GET /{conversationId}` — 获取聊天历史

**响应：**

```json
{
  "code": 0,
  "data": [
    { "role": "user", "content": "你好" },
    { "role": "assistant", "content": "你好！欢迎来到故宫博物院。" }
  ]
}
```

#### `DELETE /{conversationId}` — 删除聊天历史

---

## 8. RabbitMQ 异步任务接口

### 8.1 预加载队列 `video.preload.queue`

**触发时机：** 数字人新增/更新时

**消息体：**

```json
{
  "attractionId": "景点ID",
  "videoUrl": "视频下载地址"
}
```

**处理流程：** 下载视频 → 加载 avatar 模型 → 抽取音频 → 通知 CosyVoice 热加载 → Redis 写入状态

**Redis 状态键：** `digital_human:preload_status:{attraction_id}`

| 值 | 说明 |
|-----|------|
| `PROCESSING` | 处理中 |
| `SUCCESS` | 成功 |
| `FAILED` | 失败 |

TTL：1800 秒

### 8.2 测试视频生成队列 `video.test.queue`

**消息体：**

```json
{
  "attractionId": "景点ID",
  "testText": "测试文本（可选）"
}
```

**处理流程：** 调用 CosyVoice `/tts/offline` 获取 TTS 音频 → 生成测试视频 → Redis 写入状态

**Redis 状态键：** `digital_human:test_video_status:{attraction_id}`

### 8.3 删除队列 `video.delete.queue`

**消息体：**

```json
{
  "attractionId": "景点ID"
}
```

**处理流程：** 清除 avatar 缓存 → 删除视频/音频文件 → 通知 CosyVoice 热加载 → 清除 Redis 状态

---

## 9. 二进制帧协议

### 9.1 音频帧（CosyVoice → 客户端）

```
┌──────────────────────────────────────────────────────────────┐
│  sentence_id  │      pts_ms       │ is_keyframe │  PCM data  │
│   2 bytes     │     4 bytes       │   1 byte    │  N bytes   │
│   uint16 BE   │    uint32 BE      │   uint8     │ s16le 16kHz│
└──────────────────────────────────────────────────────────────┘
│◄──────────── 7 字节 header ────────────────►│◄── payload ──►│
```

| 字段 | 偏移 | 长度 | 类型 | 说明 |
|------|------|------|------|------|
| `sentence_id` | 0 | 2 | uint16 BE | 句子编号（0-65535 循环），同一句所有 chunk 相同 |
| `pts_ms` | 2 | 4 | uint32 BE | 该 chunk 在本句中的起始毫秒偏移 |
| `is_keyframe` | 6 | 1 | uint8 | 对音频帧无实际意义 |
| PCM data | 7 | N | s16le | 16kHz 单声道裸 PCM |

### 9.2 视频帧（MuseTalk → 客户端）

```
┌──────────────────────────────────────────────────────────────────────┐
│  type  │  sentence_id  │      pts_ms       │ is_keyframe │ H.264 AU │
│ 0x03   │   2 bytes     │     4 bytes       │   1 byte    │  N bytes │
│ 1 byte │   uint16 BE   │    uint32 BE      │   uint8     │ Annex-B  │
└──────────────────────────────────────────────────────────────────────┘
│◄1B►│◄──────────────── 7 字节 header ────────────────►│◄── payload ──►│
```

| 字段 | 偏移 | 长度 | 类型 | 说明 |
|------|------|------|------|------|
| `type` | 0 | 1 | uint8 | 固定 `0x03`，标识为视频帧 |
| `sentence_id` | 1 | 2 | uint16 BE | 句子编号 |
| `pts_ms` | 3 | 4 | uint32 BE | 该帧在本句中的毫秒偏移 |
| `is_keyframe` | 7 | 1 | uint8 | `1` = 关键帧（IDR），`0` = 非关键帧 |
| H.264 AU | 8 | N | bytes | H.264 Annex-B access unit |

### 9.3 客户端帧类型判断

```
读取第一个字节:
  if byte == 0x03 → 视频帧，按 9.2 解析
  else           → 音频帧，按 9.1 解析（该字节是 sentence_id 的高字节）
```

### 9.4 时间戳说明

- 视频帧率：**25 FPS**，每帧间隔 40ms
- `pts_ms` 从 0 开始，每帧递增 40
- 音频 `pts_ms` 计算公式：`cumulative_pcm_bytes / 32`（16000Hz × 1ch × 2bytes = 32000 bytes/s = 32 bytes/ms）
- 音频和视频的 `sentence_id` 用于配对同一句话的音画数据

---

## 10. 附录：配置项

### 10.1 Java 后端配置（application.yml）

```yaml
server:
  servlet:
    context-path: /ai-project

spring:
  museTalk:
    ws-url: ${MUSE_TALK_WS_URL}          # ws://host:6006/ws/infer
    http-url: ${MUSE_TALK_HTTP_URL}       # http://host:6006
  cosyVoice:
    ws-url: ${COSY_VOICE_WS_URL}         # ws://host:6008/ws/tts
```

### 10.2 MuseTalk 配置（Python 环境变量）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `TARGET_W` | - | 视频输出宽度 |
| `TARGET_H` | - | 视频输出高度 |

### 10.3 CosyVoice 配置（Python 环境变量）

| 配置项 | 环境变量 | 默认值 |
|--------|---------|--------|
| CosyVoice 仓库路径 | `COSYVOICE_REPO` | `/root/autodl-tmp/CosyVoice` |
| 模型目录 | `COSYVOICE_MODEL_DIR` | `.../pretrained_models/Fun-CosyVoice3-0.5B` |
| 音色目录 | `COSYVOICE_VOICES_DIR` | `/root/autodl-tmp/voices` |
| 默认音色 | `COSYVOICE_DEFAULT_VOICE` | `default` |
| 合成语速 | `TTS_SPEED` | `1.0` |
| 音频自然化 | `ENABLE_AUDIO_NATURALIZATION` | `true` |

### 10.4 阿里云 NLS 配置

| 项目 | 值 |
|------|-----|
| 连接地址 | `wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1` |
| 音频格式 | PCM, 16kHz, 16bit |
| 低能量帧丢弃阈值 | RMS 15.0 |
