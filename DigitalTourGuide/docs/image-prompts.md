# UI 背景配图清单（只要这些）

> **要出的**：作为界面一部分的装饰/氛围背景图。  
> **不要出的**：头像、景点封面、数字人视频封面、对话数字人画面——这些上线后由真实内容上传，预览用色块/文案占位即可。  
> **地图页**：附近景点 / 地图选点上线走高德 SDK，**不需要** UI 氛围配图。

生成后放到 `DigitalTourGuide/docs/preview-assets/`，文件名按约定。

**通用后缀（每条末尾加上）：**
```
soft travel atmosphere, sky-blue and airy daylight, clean premium mobile UI background, no text, no watermark, no logo, no UI chrome, no faces close-up, 4K
```

---

## 需要的 UI 背景

| 文件名 | 用在哪 | 尺寸 | 提示词 |
|--------|--------|------|--------|
| `ui-splash-bg.jpg` | 启动页整页氛围底 | 1170×2532 | Soft abstract sky-blue gradient with gentle light haze and faint distant misty mountains silhouette at bottom third, empty center for logo, calm premium travel app splash background, vertical phone wallpaper |
| `ui-profile-header.jpg` | 「我的」页顶栏背景 | 1170×780 | Wide soft lakeside willow and pavilion under clear blue sky, airy cyan morning light, empty calm scene, designed as blurred app profile header background under white text overlay |
| `ui-chat-stage-bg.jpg` | AI 对话数字人区氛围底（数字人内容另叠） | 1080×1920 | Soft vertical sky-blue gradient with subtle pavilion silhouette and gentle bokeh light, empty mid ground for digital human overlay, travel app chat stage atmosphere, no person |
| `ui-empty-history-bg.jpg` | 出游记录空状态氛围 | 800×1000 | Minimal soft blue-white abstract paper texture with faint compass and map fold shapes very low contrast, lots of empty space, empty-state illustration vibe for travel app |
| `ui-aihuman-stage-bg.jpg` | 数字人管理 · 封面区两侧氛围底（中间 9:16 内容另叠） | 1170×1400 | Soft horizontal sky-blue travel atmosphere card background, empty bright center for a vertical 9:16 portrait frame overlay, faint misty pavilion silhouette at sides only, airy daylight, no person, no phone UI |
| `ui-login-bg.jpg` | 登录页整页氛围底（表单叠在上层） | 1170×2532 | Soft vertical travel login background, airy sky-blue upper half with gentle morning light and wispy clouds, lower two-thirds nearly plain pale blue-white for form fields, faint distant misty mountains and tiny pavilion silhouette only at very bottom edge, lots of empty calm space, premium mobile auth screen, no person |
| `ui-register-bg.jpg` | 注册页整页氛围底（表单叠在上层） | 1170×2532 | Soft vertical travel register background matching login style, sky-blue airy top with soft willow branch framing corners very faintly, mid and lower area clean pale blue-white empty for longer form, subtle lakeside mist and stone bridge silhouette only at bottom edge, calm premium mobile signup screen, no person |

---

## 明确不需要 AI 出图

| 类型 | 原因 |
|------|------|
| 附近景点 / 地图选点底图 | 上线使用高德地图 SDK |
| 景点封面（故宫/西湖等） | 运营上传的真实内容 |
| 用户头像 | 用户自选 / 默认矢量 |
| 数字人视频封面 / 对话数字人帧 | 录制/上传的真实视频内容（不是两侧氛围底） |
| 偏好 / 看板 / 弹窗 | 组件 + 留白即可 |

> 若本地仍有 `ui-map-tint.jpg`，可删，预览已不再引用。  
> 登录 / 注册两张气质尽量统一，可与启动页同系列；中上大面积留白留给标题和输入框。

---

## 状态（2026-07-12）

已嵌入预览：

- `ui-splash-bg.jpg` → 启动页
- `ui-profile-header.jpg` → 我的顶栏
- `ui-chat-stage-bg.jpg` → AI 对话舞台
- `ui-empty-history-bg.jpg` → 出游记录空状态
- `ui-aihuman-stage-bg.jpg` → 数字人管理封面区氛围
- `ui-login-bg.jpg` → 登录页
- `ui-register-bg.jpg` → 注册页

已废弃：`ui-map-tint.jpg`（高德 SDK 替代）
