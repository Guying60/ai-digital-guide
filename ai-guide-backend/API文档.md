# 接口文档

## 1. 获取用户满意度趋势

### 基本信息

| 项目 | 说明 |
|------|------|
| 接口路径 | `/ai-project/v1/admins/stat/satisfaction-trend/{attractionId}` |
| 请求方法 | `GET` |
| 认证方式 | Bearer Token（管理员 JWT） |
| 接口说明 | 获取指定景点近N天的用户满意度趋势，返回每日均分和总均分，用于折线图展示 |

### 请求参数

**路径参数**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| attractionId | Long | 是 | 景点ID |

**查询参数**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| days | Integer | 是 | 查询天数，`7` 表示近7天，`30` 表示近30天 |

### 响应数据

统一响应格式 `Result<SatisfactionTrendVO>`：

```json
{
    "code": 1,
    "msg": "success",
    "data": {
        "totalAvgScore": 4.2,
        "dates": ["2026-05-06", "2026-05-07", "2026-05-08"],
        "avgScores": [4.0, 4.5, 4.2],
        "counts": [10, 8, 12]
    }
}
```

**data 字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| totalAvgScore | Double | 该时段内所有评价的总体均分（保留1位小数） |
| dates | List\<String\> | 日期列表，格式 `yyyy-MM-dd`，升序 |
| avgScores | List\<Double\> | 每日均分，与 dates 一一对应 |
| counts | List\<Integer\> | 每日评价数，与 dates 一一对应 |

### 示例

**请求**

```http
GET /ai-project/v1/admins/stat/satisfaction-trend/1001?days=7
Authorization: Bearer <admin_token>
```

**响应**

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

## 2. 评价旅游历史

### 基本信息

| 项目 | 说明 |
|------|------|
| 接口路径 | `/ai-project/v1/users/tourHistory/evaluate` |
| 请求方法 | `POST` |
| 认证方式 | Bearer Token（用户 JWT） |
| 接口说明 | 用户对某次游览进行评分和文字反馈，更新到对应的游览记录 |

### 请求体

`TourEvaluateDTO`，JSON 格式：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| conversationId | Long | 是 | 会话ID，对应一次游览记录 |
| score | Integer | 否 | 评分，1-5 分 |
| feedbackText | String | 否 | 文字反馈 |

```json
{
    "conversationId": 123456789,
    "score": 5,
    "feedbackText": "讲解很详细，非常满意"
}
```

### 响应数据

统一响应格式 `Result<?>`，无 data：

```json
{
    "code": 1,
    "msg": "success",
    "data": null
}
```

### 错误码

| code | 说明 |
|------|------|
| 1 | 成功 |
| 400 | 客户端错误（参数校验失败等） |
| 500 | 服务端错误 |

### 示例

**请求**

```http
POST /ai-project/v1/users/tourHistory/evaluate
Authorization: Bearer <user_token>
Content-Type: application/json

{
    "conversationId": 123456789,
    "score": 4,
    "feedbackText": "不错"
}
```

**响应**

```json
{
    "code": 1,
    "msg": "success",
    "data": null
}
```
