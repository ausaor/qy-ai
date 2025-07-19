package com.qy.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qy.service.IMcpToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class McpToolServiceImpl implements IMcpToolService {
    @Value("${tian-api.key}")
    private String tianApiKey;

    @Override
    @Tool(name = "getCityWeather", description = "获取城市天气")
    public Map<String, Object> getCityWeather(
            @ToolParam(description = "城市名称") String cityName,
            @ToolParam(description = "日期") String date) {
        log.info("获取城市天气: {}", cityName);
        log.info("获取城市天气: {}", date);

        Map<String, Object> result = new HashMap<>();

        Map<String, Object> params = new HashMap<>();
        params.put("key", tianApiKey);
        params.put("city", cityName);
        params.put("type", 1);

        try {
            String resultStr = HttpUtil.get("https://apis.tianapi.com/tianqi/index", params);
            JSONObject jsonObject = JSONUtil.parseObj(resultStr);
            if (MapUtil.isNotEmpty(jsonObject) && "200".equals(jsonObject.getStr("code"))) {
                JSONObject resultJson = jsonObject.getJSONObject("result");
                result.put("province", resultJson.getStr("province"));
                result.put("area", resultJson.getStr("area"));
                result.put("date", resultJson.getStr("date"));
                result.put("week", resultJson.getStr("week"));
                result.put("weather", resultJson.getStr("weather"));
                result.put("real", resultJson.getStr("real"));
                result.put("lowest", resultJson.getStr("lowest"));
                result.put("highest", resultJson.getStr("highest"));
                result.put("wind", resultJson.getStr("wind"));
                result.put("windspeed", resultJson.getStr("windspeed"));
                result.put("windsc", resultJson.getStr("windsc"));
                result.put("tips", resultJson.getStr("tips"));
            }
        } catch (Exception e) {
            result.put("cityName", cityName);
            result.put("date", date);
            result.put("error", "获取天气数据失败: " + e.getMessage());
        }
        log.info("获取城市天气结果: {}", result);
        return result;
    }
}
