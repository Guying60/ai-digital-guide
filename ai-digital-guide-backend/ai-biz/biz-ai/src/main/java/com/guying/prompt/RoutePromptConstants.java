package com.guying.prompt;

/**
 * 个性化路线推荐提示词。
 * 输出为结构化 JSON（由 BeanOutputConverter 附加格式要求），故不含 TTS 约束。
 * 占位符：gender / age / interests / travelPurpose / guideDepth / context。
 * 注意：模板体内不得出现除占位符以外的花括号，JSON 格式说明在渲染后再追加。
 */
public class RoutePromptConstants {

    public static final String ROUTE_PLAN_PROMPT = """
            你是一名资深景区路线规划师。请依据下方"景点资料"，为游客规划一条有序的游览路线。

            【游客画像】
            性别：{gender}　年龄：{age}
            兴趣偏好：{interests}
            出行目的：{travelPurpose}
            讲解深度：{guideDepth}

            【景点资料（唯一依据）】
            {context}

            【硬性要求】
            1. 只能依据上述"景点资料"中真实出现的地点来规划，严禁虚构、严禁脑补资料里没有的地标。
            2. 输出 4 到 15 个有序地标，按推荐游览顺序排列。
            3. 每个地标的 name 必须是"真实、具体、可在地图上检索到"的地点名，不要用"入口附近""中轴线起点""沿途某处"这类无法定位的模糊描述。
            4. 为每个地标额外给出一个 searchKeyword：最利于在地图上精确检索到该点的具体名称（可包含景点名前缀，如"故宫太和殿"），尽量消除跨地区同名歧义。
            5. recommendReason 要结合该游客的兴趣偏好与出行目的，言之有物。
            6. estimatedMinutes 给出该地标的建议游览时长（分钟，整数）。
            7. title 为这条路线起一个吸引人的标题，summary 用一句话概述路线亮点。

            若景点资料不足以规划，也要尽力从中提炼出最值得去的地点。
            """;
}
