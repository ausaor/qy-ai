

# QyAI 项目说明

## 项目简介
QyAI 是一个多功能 AI 对话服务平台，支持多种 AI 模型和交互模式。该项目旨在提供一个可扩展的框架，用于集成不同的 AI 服务和工具，支持流式对话和非流式对话。

## 主要功能
- 支持多种 AI 模型（如 DeepSeek、QianWen）进行对话。
- 提供流式对话接口（Flux、SSE、MCP）。
- 支持通过工具扩展 AI 的能力，例如获取城市天气。
- 高度可配置，适用于开发环境和生产环境。

## 技术架构
本项目基于 Spring Boot 和 Spring WebFlux 技术栈，结合以下关键组件：
- **OpenAI API 集成**：通过 `OpenAiApi` 和 `ChatModel` 实现。
- **流式响应支持**：使用 Spring WebFlux 的 `Flux` 和 Spring MVC 的 `SseEmitter`。
- **模块化设计**：通过 `ChatServiceFactory` 动态选择不同的聊天服务实现。
- **MCP 工具集成**：支持工具扩展 AI 的能力，如天气查询。

## 项目结构
```
com/qy/
├── config/                   # 配置类，包括模型参数、跨域设置、SSE 配置等
├── controller/               # 提供 REST API 接口，包含流式和 SSE 对话接口
├── enums/                    # 枚举类，如 ChatModeType
├── factory/                  # 工厂类，用于动态获取聊天服务
├── model/                    # 数据模型，包含请求、响应和 DTO 类
├── service/                  # 服务接口及其实现，包括 IChatService、ISseService 等
├── util/                     # 工具类，如 SSEUtil
└── QyAiApplication.java      # 主启动类
```

## 配置说明
- **application.yml**：主要配置文件，包含 AI 服务的 API Key、模型参数、温度值、最大 token 数等。
- **application-dev.yml**：开发环境配置。
- **application-prod.yml**：生产环境配置。
- **logback.xml**：日志配置文件。

## 使用方式

### 启动项目
1. 确保已安装 Java 17+ 和 Maven。
2. 执行命令：
   ```bash
   mvn spring-boot:run
   ```
3. 项目默认启动端口为 `8080`。

### 流式请求接口
以下为部分支持的接口示例：

- **流式获取消息回复**：
  - GET `/chat/stream/msg/{sessionId}` 
  - 参数：`content`, `role`, `maxTokens`, `temperature`
  - 响应类型：`Flux<ChatResponse>`，适用于 Spring WebFlux。

- **SSE 获取消息回复**：
  - GET `/chat/sse/msg/{sessionId}`
  - 参数：`content`, `role`, `model`
  - 响应类型：`SseEmitter`，适用于 Server-Sent Events。

- **MCP 流式获取消息回复**：
  - GET `/chat/mcp/msg/{sessionId}`
  - 参数：`content`, `role`, `model`
  - 响应类型：`Flux<ServerSentEvent<String>>`。

## 服务实现
- **DeepSeekChatImpl**：DeepSeek AI 模型的具体实现，处理流式对话、SSE 和 MCP 接口。
- **QianWenAiChatServiceImpl**：阿里云 Qianwen 模型的实现，支持 DashScope API。
- **McpToolServiceImpl**：实现 MCP 工具，例如 `getCityWeather`，供 AI 调用。

## 贡献指南
欢迎提交 Issue 和 Pull Request。贡献者应遵循项目的代码风格，并确保代码测试覆盖率。

## 协议
该项目遵循 MIT 协议，请参阅 `LICENSE` 文件获取详细信息。

## 联系方式
如有问题或建议，请提交 Issue 到 [Gitee 项目页面](https://gitee.com/auraor/qy-ai)。