# 	ai-digital-guide 接口文档（完整版）

> **服务器后端部署地址：** `https://ai.guying.xyz`
>
> **以下接口请求 URL 前缀：** `/ai-project/v1`
>
> **鉴权说明：** 除登录、注册接口外，所有请求均需在 Header 中携带 Token：
> `Authorization: <token>`
>
> **雪花ID 注意：** 所有 ID 字段均为雪花 ID（Long），前端请以 **String 类型** 存储和传递，避免精度丢失。

---

[TOC]

---

# 一、用户端接口

## 1.1 用户注册

**请求 URL：** `POST /users/register`

**请求参数（Body / JSON）：**

| 参数名          | 类型   | 必填 | 说明                              |
| --------------- | ------ | ---- | --------------------------------- |
| username        | String | 是   | 账号，4-16 位字母、数字或下划线   |
| password        | String | 是   | 密码，6-20 位字母、数字或特殊字符 |
| confirmPassword | String | 是   | 确认密码，必须与 password 一致    |
| nickname        | String | 是   | 昵称，1-20 个字符                 |

**请求示例：**

```json
{
  "username": "aCwg4dP9",
  "password": "Blx6GD123",
  "confirmPassword": "Blx6GD123",
  "nickname": "小明"
}
```

> 两次密码不一致时返回 `"两次输入的密码不一致"`

**成功响应示例：**

> 拿到响应结果后跳转至登录界面，并将 username 填入输入框

```json
{
  "code": 1,
  "data": {
    "username": "aCwg4dP9"
  },
  "msg": "success"
}
```

---

## 1.2 用户登录

**请求 URL：** `POST /users/login`

**请求参数（Body / JSON）：**

| 参数名   | 类型   | 必填 | 说明 |
| -------- | ------ | ---- | ---- |
| username | String | 是   | 账号 |
| password | String | 是   | 密码 |

**请求示例：**

```json
{
  "username": "asdfghjkl",
  "password": "asdfghjkl"
}
```

**成功响应参数说明：**

| 参数名 | 类型   | 说明                                             |
| ------ | ------ | ------------------------------------------------ |
| token  | String | JWT Token，后续所有请求放入 Authorization Header |
| id     | String | 用户 ID，建立 WebSocket 连接时作为路径参数使用   |

**成功响应示例：**

```json
{
  "code": 1,
  "data": {
    "id": "1234567890123456789",
    "token": "eyJhbGciOiJIUzM4NCJ9.xxx",
  },
  "msg": "success"
}
```

**错误响应示例：**

```json
{
  "code": 400,
  "data": null,
  "msg": "账号格式不正确，需为4-16位字母、数字或下划线"
}
```

> 可直接将 `msg` 弹窗提示给用户

---

## 1.3 用户信息回显

**请求 URL：** `GET /users/userInfo`

**请求参数：** 无

**响应参数说明：**

| 参数名    | 类型    | 说明                    |
| --------- | ------- | ----------------------- |
| nickname  | String  | 昵称（可能为 null）     |
| gender    | Integer | 0:女；1:男；2:未知      |
| age       | Integer | 年龄（可能为 null）     |
| avatarUrl | String  | 头像地址（可能为 null） |

> 以上参数均可能为 null，渲染前请做判空处理

**响应示例：**

```json
{
  "code": 1,
  "data": {
    "nickname": "小明",
    "gender": 1,
    "age": 25,
    "avatarUrl": "https://oss.example.com/avatar/2026/05/21/xxx.jpg"
  },
  "msg": "success"
}
```

---

## 1.4 更新用户信息

**请求 URL：** `PUT /users`

**请求参数（Body / JSON）：**

| 参数名    | 类型    | 必填 | 说明                           |
| --------- | ------- | ---- | ------------------------------ |
| nickname  | String  | 否   | 昵称，1-20 个字符              |
| gender    | Integer | 否   | 0:女；1:男；2:未知             |
| age       | Integer | 否   | 0 到 120                       |
| avatarUrl | String  | 否   | 头像地址，通过 1.11 上传后获取 |

**成功响应示例：**

```json
{
  "code": 1,
  "data": null,
  "msg": "success"
}
```

---

## 1.5 用户获取附近景点列表（按距离排序，游标分页）

**请求 URL：** `GET /users/attractions`

**接口说明：** 前端定位后传入用户当前经纬度与所在城市，**仅返回该城市内的景点**，并按距用户**由近到远**排序。游标基于 `(距离, id)`：翻页时需把上一页返回的 `nextDistance` 与 `nextLastId` 一并回传。

**请求参数（Query）：**

| 参数名        | 类型    | 必填 | 说明                                                         |
| ------------- | ------- | ---- | ------------------------------------------------------------ |
| city          | String  | 是   | 当前所在城市（精确匹配，如 `广州市`），前端定位得到           |
| userLongitude | Double  | 是   | 用户当前经度（GCJ-02 高德坐标系）                            |
| userLatitude  | Double  | 是   | 用户当前纬度（GCJ-02 高德坐标系）                            |
| keyWord       | String  | 否   | 搜索关键词，匹配景点名称，长度不超过 20 个字符               |
| lastDistance  | Double  | 否   | 距离游标，上一页最后一条的 `distance`（米），首次请求不传     |
| lastId        | String  | 否   | 与 `lastDistance` 配合的二级游标（同距离时按 id），首次不传   |
| pageSize      | Integer | 是   | 每页条数，推荐传 6                                           |

> 当用户点击某条景点进入对话时，将列表中的 `id` 作为 `attractionId` 建立 WebSocket 连接。
> 坐标缺失（未定位）的景点不会出现在结果中。

**请求示例：**

```
GET /users/attractions?city=广州市&userLongitude=113.264385&userLatitude=23.129163&pageSize=6
GET /users/attractions?city=广州市&userLongitude=113.264385&userLatitude=23.129163&lastDistance=1523.6&lastId=1234567890123456789&pageSize=6
```

**响应参数说明：**

| 字段              | 类型    | 说明                                        |
| ----------------- | ------- | ------------------------------------------- |
| list[].id         | String  | 景点 ID                                     |
| list[].attractionName | String  | 景点名称                                |
| list[].coverUrl   | String  | 封面图片地址                                |
| list[].distance   | Double  | 距用户的距离（米）                          |
| nextLastId        | String  | 下一页二级游标，为 null 表示无更多数据       |
| nextDistance      | Double  | 下一页距离游标（米），翻页时回传，无更多为 null |
| hasMore           | Boolean | 是否还有更多数据                            |

**响应示例：**

```json
{
  "code": 1,
  "data": {
    "list": [
      {
        "id": "1234567890",
        "attractionName": "故宫博物院",
        "coverUrl": "https://oss.example.com/cover/xxx.jpg",
        "distance": 845.2
      }
    ],
    "nextLastId": "1234567890123456789",
    "nextDistance": 1523.6,
    "hasMore": true
  },
  "msg": "success"
}
```

---

## 1.6 用户获取旅游历史（游标分页）

**请求 URL：** `GET /tourHistory`

**请求参数（Query）：**

| 参数名   | 类型    | 必填 | 说明                                           |
| -------- | ------- | ---- | ---------------------------------------------- |
| keyWord  | String  | 否   | 搜索关键词，匹配景点名称，长度不超过 20 个字符 |
| type     | Integer | 否   | 历史类型筛选，不传则查全部                     |
| city     | String  | 否   | 按城市（地区）筛选，不传则查全部               |
| lastId   | String  | 否   | 游标，上一页最后一条数据的 ID，首次请求不传    |
| pageSize | Integer | 是   | 每页条数                                       |

> `city` 为旅游发生时该景点所属城市（下单时冗余记录）；定位功能上线前的历史记录该字段为空，按城市筛选时不会命中。

**请求示例：**

```
GET /tourHistory?keyWord=故宫&type=1&pageSize=10
GET /tourHistory?city=广州市&pageSize=10
GET /tourHistory?lastId=1234567890123456789&pageSize=10
```

**响应参数说明：**

| 字段                  | 类型    | 说明                              |
| --------------------- | ------- | --------------------------------- |
| list[].id             | String  | 记录 ID                           |
| list[].attractionName | String  | 景点名称                          |
| list[].coverUrl       | String  | 封面图片地址                      |
| list[].conversationId | String  | 对话 ID                           |
| list[].city           | String  | 景点所属城市                      |
| nextLastId            | String  | 下一页游标，为 null 表示无更多数据 |
| hasMore               | Boolean | 是否还有更多数据                  |

**响应示例：**

```json
{
  "code": 1,
  "data": {
    "list": [
      {
        "id": "23123131344",
        "attractionName": "故宫博物院",
        "coverUrl": "https://oss.example.com/cover/xxx.jpg",
        "conversationId": "1234567890123456789",
        "city": "北京市"
      }
    ],
    "nextLastId": "1234567890123456789",
    "hasMore": true
  },
  "msg": "success"
}
```

> `hasMore` 为 `false` 时，展示"没有更多了"并停止触发加载

---

## 1.7 删除用户旅游记录

**请求 URL：** `DELETE /users/tourHistory/delete/{id}`

**请求参数（路径参数）：**

| 参数名 | 类型   | 必填 | 说明           |
| ------ | ------ | ---- | -------------- |
| id     | String | 是   | 旅游历史记录 ID |

**请求示例：** `DELETE /users/tourHistory/delete/1001`

**成功响应示例：**

```json
{
  "code": 1,
  "data": null,
  "msg": "success"
}
```

---

## 1.8 获取 AI 聊天历史

**请求 URL：** `GET /users/ai-history/{conversationId}`

**请求参数（路径参数）：**

| 参数名         | 类型   | 必填 | 说明       |
| -------------- | ------ | ---- | ---------- |
| conversationId | String | 是   | 会话唯一ID |

**响应说明：** 返回 Array，每个元素为一条消息对象，按时间顺序排列。

| 字段    | 类型   | 说明                                   |
| ------- | ------ | -------------------------------------- |
| content | String | 消息内容                               |
| role    | String | `user` 为用户，`assistant` 为 AI 助手  |

**响应示例：**

```json
[
  { "content": "你好", "role": "user" },
  { "content": "你好！我是本景区的 AI 导游，有什么可以帮您？", "role": "assistant" }
]
```

---

## 1.9 删除 AI 聊天记录

**请求 URL：** `DELETE /users/ai-history/{conversationId}`

**请求参数（路径参数）：**

| 参数名         | 类型   | 必填 | 说明       |
| -------------- | ------ | ---- | ---------- |
| conversationId | String | 是   | 会话唯一ID |

**成功响应示例：**

```json
{
  "code": 1,
  "data": null,
  "msg": "success"
}
```

---

## 1.10 上传用户头像

**请求 URL：** `POST /users/file/avatar`

**请求方式：** `multipart/form-data`

**请求参数：**

| 参数名 | 位置      | 类型 | 必填 | 说明                         |
| ------ | --------- | ---- | ---- | ---------------------------- |
| file   | form-data | File | 是   | 头像图片文件，仅支持图片格式（`image/*`） |

**请求示例：**

```
POST /users/file/avatar
Content-Type: multipart/form-data

file: <图片文件>
```

**响应参数说明：**

| 字段 | 类型   | 说明                   |
| ---- | ------ | ---------------------- |
| data | String | 头像图片 OSS 访问地址  |

**成功响应示例：**

```json
{
  "code": 1,
  "data": "https://oss.example.com/avatar/2026/05/21/xxx.jpg",
  "msg": "success"
}
```

**错误响应示例：**

```json
{
  "code": 400,
  "data": null,
  "msg": "文件格式错误，请上传图片"
}
```

> 上传成功后，将 `data` 中的 URL 保存并用于头像展示；上传失败时可直接将 `msg` 弹窗提示用户

---

## 1.11 保存或更新导览偏好

**请求 URL：** `PUT /users/guide-preference`

**请求参数（Body / JSON）：**

| 参数名             | 类型    | 必填 | 说明                                                               |
| ------------------ | ------- | ---- | ------------------------------------------------------------------ |
| guideStyle         | Integer | 否   | 讲解风格：1-专业讲解 2-故事化讲解 3-轻松幽默 4-儿童模式            |
| guideDepth         | Integer | 否   | 讲解深度：1-快速浏览 2-标准讲解 3-深度文化                         |
| interests          | String  | 否   | 兴趣偏好，逗号分隔枚举名，如 `HISTORY_CULTURE,ARCHITECTURE_ART`    |
| travelPurpose      | Integer | 否   | 出游目的：1-学习探索 2-亲子出游 3-拍照打卡 4-休闲放松              |
| specialRequirements| String  | 否   | 特殊需求，不超过 100 个字符                                        |

**interests 可选值：**

| 枚举名               | 中文     |
| -------------------- | -------- |
| HISTORY_CULTURE      | 历史文化 |
| ARCHITECTURE_ART     | 建筑艺术 |
| ROYAL_STORIES        | 皇家故事 |
| HERITAGE_COLLECTION  | 文物收藏 |
| NATURE_ECOLOGY       | 自然生态 |
| PHOTOGRAPHY          | 摄影打卡 |
| MYTHS_LEGENDS        | 神话传说 |
| FOLK_CULTURE         | 民俗风情 |
| FOOD_CULTURE         | 美食文化 |
| INTANGIBLE_HERITAGE  | 非遗文化 |

> 多选用逗号分隔，如 `"HISTORY_CULTURE,ARCHITECTURE_ART,PHOTOGRAPHY"`

**请求示例：**

```json
{
  "guideStyle": 2,
  "guideDepth": 3,
  "interests": "HISTORY_CULTURE,ARCHITECTURE_ART",
  "travelPurpose": 1,
  "specialRequirements": "我对宋代历史特别感兴趣"
}
```

**成功响应示例：**

```json
{
  "code": 1,
  "data": null,
  "msg": "success"
}
```

> 不存在则新增，已存在则更新（按 userId 去重）

---

## 1.12 查询导览偏好

**请求 URL：** `GET /users/guide-preference`

**请求参数：** 无

**响应参数说明：**

| 参数名             | 类型    | 说明                                     |
| ------------------ | ------- | ---------------------------------------- |
| id                 | String  | 记录 ID                                  |
| guideStyle         | Integer | 讲解风格 code                            |
| guideStyleName     | String  | 讲解风格中文名                           |
| guideDepth         | Integer | 讲解深度 code                            |
| guideDepthName     | String  | 讲解深度中文名                           |
| interests          | String  | 兴趣偏好，逗号分隔枚举名                 |
| travelPurpose      | Integer | 出游目的 code                            |
| travelPurposeName  | String  | 出游目的中文名                           |
| specialRequirements| String  | 特殊需求                                 |

**响应示例：**

```json
{
  "code": 1,
  "data": {
    "id": "2044359968888639999",
    "guideStyle": 2,
    "guideStyleName": "故事化讲解",
    "guideDepth": 3,
    "guideDepthName": "深度文化",
    "interests": "HISTORY_CULTURE,ARCHITECTURE_ART",
    "travelPurpose": 1,
    "travelPurposeName": "学习探索",
    "specialRequirements": "我对宋代历史特别感兴趣"
  },
  "msg": "success"
}
```

> 用户未设置过偏好时，`data` 为 `null`

---

## 1.13 获取我的评价列表（游标分页）

**请求 URL：** `GET /users/reviews`

**认证方式：** Bearer Token（用户 JWT）

**接口说明：** 游标分页获取当前用户的评价列表，支持按状态筛选。AI 对话结束时自动创建"待评价"记录，用户提交后变为"已评价"。

**请求参数（Query）：**

| 参数名   | 类型    | 必填 | 说明                                           |
| -------- | ------- | ---- | ---------------------------------------------- |
| lastId   | String  | 否   | 游标，上一页最后一条数据的 ID，首次请求不传    |
| pageSize | Integer | 否   | 每页条数，默认 10                              |
| status   | Integer | 否   | 筛选状态：不传=全部，0=待评价，1=已评价        |

**请求示例：**

```
GET /users/reviews?pageSize=10
GET /users/reviews?status=1&pageSize=10
GET /users/reviews?status=0&lastId=90000000000000005&pageSize=10
```

**响应参数说明（ScrollResult\<UserReviewVO\>）：**

| 字段                  | 类型           | 说明                                     |
| --------------------- | -------------- | ---------------------------------------- |
| list[].id             | String         | 评价记录 ID（雪花ID）                    |
| list[].attractionId   | String         | 景点 ID                                  |
| list[].attractionName | String         | 景点名称                                 |
| list[].coverUrl       | String         | 景点封面图片地址                          |
| list[].rating         | BigDecimal     | 评分 1.0-5.0，待评价时为 null            |
| list[].content        | String         | 评价内容，待评价时为 null                |
| list[].tags           | List\<String\> | 标签列表，如 `["讲解专业","风景不错"]`，待评价时为 null |
| list[].status         | Integer        | 0=待评价，1=已评价                       |
| list[].createTime     | String         | 创建时间，格式 `yyyy-MM-ddTHH:mm:ss`     |
| nextLastId            | String         | 下一页游标，为 null 表示无更多数据        |
| hasMore               | Boolean        | 是否还有更多数据                          |

**响应示例：**

```json
{
  "code": 1,
  "data": {
    "list": [
      {
        "id": "90000000000000001",
        "attractionId": "2046935279750139906",
        "attractionName": "故宫博物院",
        "coverUrl": "https://oss.example.com/cover/xxx.jpg",
        "rating": 4.0,
        "content": "讲解很专业，体验不错",
        "tags": ["讲解专业", "风景不错"],
        "status": 1,
        "createTime": "2026-05-13T14:54:39"
      },
      {
        "id": "90000000000000002",
        "attractionId": "2046935279750139907",
        "attractionName": "颐和园",
        "coverUrl": "https://oss.example.com/cover/yyy.jpg",
        "rating": null,
        "content": null,
        "tags": null,
        "status": 0,
        "createTime": "2026-05-14T10:30:00"
      }
    ],
    "nextLastId": "90000000000000002",
    "hasMore": true
  },
  "msg": "success"
}
```

> `hasMore` 为 `false` 时，展示"没有更多了"并停止触发加载

---

## 1.14 提交评价

**请求 URL：** `POST /users/reviews/submit`

**认证方式：** Bearer Token（用户 JWT）

**接口说明：** 将"待评价"记录提交为"已评价"，补充评分、内容和标签。需先通过 1.13 获取待评价记录的 `id`。

**请求体（Body / JSON）：**

| 字段     | 类型           | 必填 | 说明                                 |
| -------- | -------------- | ---- | ------------------------------------ |
| reviewId | Long           | 是   | 评价记录 ID（来自 1.13 返回的 id）   |
| rating   | BigDecimal     | 是   | 评分，1.0-5.0，支持 0.5 步进        |
| content  | String         | 是   | 评价内容，不超过 500 字              |
| tags     | List\<String\> | 否   | 标签列表，最多 5 个                  |

**请求示例：**

```json
{
  "reviewId": 90000000000000002,
  "rating": 4.5,
  "content": "风景优美，AI 导游讲解非常详细，推荐！",
  "tags": ["讲解专业", "风景不错", "值得推荐"]
}
```

**成功响应示例：**

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

**错误响应示例：**

```json
{
  "code": 500,
  "msg": "该评价已提交，请勿重复提交",
  "data": null
}
```

---

## 1.15 删除评价

**请求 URL：** `DELETE /users/reviews/{reviewId}`

**认证方式：** Bearer Token（用户 JWT）

**接口说明：** 删除一条评价记录（逻辑删除）。已评价和待评价均可删除。

**请求参数（路径参数）：**

| 参数名   | 类型   | 必填 | 说明         |
| -------- | ------ | ---- | ------------ |
| reviewId | String | 是   | 评价记录 ID  |

**请求示例：** `DELETE /users/reviews/90000000000000001`

**成功响应示例：**

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

**错误响应示例：**

```json
{
  "code": 500,
  "msg": "评价记录不存在",
  "data": null
}
```

---

## 1.16 AI 路线推荐（灵动岛数轴）

> **功能概述**：依据用户个性化偏好，从知识库文档中由 AI 结构化输出一串有序地标，前端以「灵动岛」数轴形式展示（含 已到达/进行中/未到达 状态）。**路线的生成与状态推送走 WebSocket**（与数字人对话同一连接 `/chat`），仅在 AI 数字人对话页存在；路线生命周期跟随 WS 连接，断开即清理。本节的 REST 接口仅用于刷新/重连后**恢复**当前已激活的路线。
>
> **坐标系说明**：地标坐标由后端调用高德 Web 服务预解析，返回 **GCJ-02（火星坐标）**。前端做定位与到达判定时，**定位取点与坐标比对必须统一使用高德坐标系**（如高德 JS SDK），切勿混用浏览器/系统原生 GPS（WGS-84），否则存在数十~数百米偏移。
>
> **到达判定**：由前端拿地标坐标/名称做定位检测，判定到达后通过 WS `routeArrive` 回报后端持久化；同时应保留手动点选兜底（室内/密集地标场景 GPS 不可靠）。
>
> **降级**：若未配置高德 Key、配额超限或某地标解析失败，对应节点 `resolved=false`、坐标字段为空，路线仍正常下发，前端可回退为自行用地标名定位。

### 1.16.1 恢复当前激活路线（REST）

**请求 URL：** `GET /users/route/current`

**认证方式：** Bearer Token（用户 JWT）

**接口说明：** 返回该用户在指定景点下当前已激活的路线（含各节点最新到达状态）。无激活路线时 `data` 为 `null`。

**请求参数（Query）：**

| 参数名       | 类型   | 必填 | 说明      |
| ------------ | ------ | ---- | --------- |
| attractionId | String | 是   | 景点 ID   |

**请求示例：** `GET /users/route/current?attractionId=80000000000000001`

**成功响应示例：**

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "routeId": "f1c2e0a8-6b3d-4a2e-9c11-7a0b8d2e5f3a",
    "title": "故宫中轴线·皇家建筑精华游",
    "summary": "沿中轴线一路向北，看尽明清皇家建筑的礼制与气派。",
    "attractionId": 80000000000000001,
    "cityName": "北京市",
    "adcode": "110101",
    "centerLng": 116.397026,
    "centerLat": 39.918058,
    "generatedAt": 1718700000000,
    "stops": [
      {
        "stopIndex": 0,
        "name": "午门",
        "searchKeyword": "故宫午门",
        "recommendReason": "故宫正门，了解皇家礼制的第一站。",
        "estimatedMinutes": 15,
        "longitude": 116.397026,
        "latitude": 39.913423,
        "address": "北京市东城区景山前街4号",
        "poiId": "B000A8UIN8",
        "resolved": true,
        "status": "ARRIVED"
      },
      {
        "stopIndex": 1,
        "name": "太和殿",
        "searchKeyword": "故宫太和殿",
        "recommendReason": "俗称金銮殿，紫禁城内规格最高的建筑。",
        "estimatedMinutes": 25,
        "longitude": 116.390742,
        "latitude": 39.917565,
        "address": "北京市东城区故宫内",
        "poiId": "B000A8UIN9",
        "resolved": true,
        "status": "CURRENT"
      }
    ]
  }
}
```

**成功响应参数说明（data：RoutePlanVO）：**

| 字段        | 类型   | 说明                                                  |
| ----------- | ------ | ----------------------------------------------------- |
| routeId     | String | 路线唯一 ID                                           |
| title       | String | 路线标题                                              |
| summary     | String | 路线总览说明                                          |
| attractionId| Long   | 所属景点 ID                                           |
| cityName    | String | 高德锚点城市名（前端定位限定城市用），未解析为 null   |
| adcode      | String | 高德锚点 adcode，未解析为 null                        |
| centerLng   | Number | 景点中心点经度（GCJ-02），未解析为 null               |
| centerLat   | Number | 景点中心点纬度（GCJ-02），未解析为 null               |
| generatedAt | Long   | 生成时间（epoch 毫秒）                                |
| stops       | Array  | 有序地标节点，见下表                                  |

**stops[i]（RouteStopVO）：**

| 字段             | 类型    | 说明                                                       |
| ---------------- | ------- | ---------------------------------------------------------- |
| stopIndex        | Number  | 0 基序号，按游览顺序                                       |
| name             | String  | 地标展示名                                                 |
| searchKeyword    | String  | 利于地图检索的具体地点名                                   |
| recommendReason  | String  | 推荐理由                                                   |
| estimatedMinutes | Number  | 建议游览时长（分钟）                                       |
| longitude        | Number  | 经度（GCJ-02），未解析为 null                              |
| latitude         | Number  | 纬度（GCJ-02），未解析为 null                              |
| address          | String  | 高德地址，未解析为 null                                    |
| poiId            | String  | 高德 POI id，未解析为 null                                 |
| resolved         | Boolean | 是否成功解析到坐标；false 时前端需自行用地标名定位         |
| status           | String  | 节点状态：`UPCOMING` 未到达 / `CURRENT` 进行中 / `ARRIVED` 已到达 |

**无激活路线响应示例：**

```json
{
  "code": 1,
  "msg": "success",
  "data": null
}
```

### 1.16.2 WebSocket 路线协议

复用数字人对话的 WebSocket 连接（`/chat?token=<jwt>&attractionId=<id>`）。消息为 JSON 文本帧，以 `type` 字段区分。

**客户端 → 服务端（入站）：**

| type           | 附加字段            | 说明                                             |
| -------------- | ------------------- | ------------------------------------------------ |
| `routeGenerate`| 无                  | 触发生成个性化路线（异步，结果以 `routeTimeline` 推回） |
| `routeArrive`  | `stopIndex` (Number)| 上报某节点已到达；后端置该节点为已到达并前移当前指针 |
| `routeClose`   | 无                  | 关闭并清除当前路线                               |

请求示例：

```json
{ "type": "routeGenerate" }
```
```json
{ "type": "routeArrive", "stopIndex": 0 }
```
```json
{ "type": "routeClose" }
```

**服务端 → 客户端（出站）：**

| type            | 载荷                       | 说明                                       |
| --------------- | -------------------------- | ------------------------------------------ |
| `routeTimeline` | `data`：RoutePlanVO        | 路线生成成功，下发完整数轴                 |
| `routeUpdate`   | `data`：RoutePlanVO        | 某节点到达后，下发更新后的完整数轴         |
| `routeClosed`   | 无                         | 路线已关闭                                 |
| `routeError`    | `text`：错误说明           | 无资料 / 生成失败 / 无可更新路线           |

出站示例（结构同 1.16.1 的 `data`，置于 `data` 字段）：

```json
{
  "type": "routeTimeline",
  "data": {
    "routeId": "f1c2e0a8-6b3d-4a2e-9c11-7a0b8d2e5f3a",
    "title": "故宫中轴线·皇家建筑精华游",
    "summary": "沿中轴线一路向北，看尽明清皇家建筑的礼制与气派。",
    "attractionId": 80000000000000001,
    "cityName": "北京市",
    "adcode": "110101",
    "centerLng": 116.397026,
    "centerLat": 39.918058,
    "generatedAt": 1718700000000,
    "stops": [
      { "stopIndex": 0, "name": "午门", "searchKeyword": "故宫午门", "recommendReason": "故宫正门。", "estimatedMinutes": 15, "longitude": 116.397026, "latitude": 39.913423, "address": "北京市东城区景山前街4号", "poiId": "B000A8UIN8", "resolved": true, "status": "CURRENT" }
    ]
  }
}
```

```json
{ "type": "routeError", "text": "暂无可生成路线的景点资料" }
```

### 1.16.3 WebSocket 表情采集协议

复用数字人对话的 WebSocket 连接（`/chat?token=<jwt>&attractionId=<id>`）。表情帧为 JSON 文本帧，以 `type` 字段区分。

**客户端 → 服务端（入站，新增）：**

| type            | 附加字段                | 说明                                                         |
| --------------- | ----------------------- | ------------------------------------------------------------ |
| `emotionFrame`  | `photo` (String, base64) | 前置摄像头低频采集的面部帧（JPEG base64，无 data: 前缀），后端限额+异步视觉分析并落库，原始图像不入库/不存 OSS |

请求示例：

```json
{ "type": "emotionFrame", "photo": "<base64 jpeg>" }
```

**限流说明：** 后端每用户每 5s 最多处理 1 帧；超限帧静默丢弃。客户端应保持 8~12s 间隔发送、分辨率 480×640 以内。


---

# 二、管理员端接口

## 2.1 管理员注册

**请求 URL：** `POST /admins/register`

| 参数名          | 类型   | 必填 | 说明                              |
| --------------- | ------ | ---- | --------------------------------- |
| username        | String | 是   | 账号，4-16 位字母、数字或下划线   |
| password        | String | 是   | 密码，6-20 位字母、数字或特殊字符 |
| confirmPassword | String | 是   | 确认密码，必须与 password 一致    |
| nickname        | String | 是   | 昵称，2-15 位中英文、数字或下划线 |

**请求示例：**

```json
{
  "username": "admin001",
  "password": "Admin123!@#",
  "confirmPassword": "Admin123!@#",
  "nickname": "管理员小王"
}
```

> 两次密码不一致时返回 `"两次输入的密码不一致"`

**成功响应示例：**

```json
{
  "code": 1,
  "data": {
    "username": "admin001"
  },
  "msg": "success"
}
```

---

## 2.2 管理员登录

**请求 URL：** `POST /admins/login`

> 请求参数同用户登录，参考 1.2

**成功响应示例：**

```json
{
  "code": 1,
  "data": {
    "id": "0",
    "token": "eyJhbGciOiJIUzM4NCJ9.xxx"
  },
  "msg": "success"
}
```

---

## 2.3 新增景点

**请求 URL：** `POST /admins/attractions`

**请求参数（Body / JSON）：**

| 参数名         | 类型    | 必填 | 说明                                                         |
| -------------- | ------- | ---- | ------------------------------------------------------------ |
| coverUrl       | String  | 是   | 封面图片地址                                                 |
| attractionName | String  | 是   | 景点名称，中英文或数字，2-20 个字符                          |
| type           | Integer | 是   | 景点类型：0 主题乐园 / 1 博物馆与展馆 / 2 自然公园 / 3 风景名胜与休闲度假 / 4 历史文化 / 5 古镇水乡 / 6 动植物园与水族馆 / 7 现代地标 |
| longitude      | Double  | 是   | 经度（GCJ-02 高德坐标系），前端定位后传入，范围 -180~180     |
| latitude       | Double  | 是   | 纬度（GCJ-02 高德坐标系），前端定位后传入，范围 -90~90       |
| city           | String  | 是   | 所在城市（地区筛选键，如 `北京市`）                          |
| province       | String  | 否   | 所在省                                                       |
| district       | String  | 否   | 所在区/县                                                    |
| adcode         | String  | 否   | 高德区域编码                                                 |

> `longitude/latitude/city` 由前端调用高德定位/地理逆编码得到后随表单提交。

**请求示例：**

```json
{
  "coverUrl": "https://oss.example.com/cover/xxx.jpg",
  "attractionName": "故宫博物院",
  "type": 1,
  "longitude": 116.397026,
  "latitude": 39.918058,
  "province": "北京市",
  "city": "北京市",
  "district": "东城区",
  "adcode": "110101"
}
```

**成功响应示例：**

```json
{
  "code": 1,
  "data": {
    "id": "2044359968888639490",
    "attractionName": "故宫博物院",
    "coverUrl": "https://oss.example.com/cover/xxx.jpg",
    "type": 1
  },
  "msg": "success"
}
```

---

## 2.4 更新景点

**请求 URL：** `PUT /admins/attractions`

**请求参数（Body / JSON）：**

| 参数名         | 类型    | 必填 | 说明                   |
| -------------- | ------- | ---- | ---------------------- |
| id             | String  | 是   | 景点 ID（雪花ID字符串） |
| coverUrl       | String  | 否   | 封面图片地址           |
| attractionName | String  | 否   | 景点名称               |
| type           | Integer | 否   | 景点类型（枚举同 2.3） |
| longitude      | Double  | 否   | 经度（GCJ-02），不传则不更新 |
| latitude       | Double  | 否   | 纬度（GCJ-02），不传则不更新 |
| province       | String  | 否   | 所在省                 |
| city           | String  | 否   | 所在城市               |
| district       | String  | 否   | 所在区/县              |
| adcode         | String  | 否   | 高德区域编码           |

**请求示例：**

```json
{
  "id": "2044387096879357953",
  "attractionName": "故宫博物院（更新名）",
  "coverUrl": "https://oss.example.com/cover/new.jpg",
  "type": 4,
  "longitude": 116.397026,
  "latitude": 39.918058,
  "province": "北京市",
  "city": "北京市",
  "district": "东城区",
  "adcode": "110101"
}
```

**成功响应示例：**

```json
{
  "code": 1,
  "data": {
    "id": "2044387096879357953",
    "attractionName": "故宫博物院（更新名）",
    "coverUrl": "https://oss.example.com/cover/new.jpg",
    "type": 4,
    "longitude": 116.397026,
    "latitude": 39.918058,
    "province": "北京市",
    "city": "北京市",
    "district": "东城区",
    "adcode": "110101"
  },
  "msg": "success"
}
```

---

## 2.5 删除景点

**请求 URL：** `DELETE /admins/attractions/{attractionId}`

> ⚠️ 删除前请弹窗二次确认

**请求参数（路径参数）：**

| 参数名       | 类型   | 必填 | 说明       |
| ------------ | ------ | ---- | ---------- |
| attractionId | String | 是   | 景点唯一ID |

**成功响应示例：**

```json
{
  "code": 1,
  "data": null,
  "msg": "success"
}
```

---

## 2.6 批量删除景点

**请求 URL：** `DELETE /admins/attractions/batch`

**请求参数（Body / JSON）：**

| 参数名 | 类型          | 必填 | 说明             |
| ------ | ------------- | ---- | ---------------- |
| ids    | List\<Long\> | 是   | 景点ID列表，不可为空 |

**请求示例：**

```json
{
  "ids": [2044359968888639490, 2044359968888639491]
}
```

**成功响应示例：**

```json
{
  "code": 1,
  "data": null,
  "msg": "success"
}
```

---

## 2.7 获取景点列表（管理员，游标分页）

**请求 URL：** `GET /admins/attractions`

**请求参数（Query）：**

| 参数名   | 类型    | 必填 | 说明                                        |
| -------- | ------- | ---- | ------------------------------------------- |
| keyWord  | String  | 否   | 搜索关键词，匹配景点名称，不超过 20 个字符  |
| type     | Integer | 否   | 景点类型筛选，不传则查全部                  |
| city     | String  | 否   | 按城市（地区）筛选，不传则查全部            |
| lastId   | String  | 否   | 游标，上一页最后一条数据的 ID，首次请求不传 |
| pageSize | Integer | 否   | 每页条数，默认 6                            |

**请求示例：**

```
GET /admins/attractions?keyWord=故宫&type=1&pageSize=6
GET /admins/attractions?city=北京市&pageSize=6
GET /admins/attractions?lastId=2044359968888639490&pageSize=6
```

**响应示例：**

```json
{
  "code": 1,
  "data": {
    "list": [
      {
        "id": "2044359968888639490",
        "attractionName": "故宫博物院",
        "coverUrl": "https://oss.example.com/cover/xxx.jpg",
        "type": 1,
        "city": "北京市"
      }
    ],
    "nextLastId": "2044359968888639490",
    "hasMore": true
  },
  "msg": "success"
}
```

---

## 2.8 景点数据回显

**请求 URL：** `GET /admins/attractions/{attractionId}`

> 点击某条景点进入编辑页时调用

**请求参数（路径参数）：**

| 参数名       | 类型   | 必填 | 说明       |
| ------------ | ------ | ---- | ---------- |
| attractionId | String | 是   | 景点唯一ID |

**响应示例：**

```json
{
  "code": 1,
  "data": {
    "id": "2044359968888639490",
    "attractionName": "故宫博物院",
    "coverUrl": "https://oss.example.com/cover/xxx.jpg",
    "type": 1,
    "longitude": 116.397026,
    "latitude": 39.918058,
    "province": "北京市",
    "city": "北京市",
    "district": "东城区",
    "adcode": "110101"
  },
  "msg": "success"
}
```

---

## 2.9 文件上传

**请求 URL：** `POST /admins/file/doc`

> 仅支持后缀为 `.doc`、`.pdf`、`.docx` 的文件，上传至阿里云 OSS，使用 `multipart/form-data`

**响应参数说明：**

| 参数名 | 类型   | 说明                   |
| ------ | ------ | ---------------------- |
| ossUrl | String | OSS 文件地址           |
| taskId | String | 用于轮询文档解析状态   |

**响应示例：**

```json
{
  "code": 1,
  "data": {
    "ossUrl": "https://oss.example.com/doc/xxx.pdf",
    "taskId": "task_abc123"
  },
  "msg": "success"
}
```

---

## 2.10 文档解析状态轮询

**请求 URL：** `GET /admins/attractions/documents/{taskId}`

> 文件上传成功后，拿到 `taskId` 每 **2 秒**轮询一次本接口

**请求参数（路径参数）：**

| 参数名 | 类型   | 必填 | 说明                    |
| ------ | ------ | ---- | ----------------------- |
| taskId | String | 是   | 文件上传返回的任务 ID   |

**响应 data 枚举值：**

| 值         | 含义                                                       |
| ---------- | ---------------------------------------------------------- |
| PROCESSING | 正在解析，继续轮询                                         |
| SUCCESS    | 解析完成，弹窗提示"解析成功"，并调用 2.10 刷新文件列表     |
| FAILED     | 解析失败，弹窗提示"解析失败"，并调用 2.10 刷新文件列表     |

**响应示例：**

```json
{ "code": 1, "msg": "success", "data": "PROCESSING" }
{ "code": 1, "msg": "success", "data": "SUCCESS" }
{ "code": 1, "msg": "success", "data": "FAILED" }
```

---

## 2.11 景点文件列表回显

**请求 URL：** `GET /admins/attractions/documents/{attractionId}`

**请求参数（路径参数）：**

| 参数名       | 类型   | 必填 | 说明       |
| ------------ | ------ | ---- | ---------- |
| attractionId | String | 是   | 景点唯一ID |

**响应参数说明：**

| 字段       | 类型   | 说明         |
| ---------- | ------ | ------------ |
| id         | String | 文件 ID      |
| ossUrl     | String | OSS 文件地址 |
| fileName   | String | 文件名       |
| fileType   | String | 文件类型     |

**响应示例：**

```json
{
  "code": 1,
  "data": [
    {
      "id": "1001",
      "ossUrl": "https://oss.example.com/doc/xxx.pdf",
      "fileName": "景区介绍.pdf",
      "fileType": "pdf"
    }
  ],
  "msg": "success"
}
```

> list 为空时，仅展示"添加文件"的加号卡片

---

## 2.12 删除景点文档

**请求 URL：** `DELETE /admins/attractions/documents/{fileId}`

> ⚠️ 删除前请弹窗二次确认

**请求参数（路径参数）：**

| 参数名 | 类型   | 必填 | 说明                   |
| ------ | ------ | ---- | ---------------------- |
| fileId | String | 是   | 文件 ID（列表中的 id） |

**成功响应示例：**

```json
{
  "code": 1,
  "data": null,
  "msg": "success"
}
```

---

## 2.13 新增或修改数字人

**请求 URL：** `POST /admins/attractions/digital-human`

**请求参数（Body / JSON）：**

| 参数名       | 类型    | 必填 | 说明                                |
| ------------ | ------- | ---- | ----------------------------------- |
| id           | Long    | 否   | 数字人 ID（雪花ID，字符串传递）      |
| ossUrl       | String  | 是   | 数字人肖像图片的 OSS 地址           |
| attractionId | Long    | 是   | 关联的景点 ID（雪花ID，字符串传递） |

**请求示例：**

```json
{
  "id": "2044359968888639999",
  "ossUrl": "https://oss.example.com/portrait/guide_02.jpg",
  "attractionId": "2044359968888639490"
}
```

> 新增/修改成功后，算力服务器会在后台异步预加载视频。前端应调用 **2.15 预加载状态轮询** 接口，等到状态变为 `SUCCESS` 后数字人才可用。预加载完成后可调用 **2.19 生成测试视频** 接口生成一段测试视频，供管理员预览推理效果。

**成功响应示例：**

```json
{
  "code": 1,
  "data": {
    "id": "2044359968888639999",
    "ossUrl": "https://oss.example.com/portrait/guide_02.jpg"
  },
  "msg": "success"
}
```

---

## 2.14 查询数字人详情

**请求 URL：** `GET /admins/attractions/digital-human/{attractionId}`

> 进入数字人编辑页时调用，用于数据回显

**请求参数（路径参数）：**

| 参数名       | 类型   | 必填 | 说明         |
| ------------ | ------ | ---- | ------------ |
| attractionId | String | 是   | attractionId |

**请求示例：** `GET /admins/attractions/digital-human/2044359968888639999`

**成功响应示例：**

```json
{
  "code": 1,
  "data": {
    "id": "2044359968888639999",
    "ossUrl": "https://oss.example.com/portrait/guide_02.jpg"
  },
  "msg": "success"
}
```

---

## 2.15 数字人预加载状态轮询

**请求 URL：** `GET /admins/attractions/digital-human/preload-status/{attractionId}`

> 调用 2.13 新增/修改数字人后，每 **2 秒**轮询一次本接口，直到状态变为 `SUCCESS` 或 `FAILED`。

**请求参数（路径参数）：**

| 参数名       | 类型   | 必填 | 说明               |
| ------------ | ------ | ---- | ------------------ |
| attractionId | String | 是   | 景点 ID（与 2.13 中的 attractionId 一致） |

**请求示例：** `GET /admins/attractions/digital-human/preload-status/2044359968888639490`

**响应 data 枚举值：**

| 值         | 含义                                                       |
| ---------- | ---------------------------------------------------------- |
| PROCESSING | 正在下载视频并加载模型，继续轮询                           |
| SUCCESS    | 预加载完成，数字人可以使用                                 |
| FAILED     | 预加载失败，检查视频文件是否有效或重试                     |
| null       | 尚未开始处理，继续轮询                                     |

**响应示例：**

```json
{ "code": 1, "msg": "success", "data": "PROCESSING" }
{ "code": 1, "msg": "success", "data": "SUCCESS" }
{ "code": 1, "msg": "success", "data": "FAILED" }
```

---

## 2.16 删除数字人

**请求 URL：** `DELETE /admins/attractions/digital-human/{id}`

> ⚠️ 删除前请弹窗二次确认

**请求参数（路径参数）：**

| 参数名 | 类型   | 必填 | 说明          |
| ------ | ------ | ---- | ------------- |
| id     | String | 是   | 数字人唯一 ID |

**请求示例：** `DELETE /admins/attractions/digital-human/2044359968888639999`

**成功响应示例：**

```json
{
  "code": 1,
  "data": null,
  "msg": "success"
}
```

---

## 2.17 上传景点封面图片

**请求 URL：** `POST /admins/file/cover`

**请求方式：** `multipart/form-data`

**接口说明：** 上传景点封面图片至阿里云 OSS，返回图片地址。返回的 URL 填入新增景点（2.3）或更新景点（2.4）的 `coverUrl` 字段。

**请求参数（form-data）：**

| 参数名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| file   | File | 是   | 图片文件，仅支持图片格式（`image/*`） |

**响应参数说明：**

| 字段 | 类型   | 说明              |
| ---- | ------ | ----------------- |
| data | String | 图片 OSS 访问地址 |

**成功响应示例：**

```json
{
  "code": 1,
  "data": "https://oss.example.com/cover/2026/05/19/xxx.jpg",
  "msg": "success"
}
```

**错误响应示例：**

```json
{
  "code": 400,
  "data": null,
  "msg": "文件格式错误，请上传图片"
}
```

---

## 2.18 上传数字人驱动视频

**请求 URL：** `POST /admins/file/video`

**请求方式：** `multipart/form-data`

**接口说明：** 上传数字人驱动视频至阿里云 OSS，返回视频 OSS 地址。返回的 URL 可用于 2.13 新增/修改数字人时填入 `ossUrl` 字段。

> 上传视频仅完成文件存储，预加载在调用 2.13 新增/修改数字人后才会触发。预加载完成后（轮询 2.15 获得 `SUCCESS`）数字人才可用。

**请求参数（form-data + Query）：**

| 参数名       | 位置      | 类型   | 必填 | 说明                                     |
| ------------ | --------- | ------ | ---- | ---------------------------------------- |
| file         | form-data | File   | 是   | 视频文件，仅支持视频格式（`video/*`）    |
| attractionId | Query     | String | 是   | 关联景点 ID（雪花 ID，字符串传递）       |

**请求示例：**

```
POST /admins/file/video?attractionId=2044359968888639490
Content-Type: multipart/form-data

file: <视频文件>
```

**响应参数说明：**

| 字段 | 类型   | 说明              |
| ---- | ------ | ----------------- |
| data | String | 视频 OSS 访问地址 |

**成功响应示例：**

```json
{
  "code": 1,
  "data": "https://oss.example.com/video/2026/05/19/xxx.mp4",
  "msg": "success"
}
```

**错误响应示例：**

```json
{
  "code": 400,
  "data": null,
  "msg": "文件格式错误，请上传视频"
}
```

---

## 2.19 生成测试视频

**请求 URL：** `POST /admins/attractions/digital-human/test-video/{attractionId}`

> 触发算力服务器异步生成一段测试视频，用于管理员预览数字人的推理效果。生成完成后通过 **2.20 测试视频状态轮询** 获取视频地址。

**请求参数（路径参数 + Body / JSON）：**

| 参数名       | 位置 | 类型   | 必填 | 说明                                                  |
| ------------ | ---- | ------ | ---- | ----------------------------------------------------- |
| attractionId | Path | String | 是   | 景点 ID（与数字人关联的 attractionId 一致）           |
| text         | Body | String | 否   | 自定义测试文本，为空时使用算力服务器默认语音文本      |

**请求示例：**

```json
POST /admins/attractions/digital-human/test-video/2044359968888639490
{
  "text": "你好，欢迎来到故宫博物院参观游览。"
}
```

> 自定义文本为空时传 `{}` 即可，系统会使用默认测试文本。

**成功响应示例：**

```json
{
  "code": 1,
  "data": "任务已提交",
  "msg": "success"
}
```

> 调用成功后，每 **3 秒**轮询一次 **2.20 测试视频状态轮询** 接口。测试视频生成时间通常为 10-30 秒，取决于算力负载。

---

## 2.20 测试视频生成状态轮询

**请求 URL：** `GET /admins/attractions/digital-human/test-video-status/{attractionId}`

> 调用 2.19 触发测试视频生成后，每 **3 秒**轮询一次本接口，直到状态变为 `SUCCESS` 或 `FAILED`。

**请求参数（路径参数）：**

| 参数名       | 类型   | 必填 | 说明               |
| ------------ | ------ | ---- | ------------------ |
| attractionId | String | 是   | 景点 ID            |

**请求示例：** `GET /admins/attractions/digital-human/test-video-status/2044359968888639490`

**响应 data 字段说明：**

| 字段     | 类型   | 说明                                                |
| -------- | ------ | --------------------------------------------------- |
| status   | String | 见下方枚举值                                        |
| videoUrl | String | 为 `null` 时表示尚未完成，成功后为视频播放地址      |

**status 枚举值：**

| 值         | 含义                                                       |
| ---------- | ---------------------------------------------------------- |
| PROCESSING | 正在生成测试视频，继续轮询                                 |
| SUCCESS    | 生成完成，`videoUrl` 指向可播放的 MP4 视频                 |
| FAILED     | 生成失败，检查数字人是否已预加载完成或重试                 |

**响应示例：**

```json
{ "code": 1, "msg": "success", "data": { "status": "PROCESSING", "videoUrl": null } }
```

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "status": "SUCCESS",
    "videoUrl": "/ai-project/v1/admins/attractions/digital-human/test-video-file/2044359968888639490"
  }
}
```

```json
{ "code": 1, "msg": "success", "data": { "status": "FAILED", "videoUrl": null } }
```

**前端使用说明：**

- 拿到 `SUCCESS` 后，将 `videoUrl` 直接填入 `<video>` 标签的 `src` 属性即可播放
- 测试视频在算力服务器上保留 **24 小时**，超期自动清理，届时 `videoUrl` 将失效

---

## 2.21 获取测试视频文件（代理）

**请求 URL：** `GET /admins/attractions/digital-human/test-video-file/{attractionId}`

> 后端代理接口，从算力服务器获取 MP4 视频文件并流式返回。通常直接使用 2.20 返回的 `videoUrl` 即可，无需单独调用此接口。

**请求参数（路径参数）：**

| 参数名       | 类型   | 必填 | 说明       |
| ------------ | ------ | ---- | ---------- |
| attractionId | String | 是   | 景点 ID    |

**请求示例：** `GET /admins/attractions/digital-human/test-video-file/2044359968888639490`

**响应：** `Content-Type: video/mp4`， `<video>` 标签可直接播放。

---

# 三、数据统计接口（管理员）

## 3.1 热门问题排行（柱状图）

**请求 URL：** `GET /admins/stat/faq/{attractionId}`

**请求参数：**

| 参数名       | 类型    | 位置  | 必填 | 默认值 | 说明                    |
| ------------ | ------- | ----- | ---- | ------ | ----------------------- |
| attractionId | String  | Path  | 是   | —      | 景点唯一 ID             |
| days         | Integer | Query | 否   | 7      | 统计时间窗口（天），如 7、30 |

**请求示例：** `GET /admins/stat/faq/10086?days=7`

**响应参数说明（List\<HotFaqChartVO\>）：**

| 字段     | 类型    | 说明                         |
| -------- | ------- | ---------------------------- |
| question | String  | 标准问题内容（X 轴类目数据） |
| count    | Integer | 触发次数（Y 轴数值数据）     |

> 返回数据已按 `count` 降序排列

**无数据时：** `data` 为空数组 `[]`

**响应示例：**

```json
{
  "code": 1,
  "data": [
    { "count": 452, "question": "学生票半价需要带学生证吗？" },
    { "count": 389, "question": "五一期间景区的开放时间是几点？" },
    { "count": 210, "question": "自驾游的车可以停在景区里面吗？" }
  ],
  "msg": "success"
}
```

---

## 3.2 聊天服务使用趋势

**请求 URL：** `GET /admins/stat/chat-trend/{attractionId}`

> 统计口径为 T+1，不含今天

**请求参数：**

| 参数名       | 类型    | 位置  | 必填 | 默认值 | 说明                                                                  |
| ------------ | ------- | ----- | ---- | ------ | --------------------------------------------------------------------- |
| attractionId | String  | Path  | 是   | —      | 景点唯一 ID                                                           |
| days         | Integer | Query | 是   | 7      | 1：昨日（按小时，24 个数据点）；7：近 7 天；30：近 30 天（均按天分组） |

**响应参数说明（ChatTrendVO）：**

| 字段                  | 类型    | 说明                                                                 |
| --------------------- | ------- | -------------------------------------------------------------------- |
| summary.totalChats    | Integer | 所选周期内的总聊天数                                                 |
| trendList[].time      | String  | X 轴时间节点，`days=1` 时格式 `HH:00`，`days>1` 时格式 `YYYY-MM-DD` |
| trendList[].count     | Integer | Y 轴数值，对应时间节点的聊天次数                                     |

**无数据时：** `summary.totalChats` 为 `0`，`trendList` 仍返回完整时间轴，每个点 `count` 均为 `0`（不会断档）

**响应示例（days=7）：**

```json
{
  "code": 1,
  "data": {
    "summary": { "totalChats": 1250 },
    "trendList": [
      { "time": "2026-04-07", "count": 120 },
      { "time": "2026-04-08", "count": 150 },
      { "time": "2026-04-13", "count": 116 }
    ]
  },
  "msg": "success"
}
```

**响应示例（days=1）：**

```json
{
  "code": 1,
  "data": {
    "summary": { "totalChats": 116 },
    "trendList": [
      { "time": "00:00", "count": 2 },
      { "time": "01:00", "count": 0 },
      { "time": "23:00", "count": 8 }
    ]
  },
  "msg": "success"
}
```

---

## 3.3 用户满意度趋势（折线图）

**请求 URL：** `GET /admins/stat/satisfaction-trend/{attractionId}`

**认证方式：** Bearer Token（管理员 JWT）

**接口说明：** 获取指定景点近N天的用户满意度趋势，返回每日均分和总均分，用于折线图展示。数据来源为用户评价表（`tb_user_review`），仅统计已评价（status=1）的记录。

**请求参数：**

| 参数名       | 类型    | 位置  | 必填 | 说明                                  |
| ------------ | ------- | ----- | ---- | ------------------------------------- |
| attractionId | Long    | Path  | 是   | 景点 ID                               |
| days         | Integer | Query | 是   | 查询天数，`7` 表示近7天，`30` 表示近30天 |

**请求示例：**

```http
GET /admins/stat/satisfaction-trend/1001?days=7
Authorization: Bearer <admin_token>
```

**响应参数说明（SatisfactionTrendVO）：**

| 字段          | 类型           | 说明                                   |
| ------------- | -------------- | -------------------------------------- |
| totalAvgScore | Double         | 该时段内所有评价的总体均分（保留1位小数） |
| dates         | List\<String\> | 日期列表，格式 `yyyy-MM-dd`，升序       |
| avgScores     | List\<Double\> | 每日均分，与 dates 一一对应             |
| counts        | List\<Integer\>| 每日评价数，与 dates 一一对应           |

**无数据时：** `totalAvgScore` 为 `null`（前端需做空值处理），`dates` 仍返回完整日期序列，`avgScores` 全部为 `0.0`，`counts` 全部为 `0`

**响应示例：**

```json
{
  "code": 1,
  "msg": "success",
  "data": {
    "totalAvgScore": 3.8,
    "dates": ["2026-05-06", "2026-05-07", "2026-05-08", "2026-05-09", "2026-05-10", "2026-05-11", "2026-05-12"],
    "avgScores": [3.5, 4.0, 4.0, 3.7, 4.2, 3.5, 4.0],
    "counts": [8, 10, 9, 7, 12, 6, 11]
  }
}
```

---

# 四、游客体验分析接口（管理员）

## 4.1 情感趋势

**请求 URL：** `GET /admin/analysis/emotion-trend/{attractionId}`

**请求参数：**

| 参数名       | 类型    | 必填 | 说明                     |
| ------------ | ------- | ---- | ------------------------ |
| attractionId | String  | 是   | 景区 ID，路径参数        |
| days         | Integer | 是   | 时间范围，可传 7 或 30   |

**响应参数说明：**

| 参数名            | 类型            | 说明                                                |
| ----------------- | --------------- | --------------------------------------------------- |
| dates             | List\<String\>  | 日期序列，格式 MM-dd，无数据日期照常返回            |
| positiveCount     | List\<Integer\> | 每日正面情感数量，下标与 dates 对应                 |
| neutralCount      | List\<Integer\> | 每日中性情感数量                                    |
| negativeCount     | List\<Integer\> | 每日负面情感数量                                    |
| positiveRate      | List\<Double\>  | 每日正面情感占比，保留一位小数，如 68.4             |
| neutralRate       | List\<Double\>  | 每日中性情感占比                                    |
| negativeRate      | List\<Double\>  | 每日负面情感占比                                    |
| totalPositiveRate | Double          | 所选时间段内正面情感整体占比                        |
| totalNeutralRate  | Double          | 所选时间段内中性情感整体占比                        |
| totalNegativeRate | Double          | 所选时间段内负面情感整体占比                        |

**响应示例：**

```json
{
  "code": 1,
  "data": {
    "dates": ["04-01", "04-02", "04-03"],
    "positiveCount": [65, 70, 0],
    "neutralCount": [20, 18, 0],
    "negativeCount": [10, 8, 0],
    "positiveRate": [68.4, 72.9, 0.0],
    "neutralRate": [21.1, 18.8, 0.0],
    "negativeRate": [10.5, 8.3, 0.0],
    "totalPositiveRate": 70.7,
    "totalNeutralRate": 19.9,
    "totalNegativeRate": 9.4
  },
  "msg": "success"
}
```

**前端使用说明：**

- 折线图使用 `*Rate` 字段渲染曲线，点击数据点时用同下标的 `*Count` 展示当日详情
- 环形图直接使用 `total*Rate` 三个字段，无需前端二次计算
- 某天无数据时 count 为 0、rate 为 0.0，日期序列完整不断档，前端无需做缺失处理
- **整个周期完全无数据时：** 所有 count 为 `0`、rate 为 `0.0`，`total*Rate` 均为 `0.0`，日期序列照常返回

---

## 4.2 情感与关注点卡片

**请求 URL：** `GET /admin/analysis/emotion-focus-card/{attractionId}`

**请求参数：**

| 参数名       | 类型    | 必填 | 说明                      |
| ------------ | ------- | ---- | ------------------------- |
| attractionId | String  | 是   | 景区 ID，路径参数         |
| days         | Integer | 是   | 时间范围，可传 1 / 7 / 30 |

**响应参数说明：**

| 参数名             | 类型   | 说明                                                     |
| ------------------ | ------ | -------------------------------------------------------- |
| positiveRate       | Double | 当期正面情感占比，保留一位小数，如 68.4                  |
| positiveRateChange | Double | 与上期相比的变化值，正数上升，负数下降，如 +3.2          |
| changeLabel        | String | 对比说明，如"较昨日"/"较上个7天"/"较上个30天"            |
| topFocus           | String | 高频关注点，如"餐饮"或"餐饮/票务"                        |
| topFocusRate       | Double | 高频关注点第一名占所有问询的比例，保留一位小数           |
| worstFocus         | String | 待改善项，负面情感最集中的关注点，如"停车"或"停车/导览"  |

**响应示例：**

```json
{
  "code": 1,
  "data": {
    "positiveRate": 68.4,
    "positiveRateChange": 3.2,
    "changeLabel": "较上个7天",
    "topFocus": "餐饮/票务",
    "topFocusRate": 41.0,
    "worstFocus": "停车"
  },
  "msg": "success"
}
```

**前端使用说明：**

- `topFocus` 和 `worstFocus`：第二名数量不足第一名一半时只返回第一名，否则返回前两名以 `/` 拼接
- **无数据时：** `positiveRate` 为 `0.0`，`positiveRateChange` 为 `0.0`，`changeLabel` 照常返回（如"较上个7天"），`topFocus` 和 `worstFocus` 返回 `"暂无数据"`，`topFocusRate` 始终为 `null`（当前版本未赋值）

---

## 4.3 面部表情趋势

**请求 URL：** `GET /admin/analysis/face-emotion-trend/{attractionId}`

**请求参数：**

| 参数名       | 类型    | 必填 | 说明                     |
| ------------ | ------- | ---- | ------------------------ |
| attractionId | String  | 是   | 景区 ID，路径参数        |
| days         | Integer | 是   | 时间范围，可传 7 或 30   |

**响应参数说明：**

| 参数名            | 类型            | 说明                                                |
| ----------------- | --------------- | --------------------------------------------------- |
| dates             | List\<String\>  | 日期序列，格式 MM-dd，无数据日期照常返回            |
| joyCount          | List\<Integer\> | 每日喜悦表情数量，下标与 dates 对应                 |
| surpriseCount     | List\<Integer\> | 每日惊讶表情数量                                    |
| neutralCount      | List\<Integer\> | 每日中性表情数量                                    |
| confusionCount    | List\<Integer\> | 每日困惑表情数量                                    |
| disgustCount      | List\<Integer\> | 每日厌恶表情数量                                    |
| angerCount        | List\<Integer\> | 每日愤怒表情数量                                    |
| sadnessCount      | List\<Integer\> | 每日悲伤表情数量                                    |
| joyRate           | List\<Double\>  | 每日喜悦表情占比，保留一位小数，如 68.4             |
| surpriseRate      | List\<Double\>  | 每日惊讶表情占比                                    |
| neutralRate       | List\<Double\>  | 每日中性表情占比                                    |
| confusionRate     | List\<Double\>  | 每日困惑表情占比                                    |
| disgustRate       | List\<Double\>  | 每日厌恶表情占比                                    |
| angerRate         | List\<Double\>  | 每日愤怒表情占比                                    |
| sadnessRate       | List\<Double\>  | 每日悲伤表情占比                                    |
| totalJoyRate      | Double          | 所选时间段内喜悦表情整体占比                        |
| totalSurpriseRate | Double          | 所选时间段内惊讶表情整体占比                        |
| totalNeutralRate  | Double          | 所选时间段内中性表情整体占比                        |
| totalConfusionRate| Double          | 所选时间段内困惑表情整体占比                        |
| totalDisgustRate  | Double          | 所选时间段内厌恶表情整体占比                        |
| totalAngerRate    | Double          | 所选时间段内愤怒表情整体占比                        |
| totalSadnessRate  | Double          | 所选时间段内悲伤表情整体占比                        |

**响应示例：**

```json
{
  "code": 1,
  "data": {
    "dates": ["06-26", "06-27", "06-28"],
    "joyCount": [15, 20, 18],
    "surpriseCount": [3, 5, 2],
    "neutralCount": [60, 55, 62],
    "confusionCount": [8, 6, 7],
    "disgustCount": [1, 0, 2],
    "angerCount": [2, 3, 1],
    "sadnessCount": [5, 4, 6],
    "joyRate": [15.8, 21.5, 18.4],
    "surpriseRate": [3.2, 5.4, 2.0],
    "neutralRate": [63.2, 59.1, 63.3],
    "confusionRate": [8.4, 6.5, 7.1],
    "disgustRate": [1.1, 0.0, 2.0],
    "angerRate": [2.1, 3.2, 1.0],
    "sadnessRate": [5.3, 4.3, 6.1],
    "totalJoyRate": 18.5,
    "totalSurpriseRate": 3.5,
    "totalNeutralRate": 61.9,
    "totalConfusionRate": 7.3,
    "totalDisgustRate": 1.0,
    "totalAngerRate": 2.1,
    "totalSadnessRate": 5.3
  },
  "msg": "success"
}
```

**前端使用说明：**

- 折线图可选 7 个维度中 top-N 渲染，点击数据点时用同下标的 `*Count` 展示当日详情
- 环形图直接使用 `total*Rate` 七个字段，无需前端二次计算
- 某天无数据时 count 为 0、rate 为 0.0，日期序列完整不断档，前端无需做缺失处理
- **整个周期完全无数据时：** 所有 count 为 `0`、rate 为 `0.0`，`total*Rate` 均为 `0.0`，日期序列照常返回

---

## 4.4 获取 AI 服务建议

**请求 URL：** `GET /admin/analysis/ai-service-suggestion/{attractionId}`

**请求参数：**

| 参数名       | 类型    | 位置  | 必填 | 说明                               |
| ------------ | ------- | ----- | ---- | ---------------------------------- |
| attractionId | String  | Path  | 是   | 景点唯一 ID                        |
| type         | Integer | Query | 是   | 时间范围：0 = 近 7 天；1 = 近 30 天 |

**请求示例：** `GET /admin/analysis/ai-service-suggestion/1001?type=0`

**响应参数说明（SuggestionVO）：**

| 字段       | 类型   | 说明                        |
| ---------- | ------ | --------------------------- |
| summary    | String | AI 综合分析后生成的总结内容 |
| suggestion | String | AI 针对该景点给出的具体建议 |

**无数据时：** `summary` 和 `suggestion` 均为 `null`

**成功响应示例：**

```json
{
  "code": 1,
  "data": {
    "summary": "近7天游客整体满意度较高，情感正面率达 70.7%...",
    "suggestion": "建议在餐饮区增设引导标识，改善停车区拥堵问题..."
  },
  "msg": "success"
}
```

---

## 4.5 情感概览（文本+面部合并）

**请求 URL：** `GET /admin/analysis/emotion-overview/{attractionId}`

**请求参数：**

| 参数名       | 类型    | 必填 | 说明                     |
| ------------ | ------- | ---- | ------------------------ |
| attractionId | String  | 是   | 景区 ID，路径参数        |
| days         | Integer | 是   | 时间范围，可传 7 或 30   |

**响应参数说明（EmotionOverviewVO）：**

响应分三大块：文本情感、面部表情、关注点。各 `*Count`/`*Rate` 数组下标与 `dates` 一一对齐；无数据日期 count 为 0、rate 为 0.0，日期序列不断档。

| 字段名                     | 类型            | 归属   | 说明                          |
| -------------------------- | --------------- | ------ | ----------------------------- |
| dates                      | List\<String\>  | 通用   | 日期序列，格式 MM-dd           |
| textPositiveCount          | List\<Integer\> | 文本   | 每日正面情感数量              |
| textNeutralCount           | List\<Integer\> | 文本   | 每日中性情感数量              |
| textNegativeCount          | List\<Integer\> | 文本   | 每日负面情感数量              |
| textPositiveRate           | List\<Double\>  | 文本   | 每日正面情感占比，如 68.4     |
| textNeutralRate            | List\<Double\>  | 文本   | 每日中性情感占比              |
| textNegativeRate           | List\<Double\>  | 文本   | 每日负面情感占比              |
| textTotalPositiveRate      | Double          | 文本   | 期内正面情感整体占比          |
| textTotalNeutralRate       | Double          | 文本   | 期内中性情感整体占比          |
| textTotalNegativeRate      | Double          | 文本   | 期内负面情感整体占比          |
| textRecordCount            | Integer         | 文本   | 期内文本分析记录总数          |
| faceJoyCount               | List\<Integer\> | 面部   | 每日喜悦表情数量              |
| faceSurpriseCount          | List\<Integer\> | 面部   | 每日惊讶表情数量              |
| faceNeutralCount           | List\<Integer\> | 面部   | 每日中性表情数量              |
| faceConfusionCount         | List\<Integer\> | 面部   | 每日困惑表情数量              |
| faceDisgustCount           | List\<Integer\> | 面部   | 每日厌恶表情数量              |
| faceAngerCount             | List\<Integer\> | 面部   | 每日愤怒表情数量              |
| faceSadnessCount           | List\<Integer\> | 面部   | 每日悲伤表情数量              |
| faceJoyRate                | List\<Double\>  | 面部   | 每日喜悦表情占比              |
| faceSurpriseRate           | List\<Double\>  | 面部   | 每日惊讶表情占比              |
| faceNeutralRate            | List\<Double\>  | 面部   | 每日中性表情占比              |
| faceConfusionRate          | List\<Double\>  | 面部   | 每日困惑表情占比              |
| faceDisgustRate            | List\<Double\>  | 面部   | 每日厌恶表情占比              |
| faceAngerRate              | List\<Double\>  | 面部   | 每日愤怒表情占比              |
| faceSadnessRate            | List\<Double\>  | 面部   | 每日悲伤表情占比              |
| faceTotalJoyRate           | Double          | 面部   | 期内喜悦表情整体占比          |
| faceTotalSurpriseRate      | Double          | 面部   | 期内惊讶表情整体占比          |
| faceTotalNeutralRate       | Double          | 面部   | 期内中性表情整体占比          |
| faceTotalConfusionRate     | Double          | 面部   | 期内困惑表情整体占比          |
| faceTotalDisgustRate       | Double          | 面部   | 期内厌恶表情整体占比          |
| faceTotalAngerRate         | Double          | 面部   | 期内愤怒表情整体占比          |
| faceTotalSadnessRate       | Double          | 面部   | 期内悲伤表情整体占比          |
| faceRecordCount            | Integer         | 面部   | 期内面部表情记录总数          |
| topFocus                   | String          | 关注点 | 高频关注点（文本来源）        |
| worstFocus                 | String          | 关注点 | 待改善项（文本来源）          |

**响应示例：**

```json
{
  "code": 1,
  "data": {
    "dates": ["06-26", "06-27", "06-28"],
    "textPositiveCount": [65, 70, 68],
    "textNeutralCount": [20, 18, 22],
    "textNegativeCount": [10, 8, 12],
    "textPositiveRate": [68.4, 72.9, 66.7],
    "textNeutralRate": [21.1, 18.8, 21.6],
    "textNegativeRate": [10.5, 8.3, 11.8],
    "textTotalPositiveRate": 69.3,
    "textTotalNeutralRate": 20.5,
    "textTotalNegativeRate": 10.2,
    "textRecordCount": 291,
    "faceJoyCount": [15, 20, 18],
    "faceSurpriseCount": [3, 5, 2],
    "faceNeutralCount": [60, 55, 62],
    "faceConfusionCount": [8, 6, 7],
    "faceDisgustCount": [1, 0, 2],
    "faceAngerCount": [2, 3, 1],
    "faceSadnessCount": [5, 4, 6],
    "faceJoyRate": [15.8, 21.5, 18.4],
    "faceSurpriseRate": [3.2, 5.4, 2.0],
    "faceNeutralRate": [63.2, 59.1, 63.3],
    "faceConfusionRate": [8.4, 6.5, 7.1],
    "faceDisgustRate": [1.1, 0.0, 2.0],
    "faceAngerRate": [2.1, 3.2, 1.0],
    "faceSadnessRate": [5.3, 4.3, 6.1],
    "faceTotalJoyRate": 18.5,
    "faceTotalSurpriseRate": 3.5,
    "faceTotalNeutralRate": 61.9,
    "faceTotalConfusionRate": 7.3,
    "faceTotalDisgustRate": 1.0,
    "faceTotalAngerRate": 2.1,
    "faceTotalSadnessRate": 5.3,
    "faceRecordCount": 102,
    "topFocus": "餐饮/票务",
    "worstFocus": "停车"
  },
  "msg": "success"
}
```

**前端使用说明：**

- 建议在概览页左侧放文本情感折线（正/中/负 3 条线），右侧放面部表情分布（7 类可选 top-N 渲染），底部放关注点卡片
- 面部暂无数据时所有 `face*` 字段为 0/0.0，前端按"暂无面部表情数据"兜底即可，不影响文本维度正常展示
- 单独下钻时可复用 4.1（文本情感趋势）和 4.3（面部表情趋势）独立接口
- **整个周期完全无数据时：** 所有 count 为 `0`、rate 为 `0.0`，`dates` 照常返回完整序列

---

# 附录：通用响应结构

所有接口统一返回以下结构：

```json
{
  "code": 1,
  "data": "...",
  "msg": "success"
}
```

| 字段 | 类型    | 说明                    |
| ---- | ------- | ----------------------- |
| code | Integer | 1 表示成功，其他表示失败 |
| data | Any     | 业务数据，失败时为 null |
| msg  | String  | 提示信息                |

**错误响应示例：**

```json
{
  "code": 400,
  "data": null,
  "msg": "数字人图片URL不能为空"
}
```
