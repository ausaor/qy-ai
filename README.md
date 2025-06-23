

# QyAI 项目说明

## 项目简介
QyAI 是一个AI聊天服务项目，旨在为用户提供与不同AI模型进行交互的能力。该项目支持流式和SSE（Server-Sent Events）消息回复，允许实时聊天体验。

## 主要功能
- 提供流式获取AI消息回复的接口
- 提供SSE方式获取AI消息回复的接口
- 支持多种AI模型，包括DeepSeek和QianWen
- 集成聊天服务工厂模式，便于扩展新AI模型

## 技术架构
QyAI项目基于Spring Boot框架构建，使用了Spring WebFlux和Spring Web模块来处理HTTP请求。项目利用了Spring的配置管理和依赖注入功能，以及Java的枚举和工具类来组织代码。通过Flux和SseEmitter实现了响应式的流式消息处理。

## 项目结构
QyAI项目由多个模块组成，包括配置、控制器、模型、服务及其具体实现。使用工厂模式来管理不同的聊天服务实现，使得代码结构清晰，易于维护和扩展。

## 配置说明
项目使用application.yml作为主要配置文件，包含以下配置项：
- API密钥
- API基础URL
- AI模型名称
- 温度参数（temperature）
- 最大token数（max-tokens）

## 使用方式

### 启动项目
确保已安装Java和Maven，然后运行以下命令：
```bash
mvnw spring-boot:run
```

### 发送流式请求
使用GET请求发送流式消息，请求示例：
```
GET /chat/stream/msg/{sessionId}?content=你好&role=user
```

### 服务实现
目前支持两种AI模型服务：
- DeepSeek AI
- QianWen AI

每个服务都实现了流式消息处理和SSE消息处理功能。

## 贡献指南
我们欢迎社区的贡献！请确保遵循以下指导：
1. Fork项目并创建新分支
2. 提交Pull Request并确保代码质量
3. 遵循项目代码风格和规范

## 协议
本项目采用MIT协议。

## 联系方式
如需联系，请参考项目的英文文档(README.en.md)。