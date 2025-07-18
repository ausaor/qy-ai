# QyAI 项目说明

## 项目简介
QyAI 是一个基于 Spring Boot 的 AI 聊天服务项目，支持多种 AI 模型和流式消息回复。该项目集成了 DeepSeek 和 Qwen 等 AI 模型，提供多种交互方式，包括流式响应（Flux）、SSE（Server-Sent Events）和 MCP（Model Control Protocol）协议。

## 主要功能
- 支持多种 AI 模型（如 DeepSeek 和 Qwen）。
- 提供流式消息回复接口（Flux 和 SSE）。
- 支持 MCP 协议进行模型控制。
- 集成 JWT 认证和 Redis 缓存。
- 提供全局异常处理和统一的响应格式。

## 技术架构
- **后端框架**：Spring Boot 2.x
- **AI 模型集成**：Spring AI
- **认证机制**：JWT
- **缓存**：Redis
- **日志**：Logback
- **异常处理**：全局异常处理器
- **跨域支持**：CorsFilter

## 项目结构
```
src/
├── main/
│   ├── java/
│   │   └── com/qy/
│   │       ├── QyAiApplication.java
│   │       ├── config/        # 配置类
│   │       ├── controller/    # 控制器类
│   │       ├── enums/         # 枚举类
│   │       ├── exception/     # 异常处理类
│   │       ├── factory/       # 工厂类
│   │       ├── interceptor/   # 拦截器类
│   │       ├── model/         # 数据模型类
│   │       ├── result/        # 响应结果类
│   │       ├── service/       # 服务接口及实现
│   │       ├── session/       # 用户会话类
│   │       ├── util/          # 工具类
│   │       └── contant/       # 常量类
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-prod.yml
│       └── logback.xml
└── test/
    └── java/
        └── com/qy/
            └── QyAiApplicationTests.java
```

## 配置说明
- **application.yml**：主配置文件，包含通用配置。
- **application-dev.yml**：开发环境配置文件。
- **application-prod.yml**：生产环境配置文件。
- **logback.xml**：日志配置文件。

## 使用方式

### 启动项目
1. 确保已安装 JDK 1.8 或更高版本。
2. 确保已安装 Maven。
3. 执行以下命令启动项目：
   ```bash
   mvn spring-boot:run
   ```

### 流式请求接口
#### DeepSeek 模型
- **流式获取消息回复（Flux）**
  - URL: `/chat/stream/msg/{sessionId}`
  - Method: GET
  - Parameters:
    - `sessionId`: 会话 ID
    - `content`: 消息内容
    - `role`: 角色（默认为 "user"）
    - `model`: 模型名称
    - `maxTokens`: 最大 token 数量（可选）
    - `temperature`: 温度参数（可选）

- **SSE 获取消息回复**
  - URL: `/chat/sse/msg/{sessionId}`
  - Method: GET
  - Parameters:
    - `sessionId`: 会话 ID
    - `content`: 消息内容
    - `role`: 角色（默认为 "user"）
    - `model`: 模型名称

- **MCP 流式获取消息回复**
  - URL: `/chat/mcp/msg/{sessionId}`
  - Method: GET
  - Parameters:
    - `sessionId`: 会话 ID
    - `content`: 消息内容
    - `role`: 角色（默认为 "user"）
    - `model`: 模型名称

#### Qwen 模型
- **流式获取消息回复（Flux）**
  - URL: `/chat/flux/msg/{sessionId}`
  - Method: GET
  - Parameters:
    - `sessionId`: 会话 ID
    - `content`: 消息内容
    - `role`: 角色（默认为 "user"）
    - `model`: 模型名称

## 服务实现
- **DeepSeekChatImpl**：实现 DeepSeek 模型的聊天服务。
- **QianWenAiChatServiceImpl**：实现 Qwen 模型的聊天服务。
- **SseServiceImpl**：实现 SSE 流式消息服务。
- **McpToolServiceImpl**：实现 MCP 工具服务。

## 贡献指南
欢迎贡献代码！请遵循以下步骤：
1. Fork 项目。
2. 创建新分支。
3. 提交代码更改。
4. 创建 Pull Request。

## 协议
本项目采用 MIT 许可证。详情请参阅 [LICENSE](LICENSE) 文件。

## 联系方式
如有问题或建议，请联系项目维护者或提交 Issue。