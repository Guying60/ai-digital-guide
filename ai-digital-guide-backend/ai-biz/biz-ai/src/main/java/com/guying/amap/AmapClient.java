package com.guying.amap;

import com.guying.common.constants.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 高德 Web 服务客户端：仅用于把地标名解析为坐标（POI 文本/周边检索），<b>不做路径规划</b>。
 *
 * <p>降级原则：未配置 key / 高德返回异常 / 无结果，一律返回 {@code null}，由调用方按 resolved=false 处理。
 * 解析结果写入 Redis 缓存以省配额、保持一致性。坐标系为 GCJ-02。
 */
@Component
@Slf4j
public class AmapClient {

    private static final long REQUEST_INTERVAL_MILLIS = 500L;
    private static final int CUQPS_MAX_RETRIES = 1;
    private static final long CUQPS_RETRY_DELAY_MILLIS = 1000L;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private static final String TEXT_URL =
            "https://restapi.amap.com/v5/place/text?key={key}&keywords={kw}&page_size=1";
    private static final String TEXT_REGION_URL =
            "https://restapi.amap.com/v5/place/text?key={key}&keywords={kw}&region={region}&city_limit=true&page_size=1";
    private static final String AROUND_URL =
            "https://restapi.amap.com/v5/place/around?key={key}&location={loc}&radius={radius}&keywords={kw}&page_size=1";

    @Value("${spring.amap.web-key:}")
    private String webKey;

    private final RestClient restClient = createRestClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final StringRedisTemplate stringRedisTemplate;
    private final Object requestRateLock = new Object();
    private long nextRequestAtNanos;

    public AmapClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 是否可用（已配置 Web 服务 key）。 */
    public boolean enabled() {
        return webKey != null && !webKey.isBlank();
    }

    /**
     * 解析景点锚点：用 POI 文本检索拿到景点的坐标 + adcode + 城市（后续地标 around 检索与前端 region 限定都依赖它）。
     * 结果按 attractionId 缓存。
     */
    public AmapPoi resolveAttractionAnchor(Long attractionId, String attractionName) {
        if (!enabled() || attractionName == null || attractionName.isBlank()) return null;

        String cacheKey = RedisConstants.AMAP_ANCHOR_KEY + attractionId;
        AmapPoi cached = readCache(cacheKey);
        if (cached != null) return cached;

        try {
            AmapPoi poi = firstPoi(TEXT_URL, webKey, attractionName);
            if (poi != null) {
                writeCache(cacheKey, poi, RedisConstants.AMAP_ANCHOR_EXPIRE_TIME, TimeUnit.DAYS);
            }
            return poi;
        } catch (AmapQuotaExceededException e) {
            return null;
        }
    }

    /**
     * 解析单个地标坐标：优先在景点锚点周边检索，失败回退按城市(adcode)限定的文本检索。
     * 结果按 adcode:keyword 缓存。
     */
    public AmapPoi resolveLandmark(String keyword, AmapPoi anchor) {
        if (!enabled() || keyword == null || keyword.isBlank()) return null;

        String adcode = anchor == null ? "" : (anchor.adcode() == null ? "" : anchor.adcode());
        String cacheKey = RedisConstants.AMAP_POI_KEY + adcode + ":" + keyword;
        AmapPoi cached = readCache(cacheKey);
        if (cached != null) return cached;

        try {
            AmapPoi poi = null;
            if (anchor != null && anchor.longitude() != null && anchor.latitude() != null) {
                String loc = anchor.longitude() + "," + anchor.latitude();
                poi = firstPoi(AROUND_URL, webKey, loc, "5000", keyword);
            }
            if (poi == null && !adcode.isBlank()) {
                poi = firstPoi(TEXT_REGION_URL, webKey, keyword, adcode);
            }
            if (poi == null) {
                poi = firstPoi(TEXT_URL, webKey, keyword);
            }
            if (poi != null) {
                writeCache(cacheKey, poi, RedisConstants.AMAP_POI_EXPIRE_TIME, TimeUnit.DAYS);
            }
            return poi;
        } catch (AmapQuotaExceededException e) {
            return null;
        }
    }

    /** 发请求并解析首条 POI，任何异常/无结果返回 null（触发降级）。 */
    private AmapPoi firstPoi(String urlTemplate, Object... uriVars) {
        for (int attempt = 0; attempt <= CUQPS_MAX_RETRIES; attempt++) {
            try {
                String body = executeRateLimitedRequest(urlTemplate, uriVars);
                if (body == null) return null;
                JsonNode root = objectMapper.readTree(body);
                if (!"1".equals(root.path("status").asText())) {
                    String infocode = root.path("infocode").asText();
                    log.warn("高德返回异常 status={} info={} infocode={}",
                            root.path("status").asText(),
                            root.path("info").asText(),
                            infocode);
                    if ("10021".equals(infocode)) {
                        if (attempt < CUQPS_MAX_RETRIES) {
                            Thread.sleep(CUQPS_RETRY_DELAY_MILLIS);
                            continue;
                        }
                        throw new AmapQuotaExceededException();
                    }
                    return null;
                }
                JsonNode pois = root.path("pois");
                if (!pois.isArray() || pois.isEmpty()) return null;
                return parsePoi(pois.get(0));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (AmapQuotaExceededException e) {
                throw e;
            } catch (Exception e) {
                log.warn("高德请求失败：{}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    private String executeRateLimitedRequest(String urlTemplate, Object... uriVars) throws InterruptedException {
        synchronized (requestRateLock) {
            long waitNanos = nextRequestAtNanos - System.nanoTime();
            if (waitNanos > 0) {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            }
            nextRequestAtNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REQUEST_INTERVAL_MILLIS);
        }
        return restClient.get().uri(urlTemplate, uriVars).retrieve().body(String.class);
    }

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private static class AmapQuotaExceededException extends RuntimeException {
    }

    private AmapPoi parsePoi(JsonNode poi) {
        String location = poi.path("location").asText(); // "lng,lat"
        Double lng = null, lat = null;
        if (!location.isBlank() && location.contains(",")) {
            String[] parts = location.split(",");
            try {
                lng = Double.parseDouble(parts[0].trim());
                lat = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return new AmapPoi(
                poi.path("id").asText(),
                poi.path("name").asText(),
                poi.path("address").asText(),
                lng, lat,
                poi.path("adcode").asText(),
                poi.path("cityname").asText()
        );
    }

    private AmapPoi readCache(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, AmapPoi.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeCache(String key, AmapPoi poi, long ttl, TimeUnit unit) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(poi), ttl, unit);
        } catch (Exception e) {
            log.debug("高德缓存写入失败：{}", e.getMessage());
        }
    }
}
