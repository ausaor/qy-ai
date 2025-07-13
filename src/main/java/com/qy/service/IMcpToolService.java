package com.qy.service;

import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;

public interface IMcpToolService {

    Map<String, Object> getCityWeather(@ToolParam(description = "城市名称") String cityName,
                                       @ToolParam(description = "日期") String date);
}
