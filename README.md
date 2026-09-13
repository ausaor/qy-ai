# QyAI 项目说明

## 项目简介

QyAI 是一个基于 **Spring Boot 4 + Spring AI 2.0** 的 AI 聊天服务项目，同时接入 **DeepSeek** 与**通义千问**两套大模型（均通过 OpenAI 兼容接口），支持文本对话、多模态对话（图片/音频/视频）、多 Agent 智能体路由、NL2SQL 数据查询、高级 RAG 文档问答等能力，并以多种流式方式（Flux / SSE / ServerSentEvent）向客户端推送回复。

## 主要功能

### 1. 多模型对话
- 接入 DeepSeek（`deepseek-v4-pro`）与通义千问（`qwen-plus`，DashScope compatible-mode），通过 `ChatServiceFactory` 按模型类别动态路由到对应服务实现。
- 提供三种流式回复方式：
  - **Flux\<ChatResponse\>**：WebFlux 响应式流，逐 token 返回完整响应对象；
  - **Flux\<ServerSentEvent\>**：SSE 事件流，纯文本增量推送；
  - **SseEmitter**：Spring MVC 异步流式响应。
- 支持自定义 `temperature`、`maxTokens` 等生成参数。

### 2. 多模态对话（通义千问）
- 基于 `qwen3.5-omni-plus` 多模态模型，支持上传图片、音频（MP3/WAV）、视频等附件随文本提问。
- 图片走 Spring AI 的 Media 转换；音频/视频因 DashScope 兼容模式要求 `input_audio.data` 为 URL/data URL、且 Spring AI 2.0 会将 `video/*` 降级为文本，故底层改用 openai-java SDK 直接构造请求（含视频 `video_url` content part 的反射构造）。

### 3. 多 Agent 智能体系统
统一入口 `POST /agent/chat/{sessionId}`，由 **Router Agent** 自动识别意图并分发到专业 Agent：

| Agent | 职责 | 挂载工具 |
| --- | --- | --- |
| `router` | 意图分类与路由分发 | `routeToAgent`（含同名兼容别名工具） |
| `general` | 通用对话与问答 | `getCityWeather`（天行数据实时天气） |
| `text_to_sql` | 自然语言转 SQL 数据查询 | `listTables` / `getTableStructure` / `explainQuerySql` / `executeQuerySql` |
| `document_qa` | 文档问答（RAG） | 高级 RAG 管线 Advisor |

**NL2SQL 安全保障**：仅允许只读查询（SELECT/WITH），多层拦截写操作关键字、系统库、多语句拼接与 SQL 注入；强制 EXPLAIN 预校验（`FORMAT=TRADITIONAL` 兼容 MySQL 9.x）、自动补 LIMIT、结果行数与字段值截断；Router 侧还基于线程内查询执行计数做“防编造兜底”，模型未真实执行查询时会自动补齐执行环节。

**路由健壮性**：Router 不挂载记忆（防止历史诱导跳过路由）；通过 `ToolContext` 向子 Agent 传递 `conversationId` 保持多轮上下文；内置循环路由检测，避免“路由→工具→再路由”死循环。

### 4. 高级 RAG 管线
自研多阶段 RAG 管线，按序执行：

```
查询改写(Rewrite) → 多查询扩展(MultiQuery x3) → 混合检索 → RRF 融合 → LLM 重排序 → 引用增强([N] 标注)
```

- **混合检索**：Redis 向量检索（`text-embedding-v4` 向量模型）+ 纯 Java 内存 BM25 关键词检索，并行执行、失败降级互不阻塞；
- **RRF 融合**：Reciprocal Rank Fusion（k=60）合并两路结果并自然去重；
- **LLM 重排序**：使用轻量快速模型 `qwen-turbo`（temperature=0、限长）精排，避免大模型长文档列表推理超时；
- **引用增强**：检索片段以 `[N] (来源: xxx.pdf, 第n块)` 格式注入提示词，回答带引用标注。

### 5. 文档上传与索引
- 支持 PDF、Word（doc/docx）、Markdown、纯文本、RTF 多格式上传；
- 多格式读取器自动识别类型（PDF/Markdown/Text 专用 Reader，其余走 Tika）；
- 语义分块（TokenTextSplitter，chunk 300 token）后**双写**：批量写入 Redis 向量库（DashScope embedding 每批最多 10 条）+ 同步写入 BM25 内存索引；
- 异步处理（`@Async`），上传即返回，临时文件自动清理；提供 `/doc/stats` 查询索引统计。

### 6. 对话记忆（Redis 持久化）
- 自定义 `RedisChatMemoryRepository` 实现 Spring AI 2.0 的 `ChatMemoryRepository`，配合 `MessageWindowChatMemory` 滑动窗口；
- 每个会话一个 Redis String 键（`ai:chat:memory:{conversationId}`），JSON 存储，TTL 默认 7 天、每次保存自动续期；
- 主动过滤 TOOL 消息与携带 toolCalls 的 AssistantMessage，防止历史诱导模型跳过工具调用；
- Redis 不可用时自动降级，不影响对话主流程。

### 7. 聊天记录落库
- 会话（`ai_chat_session`）与消息（`ai_chat_message`）通过 MyBatis-Plus 持久化到 MySQL，逻辑删除、雪花 ID 自动填充。

### 8. 认证与权限
- **JWT 认证**（auth0 java-jwt）：`AuthInterceptor` 拦截全部请求，从 `accessToken` 请求头解析用户会话（存入请求上下文 `SessionContext`），校验签名与封禁状态；
- **角色权限**：`@RequireRoles` 注解 + `RoleAuthAspect` 切面实现接口级角色控制（如文档上传仅限 `SUPER_ADMIN`）。

### 9. 基础设施能力
- **Redis 缓存**：`RedisCache` 通用工具类 + `CacheManager` 注解缓存；
- **MCP Server**：启用 Streamable-HTTP 协议（端点 `/ai/chat`），可将应用能力以 MCP 协议对外暴露；
- **全局异常处理**：统一 `Result` 响应格式（code/message/data）；
- **CORS 跨域**：过滤器级全路径跨域支持；
- **日志**：Logback（`logback.xml`）。

## 技术架构

- **后端框架**：Spring Boot 4.0.8（JDK 21）
- **AI 集成**：Spring AI 2.0.1（ChatClient、Advisor、RAG、Redis 向量存储、MCP Server）
- **模型 SDK**：openai-java 4.39.1（OkHttp 客户端），DeepSeek / DashScope 均走 OpenAI 兼容接口
- **数据库**：MySQL（HikariCP 连接池）+ MyBatis-Plus 3.5.17（spring-boot4 专用 starter）
- **缓存/向量库**：Redis（spring-boot-starter-data-redis + Jedis 7.4.1），RedisVectorStore + 自研 BM25 内存索引
- **认证**：JWT（auth0 java-jwt 3.11.0）+ 拦截器 + AOP 权限切面
- **Web**：Spring WebMVC（SseEmitter）+ Spring WebFlux（Flux/SSE），缓冲区提升至 16MB
- **工具库**：Hutool 5.8.26、Lombok 1.18.42
- **JSON**：Jackson 3（`tools.jackson`，Spring Boot 4 默认）
- **日志**：Logback

### 核心 Bean 架构

- `deepseekChatModel` / `qianwenChatModel`：两个 `OpenAiChatModel` Bean 共存（须同时设置同步与异步客户端），通过 `@Qualifier` 按需注入；
- `qianwenEmbeddingModel`：专用于向量库写入的 Embedding 模型（DeepSeek 不提供 embedding API）；
- 多 Agent 的 ChatClient 在 `MultiAgentConfiguration` 中构建并注册到 `AgentRegistry`，Router 构建提示词时动态读取已注册 Agent 清单。

## 快速开始

### 环境要求

| 依赖 | 版本要求 | 说明 |
| --- | --- | --- |
| JDK | 21+ | 终端默认 JDK 非 21 时需显式设置 `JAVA_HOME` |
| Maven | 3.6+ | 也可直接使用项目自带 `./mvnw` |
| MySQL | 8.x / 9.x | 本地库名 `qy-im` |
| Redis | 6+（标准版即可） | 无需 Redis Stack |

### 配置 API 密钥

密钥通过环境变量外部化注入（配置中为空占位符）：

```bash
export DEEPSEEK_API_KEY=你的DeepSeek密钥
export QIANWEN_API_KEY=你的DashScope密钥
export TIAN_API_KEY=你的天行数据密钥   # 天气工具，可选
```

### 启动项目

```bash
# 开发环境（默认激活 dev profile）
./mvnw spring-boot:run
# 或指定环境
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

启动后服务地址：`http://localhost:8181/ai`（端口 8181，context-path `/ai`）。

## 接口文档

> 所有接口需在请求头携带 `accessToken`（JWT），由 `AuthInterceptor` 统一校验。

### 对话接口 `/ai/chat`

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/chat/stream/msg/{sessionId}` | GET | Flux 流式对话，参数 `content`、`model`（`deepseek` / `qianwen`） |
| `/chat/flux/msg/{sessionId}` | POST | 多模态 SSE 流式对话（multipart），参数 `content`、`model`、`chatType=chat`、`files`（可选，图片/音频/视频） |
| `/chat/sse/msg/{sessionId}` | GET | SseEmitter 流式对话，参数 `content`、`model` |

### 多 Agent 接口 `/ai/agent`

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/agent/chat/{sessionId}` | POST | 智能体统一入口（multipart），参数 `content`、`model`、`chatType=agent`；Router 自动路由至 text_to_sql / document_qa / general |

### 文档接口 `/ai/doc`

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/doc/upload` | POST | 上传文档（PDF/Word/Markdown/TXT/RTF），需 `SUPER_ADMIN` 角色，异步写入向量库与 BM25 索引 |
| `/doc/stats` | GET | 查询已索引文档统计（文档块数、来源文件数） |

### MCP 端点

MCP Server 通过 Streamable-HTTP 协议暴露，端点：`http://localhost:8181/ai/chat`。

## 项目结构

```
src/main/java/com/qy/
├── QyAiApplication.java      # 启动类（@EnableAsync + @MapperScan）
├── agent/                    # 多 Agent：注册中心、路由工具、类型枚举
│   ├── AgentRegistry.java
│   ├── AgentRouterTool.java
│   └── AgentType.java
├── annotation/               # 自定义注解
│   └── RequireRoles.java     # 接口角色权限注解
├── aspect/                   # AOP 切面
│   └── RoleAuthAspect.java   # 角色权限校验切面
├── config/                   # 配置类
│   ├── AdvancedRagConfiguration.java   # 高级 RAG 管线配置
│   ├── ChatClientConfig.java           # DeepSeek/千问 ChatClient
│   ├── ChatMemoryConfig.java           # 对话记忆（Redis 持久化 + 滑动窗口）
│   ├── CommonConfiguration.java        # Redis 连接、RedisVectorStore
│   ├── CorsFilterConfig.java           # CORS 跨域
│   ├── DeepseekAiConfig.java           # DeepSeek 客户端与模型 Bean
│   ├── JwtProperties.java              # JWT 配置属性
│   ├── MultiAgentConfiguration.java    # 多 Agent ChatClient Bean
│   ├── MvcConfig.java                  # 拦截器注册
│   ├── QianwenAiConfig.java            # 千问客户端、ChatModel、EmbeddingModel
│   ├── RedisConfig.java                # RedisTemplate 与缓存管理器
│   └── WebFluxConfig.java              # WebFlux 编解码配置
├── contant/                  # 常量
│   ├── PromptConstant.java   # NL2SQL 系统提示词
│   └── RedisKey.java         # Redis 键前缀
├── controller/               # 控制器
│   ├── AgentController.java  # 多 Agent 统一入口
│   ├── ChatController.java   # 对话/多模态接口
│   └── DocumentController.java # 文档上传与统计
├── entity/                   # 实体（ai_chat_session / ai_chat_message）
├── enums/                    # 枚举（模型类别、对话类型、角色、结果码）
├── exception/                # 全局异常与处理器
├── factory/
│   └── ChatServiceFactory.java # 按模型类别路由聊天服务实现
├── interceptor/
│   └── AuthInterceptor.java  # JWT 认证拦截器
├── mapper/                   # MyBatis-Plus Mapper
├── memory/
│   └── RedisChatMemoryRepository.java # Redis 对话记忆仓库
├── model/                    # 请求模型（ChatRequest / ChatMessageRequest）
├── rag/                      # 高级 RAG 管线组件
│   ├── augmenter/CitationQueryAugmenter.java      # 引用增强
│   ├── joiner/RrfDocumentJoiner.java              # RRF 融合
│   ├── observer/                                   # 查询改写/扩展日志装饰器
│   ├── postprocessor/LlmRerankingPostProcessor.java # LLM 重排序
│   ├── reader/MultiFormatDocumentReader.java      # 多格式文档读取
│   ├── retrieval/                                  # 混合/向量/BM25 检索
│   └── splitter/SemanticDocumentSplitter.java      # 语义分块
├── result/                   # 统一响应（Result / ResultUtils）
├── service/                  # 服务接口与实现
│   └── impl/
│       ├── DeepSeekChatImpl.java       # DeepSeek 对话
│       ├── QianWenAiChatServiceImpl.java # 千问对话（含多模态 SDK 直连）
│       ├── SseServiceImpl.java         # SSE 流式服务编排
│       ├── DocumentServiceImpl.java    # 文档异步入库
│       └── AiChat*ServiceImpl.java     # 会话/消息持久化
├── session/                  # 会话上下文（SessionContext / UserSession）
├── tools/                    # AI 工具
│   ├── CommonTools.java      # 天气查询等通用工具
│   └── TextToSqlTools.java   # NL2SQL 只读安全工具链
└── util/                     # JwtUtil / RedisCache / SSEUtil / VectorDistanceUtils

src/main/resources/
├── application.yml           # 主配置（端口/context-path/Spring AI/记忆/MCP）
├── application-dev.yml       # 开发环境（MySQL/Redis/JWT）
├── application-prod.yml      # 生产环境
└── logback.xml               # 日志配置

src/test/java/com/qy/         # 单元测试
├── memory/                   # Redis 对话记忆仓库测试
├── rag/                      # 文档读取器、BM25 检索器测试
└── service/impl/             # 文档服务测试
```

## 配置说明

### 主配置 `application.yml`

| 配置项 | 说明 |
| --- | --- |
| `server.port` / `server.servlet.context-path` | 端口 8181，上下文路径 `/ai` |
| `spring.ai.openai.*` | DeepSeek 接入（OpenAI 兼容接口，模型 `deepseek-v4-pro`） |
| `spring.ai.qianwen.*` | 通义千问接入（DashScope compatible-mode），含多模态模型 `qwen3.5-omni-plus`、重排序模型 `qwen-turbo`、Embedding 模型 `text-embedding-v4` |
| `spring.ai.chat.memory.*` | 对话记忆：滑动窗口 `max-messages`（默认 20）、Redis TTL `time-to-live`（默认 7d） |
| `spring.ai.mcp.server.*` | MCP Server：启用、STREAMABLE 协议、端点 `/chat` |
| `spring.servlet.multipart.*` | 上传限制 50MB |
| `tian-api.key` | 天气工具密钥（天行数据） |

### 环境配置

- **application-dev.yml**：开发环境数据源（`qy-im` 库）、Redis（localhost:6379）、JWT 密钥与有效期；
- **application-prod.yml**：生产环境配置（含 Redis 密码）；
- 密钥一律通过环境变量注入（`DEEPSEEK_API_KEY` / `QIANWEN_API_KEY` / `TIAN_API_KEY`），配置文件仅保留空占位符。

## 运行测试

```bash
./mvnw test
```

测试覆盖：Redis 对话记忆仓库（单元 + 冒烟）、多格式文档读取器、BM25 检索器、文档入库服务。

## 常见问题

- **启动报 `NoUniqueBeanDefinitionException`**：多个 `OpenAiChatModel` / `OpenAiChatOptions` / `ChatClient` / `OpenAIClient` Bean 共存是设计使然，注入时必须使用 `@Qualifier` 明确指定（如 `qianwenChatModel`）。
- **报 `At least one credential source must be specified`**：手动构建 `OpenAiChatModel` 时必须同时设置 `openAiClient` 与 `openAiClientAsync`，否则会回退到标准配置读取。
- **向量检索返回“未知来源”**：`RedisVectorStore` 需通过 `metadataFields` 声明实际使用的元数据字段（`source_file` / `chunk_index` / `file_type`），否则检索结果会丢失元数据。
- **多轮对话中工具不再被调用**：对话记忆会过滤 TOOL 消息与 toolCalls 助手消息，且 Router 不挂载记忆；若仍出现，请检查 `conversationId` 是否通过 `.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))` 正确传递。

## 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 项目；
2. 创建新分支；
3. 提交代码更改；
4. 创建 Pull Request。

## 协议

本项目采用 MIT 许可证。详情请参阅 [LICENSE](LICENSE) 文件。

## 联系方式

如有问题或建议，请联系项目维护者或提交 Issue。
