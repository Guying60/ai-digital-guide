# Digital Tour Guide — Design System

> 目标：终结「一页一套色」的混乱，建立**唯一**语义色板，供用户端与管理端共用。  
> 依据：ui-ux-pro-max（Travel/Tourism + Flat/Minimal Light）+ 当前工程审计。

---

## 1. 现状诊断（为什么看起来乱）

当前 `colors.xml` 与布局里至少并存 **5 套互不兼容的主色**：

| 来源 | Primary / 强调色 | 问题 |
|------|------------------|------|
| Tab | `#165EFE` | 与 Theme / Profile 蓝不一致 |
| 底部导航选中 | `#FF5722` | 橙红作导航主色，与品牌蓝冲突 |
| Profile / Theme | `#004BD7` + 青绿 secondary | 相对完整，但只服务个人中心 |
| Login / Chat | `#5241CD` 紫系 | 与蓝系品牌割裂 |
| Splash | `#2F6BFF` | 又一套蓝 |
| 管理端布局硬编码 | `#8B5CF6` / `#4F46E5` / `#F59E0B` / `#2196F3` 等 | 未走 token，紫/靛/琥珀混用 |

另外：约 **20+ 个 layout** 直接写死 `#xxxxxx`（如 `activity_scenic_edit`、`activity_tourist_analysis`、`activity_point_manager`），token 再完善也管不到这些页。

**结论**：不是缺颜色，是**没有单一 Source of Truth**。

---

## 2. 产品与设计方向

| 维度 | 定义 |
|------|------|
| 产品 | AI 数字导游（景点讲解、实时对话、情绪感知、管理端分析） |
| 受众 | C 端游客 + B 端景区管理员 |
| 使用场景 | 户外强光、单手操作、聊天沉浸、数据看板 |
| 风格 | **Light-first · Flat / Minimal · 旅行可信蓝** |
| 密度 | 标准（8dp 节奏）；管理端图表区可略密 |
| 动效 | 标准微交互 150–300ms；页面切换沿用现有 slide |

### 明确不做（Anti-patterns）

- 不以紫色 / 紫白渐变作品牌主色（登录/聊天里的 `#5241CD`、管理页 `#8B5CF6` 淘汰）
- 不以暗色影院风 / glow / 厚玻璃拟态作默认主题
- 不以 emoji 当图标；图标统一矢量（现有 XML vector / Phosphor 语义）
- 不以颜色 alone 表达状态（成功/失败需图标或文案）

---

## 3. 统一色板（唯一真相）v0.3 · Sky Travel

定位：**浅蓝主色的轻旅行产品**，不是银行/博物馆商务风，也不是荧光塑料蓝。

| 避免 | 要做 |
|------|------|
| 墨蓝 / 炭灰 / 铜金商务感（v0.2） | 晴空蓝 + 浅天蓝底 |
| 紫系登录/聊天 | 全端统一蓝 |
| 导航用橙 | 选中态也用蓝 |
| 为好看重排布局 | 筛选标签与时间排序 **同一行**（现网结构） |

### 3.1 品牌与交互色

| Token（建议名） | Hex | 用途 |
|-----------------|-----|------|
| `color_primary` | `#3B82F6` | 主品牌、主按钮、选中 Tab/Nav、链接、「查看对话记录」 |
| `color_on_primary` | `#FFFFFF` | 主色上的文字/图标 |
| `color_primary_container` | `#DBEAFE` | 轻量强调底（chip、选中行） |
| `color_on_primary_container` | `#1E40AF` | 容器上文字 |
| `color_secondary` | `#38BDF8` | 浅蓝辅色（偏好标签、轻强调，同属蓝色系） |
| `color_on_secondary` | `#0C4A6E` | 浅辅色上的字（深一点保证对比） |
| `color_secondary_container` | `#E0F2FE` | |
| `color_on_secondary_container` | `#0369A1` | |
| `color_accent` | `#F59E0B` | **仅**评分星等点睛；**禁止**当导航选中色 |
| `color_on_accent` | `#FFFFFF` | |

> 主色是明亮天空蓝；辅色也是浅蓝，整体「一片蓝」，不靠墨绿/炭灰撑高级。

### 3.2 表面与文字

| Token | Hex | 用途 |
|-------|-----|------|
| `color_background` | `#F5F9FE` | 页面底（极浅天蓝） |
| `color_surface` | `#FFFFFF` | 卡片 / 对话框 |
| `color_surface_variant` | `#E8F1FB` | 输入框底、分段未选中 |
| `color_outline` | `#94A3B8` | 边框、次要图标 |
| `color_outline_variant` | `#D7E3F2` | 分割线 |
| `color_on_surface` | `#1E293B` | 主文案（石板灰，非纯黑） |
| `color_on_surface_variant` | `#64748B` | 次文案 |
| `color_muted` | `#94A3B8` | 占位、禁用文案 |

### 3.3 语义状态（全端共用）

| Token | Hex | 用途 |
|-------|-----|------|
| `color_success` | `#22C55E` | 在线、成功 |
| `color_warning` | `#F59E0B` | 解析中、注意 |
| `color_error` | `#EF4444` | 错误、结束对话 |
| `color_error_container` | `#FEE2E2` | 危险浅底 |
| `color_on_error_container` | `#B91C1C` | |
| `color_info` | `#3B82F6` | 与 primary 对齐 |

### 3.4 图表专用（管理端）

| Token | Hex | 含义 |
|-------|-----|------|
| `chart_positive` | `#22C55E` | 正面情绪 |
| `chart_neutral` | `#94A3B8` | 中性 |
| `chart_negative` | `#EF4444` | 负面 |
| `chart_line_primary` | `#3B82F6` | 主折线 |
| `chart_line_secondary` | `#38BDF8` | 对比系列 |

### 3.5 视觉规则

- **浅色优先**：页面、列表、偏好、个人中心均为浅底；数字人视频区可深（内容本身），控件栏尽量浅色蓝底
- **高级靠摄影 + 留白**，不靠深色压抑感
- **布局交互不改**：见 §3.6

### 3.6 布局交互铁律（预览/改版均遵守）

| 场景 | 正确行为 | 禁止 |
|------|----------|------|
| 出游记录顶栏 | **筛选标签 + 时间升降序同一行**（标签可横滑，排序靠右） | 拆成上下两行 |
| 出游记录卡片主体 | 点击导航区 → **查看对话记录** | 把「查看记录」做成底栏按钮 |
| 进行中对话 | 底栏仅 **结束** + **继续对话** | 擅自增加第三操作 |
| 已结束对话 | 底栏仅 **评价景点** | 用「查看记录」替代评价 |
| 偏好 / 我的结构 | 保持现有分区与入口层级 | 为了「好看」重排信息架构 |

---

## 4. 旧色 → 新色映射（迁移表）

落地时先改 `colors.xml` alias，再清硬编码。

| 旧资源 / 硬编码 | 新 token |
|-----------------|----------|
| `tab_selected` `#165EFE` | `color_primary` |
| `nav_text_selected` `#FF5722` | `color_primary`（导航选中归品牌，不再用橙） |
| `profile_primary` `#004BD7` 及容器系列 | → 上表 primary 系 |
| `login_primary` / `chat_primary` `#5241CD` | → `color_primary`（**废紫**） |
| `splash_accent` `#2F6BFF` | → `color_primary` |
| `#8B5CF6` / `#4F46E5`（管理端） | → `color_primary` / `color_primary_container` |
| `#F59E0B` | → `color_warning` 或 `color_accent`（仅 CTA） |
| `#2196F3` | → `color_primary` |
| `profile_star_yellow` | → `color_accent`（星级） |
| `chat_green_dot` / `chat_green_text` | → `color_success` |
| 布局内 `#333/#666/#999/#111` | → `on_surface` / `on_surface_variant` / `muted` |
| `#F9FAFB` / `#EEEEEE` 背景 | → `color_background` / `color_surface_variant` |

废弃策略：旧名可暂作 **alias 指向新色**（兼容编译），新布局禁止再引用 `login_*` / `chat_*` / `profile_*` 前缀；下一迭代删除前缀色。

---

## 5. 字体（Android）

ui-ux-pro-max 推荐 **Noto Sans SC**（简体中文可读）。

| 角色 | 实现 |
|------|------|
| 标题 / 正文 | `sans-serif-medium` / `sans-serif`（系统），有条件可嵌入 Noto Sans SC |
| 字号阶梯 | 12 / 14 / 16 / 20 / 24 / 28 sp（与现有 Profile TextAppearance 对齐，统一到 `styles.xml`） |
| 禁止 | 同屏混用随意 dp 字号（现有 Profile 有用 `dp` 写字号，应改为 `sp`） |

---

## 6. 圆角 · 间距 · 海拔

| Token | 值 |
|-------|-----|
| `radius_sm` | 8dp |
| `radius_md` | 12dp |
| `radius_lg` | 16dp |
| `radius_full` | 999dp（胶囊按钮/chip） |
| 间距节奏 | 4 / 8 / 12 / 16 / 24 / 32 dp |
| 海拔 | 默认扁平；对话框 / 底部 sheet 可用 2–8dp，避免多层彩色阴影 |

与现有 `RoundedCorner8dp` / `12dp` / `24dp` 对齐：保留 8/12，大卡片统一 **16dp**，少用 24 除非全圆头像。

---

## 7. 组件用色规则（防再次混乱）

| 组件 | 规则 |
|------|------|
| 主按钮 | `primary` 底 + `on_primary` 字 |
| 次按钮 | `surface` + `outline` 描边 + `on_surface` 字 |
| 危险按钮 | `error_container` + `on_error_container`（已有 Profile 登出样式可复用） |
| Tab / BottomNav 选中 | **只用** `primary`，禁用橙/紫 |
| Chip 选中 | `secondary` 或 `primary_container`，全端二选一并写死一种 |
| 聊天气泡（用户） | `primary_container` |
| 聊天气泡（AI） | `surface` + 浅描边 |
| 输入框 | `surface_variant` 底，聚焦环 `primary` |
| 管理端主 CTA | 与用户端同 `primary`，禁止页面私有紫按钮 |

---

## 8. 用户端 vs 管理端

同一套色板，**密度不同**：

| | 用户端 | 管理端 |
|--|--------|--------|
| 气质 | 轻、白、大触控 | 信息密、KPI 清晰 |
| 背景 | `background` | 同左，列表可 `surface` 卡片 |
| 图表 | 少用 | 必须用 §3.4 chart token |
| 强调橙 | 评分、关键转化 | 告警/待办角标，不作整页主题 |

---

## 9. 动效与无障碍（交付底线）

- 按压反馈：150–300ms，opacity / ripple，不位移布局
- 触控热区 ≥ 48×48dp
- 正文对比 ≥ 4.5:1；次要文案 ≥ 3:1
- 成功/失败：**色 + 图标/文案**
- `prefers-reduced-motion` / 系统动画缩放开启时缩短或关闭装饰动效
- 图标-only 按钮需 `contentDescription`

---

## 10. 落地顺序（建议）

1. **重写** `res/values/colors.xml`：语义 token + 旧名 alias  
2. **统一** `themes.xml`：`colorPrimary` 等全部指向新 token（night 主题补齐，勿空壳）  
3. **清硬编码**：按文件批量替换 layout 中 `#` 色值为 `@color/...`（优先管理端大文件）  
4. **删页面私有色前缀**：`login_*` / `chat_*` / `splash_*` 合并进语义色  
5. **视觉走查**：登录 → 主页 Tab → 聊天 → 个人中心 → 景点管理 → 数据分析，确认主色只出现蓝系一种品牌印象

---

## 11. 一句话品牌印象

> **晴空蓝可信、青绿作辅、橘色只点睛** —— 户外导游该有的干净，而不是五套主题拼贴。

---

*文档版本：v0.3 · Sky Travel 浅蓝主色。配图提示词见 `image-prompts.md`。仅设计规范，未改 Android 代码。*
)
