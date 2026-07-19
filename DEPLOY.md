# 闽师小羽学长 AI智能校园助手 — 部署指南

---

## 📦 项目结构

```
闽师小羽学长/
├── index.html                          # 前端单页（移动端优先）
└── backend/                            # SpringBoot 后端（API中转 + 密钥保护）
    ├── pom.xml
    └── src/main/
        ├── java/com/mnnu/assistant/
        │   ├── MnnuAssistantApplication.java      # 启动类
        │   ├── config/
        │   │   ├── DeepSeekConfig.java            # DeepSeek配置（读yml）
        │   │   ├── AuthConfig.java                # 鉴权配置
        │   │   └── WebConfig.java                 # CORS + 拦截器注册
        │   ├── controller/
        │   │   └── ChatController.java            # POST /api/chat (SSE)
        │   ├── interceptor/
        │   │   └── AuthInterceptor.java           # Token鉴权拦截器
        │   └── service/
        │       └── ChatService.java               # 流式调用DeepSeek
        └── resources/
            └── application.yml                    # 密钥、模型、鉴权配置
```

---

## 🚀 部署步骤

### 前提条件

- **JDK 17+** 已安装（`java -version` 确认）
- **Maven 3.6+** 已安装（`mvn -v` 确认）
- **DeepSeek API Key**（已配置在 `application.yml` 中）

### 第一步：配置后端密钥

编辑 `backend/src/main/resources/application.yml`：

```yaml
deepseek:
  api-key: sk-你的DeepSeek密钥    # ← 修改为你的真实密钥
  base-url: https://api.deepseek.com/v1
  model: deepseek-chat             # deepseek-chat(V3) 或 deepseek-reasoner(R1)

auth:
  token: mnnu-xiaoyu-2025          # ← 建议改成你自己的随机字符串
```

### 第二步：启动后端

```bash
# 进入后端目录
cd backend

# Maven 编译 + 启动（首次会自动下载依赖，约1-2分钟）
mvn spring-boot:run

# 看到以下输出表示启动成功：
# ========================================
#   闽师小羽学长 AI助手后端已启动！
#   接口地址: http://localhost:8080/api/chat
#   鉴权方式: Header X-Auth-Token
#   大模型: DeepSeek (密钥保护在后端)
# ========================================
```

**验证后端：**
```bash
# 健康检查（无需Token）
curl http://localhost:8080/api/health

# 预期返回：{"status":"ok","service":"闽师小羽学长 AI助手后端",...}

# 测试对话（需要Token）
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: mnnu-xiaoyu-2025" \
  -d '{"question":"闽南师范大学是公办的吗？"}'

# 预期：看到SSE流式逐字输出
```

### 第三步：配置前端

编辑 `index.html` 顶部 `<script>` 中的配置（约第680行）：

```javascript
const API_BASE = 'http://localhost:8080';    // 后端地址
// 部署到服务器后改为实际地址，如：
// const API_BASE = 'https://你的域名.com';
const AUTH_TOKEN = 'mnnu-xiaoyu-2025';       // 与后端application.yml中一致
```

### 第四步：本地运行前端

**方式A：直接用浏览器打开（最简单，适合本地测试）**
```
双击 index.html 即可在浏览器中打开
```

**方式B：用VS Code Live Server（推荐开发时使用）**
```
VS Code 安装 Live Server 插件 → 右键 index.html → Open with Live Server
```

**方式C：用Python简易服务器**
```bash
cd C:\闽师小羽学长
python -m http.server 3000
# 访问 http://localhost:3000
```

### 第五步：手机端测试

同一WiFi下：
```bash
# 1. 查看电脑IP
ipconfig    # Windows，找到IPv4地址，如 192.168.1.100

# 2. 修改index.html中后端地址
const API_BASE = 'http://192.168.1.100:8080';

# 3. 手机浏览器访问
http://192.168.1.100:3000
```

---

## 🔐 安全架构说明

### 为什么API Key不会泄露？

```
┌─────────────────────────────────────────────────────┐
│  用户浏览器（前端）                                    │
│  ┌─────────────────────────────────────────────┐    │
│  │ index.html                                   │    │
│  │ ✅ 可见：API_BASE = 'http://后端:8080'        │    │
│  │ ✅ 可见：AUTH_TOKEN = 'mnnu-xiaoyu-2025'     │    │
│  │ ❌ 不可见：DeepSeek API Key                  │    │
│  │                                              │    │
│  │ 用户点击"发送" → POST /api/chat              │    │
│  │ 携带 Header: X-Auth-Token                    │    │
│  │ Body: {"question": "宿舍有空调吗？"}          │    │
│  └──────────────────┬──────────────────────────┘    │
└─────────────────────┼───────────────────────────────┘
                      │  HTTPS（生产环境）
                      ▼
┌─────────────────────────────────────────────────────┐
│  后端服务器（SpringBoot）                              │
│  ┌─────────────────────────────────────────────┐    │
│  │ ① AuthInterceptor 校验 X-Auth-Token         │    │
│  │    └─ Token不匹配 → 返回401，拒绝请求        │    │
│  │    └─ Token匹配   → 放行到Controller         │    │
│  │                                              │    │
│  │ ② ChatController 接收问题                     │    │
│  │    └─ 调用 ChatService.chatStream()          │    │
│  │                                              │    │
│  │ ③ ChatService 构建请求                        │    │
│  │    └─ 从 application.yml 读取 API Key        │  ← 密钥在这里！
│  │    └─ Header: Authorization: Bearer sk-xxx   │    │
│  │    └─ 发送到 https://api.deepseek.com        │    │
│  │                                              │    │
│  │ ④ 流式读取 DeepSeek 返回 → SSE 推送给前端     │    │
│  └─────────────────────────────────────────────┘    │
│                                                       │
│  application.yml（仅在服务器磁盘上）                    │
│  deepseek.api-key: sk-xxxxxxxxxxxx  ← 前端永远看不到  │
└─────────────────────────────────────────────────────┘
```

### 四层安全防护

| 层级 | 机制 | 作用 |
|------|------|------|
| **1. 密钥隔离** | API Key 只在 `application.yml` 中，前端代码不包含 | 即使前端源码完全泄露，攻击者也拿不到DeepSeek密钥 |
| **2. Token鉴权** | `AuthInterceptor` 校验 `X-Auth-Token` | 不知道Token的人无法调用 `/api/chat` |
| **3. 后端代理** | 前端 → 后端 → DeepSeek（非前端 → DeepSeek） | 前端只知道自己后端的URL，不知道DeepSeek的存在 |
| **4. HTTPS** | 生产环境使用HTTPS | 防止中间人监听，Token和对话内容加密传输 |

### 安全升级建议（生产环境）

| 建议 | 说明 |
|------|------|
| 🔑 **改用JWT** | 将固定Token替换为JWT，带过期时间，支持刷新 |
| 🛡️ **加IP限流** | 使用Spring Cloud Gateway或Nginx对/api/chat限流（如每IP每秒1次） |
| 📝 **敏感词过滤** | 前端+后端双重过滤违规提问 |
| 🔒 **HTTPS强制** | Nginx配置SSL证书（Let's Encrypt免费） |
| 📊 **日志脱敏** | 确保日志中不记录完整API Key（当前已在AuthInterceptor中截断） |
| 🚪 **网关隔离** | 将后端放在Nginx后面，只暴露/api/chat，隐藏其他端口 |

---

## 🔧 常见问题

### Q: 启动后端报 "端口8080被占用"
```bash
# 修改 application.yml
server:
  port: 8088    # 改成其他端口
```
然后同步修改 `index.html` 中 `API_BASE` 的端口。

### Q: 前端点击发送后提示 "网络请求失败"
1. 确认后端已启动（访问 `http://localhost:8080/api/health`）
2. 确认 `index.html` 中 `API_BASE` 地址正确
3. 如果手机访问，确认手机和电脑在同一WiFi，且防火墙允许8080端口

### Q: DeepSeek返回错误
1. 确认API Key有效且余额充足
2. 检查 `application.yml` 中 `base-url` 是否为 `https://api.deepseek.com/v1`
3. 查看后端控制台日志排查

### Q: 怎么换其他大模型（豆包、通义千问等）？
只需修改 `application.yml` 三个参数：
```yaml
deepseek:
  api-key: 你的新模型密钥
  base-url: https://新模型的兼容地址/v1     # OpenAI兼容格式
  model: 新模型名称
```

---

## 📁 打包部署（生产环境）

```bash
# 打包成可执行JAR
cd backend
mvn clean package -DskipTests

# 运行
java -jar target/mnnu-assistant-1.0.0.jar

# 后台运行（Linux）
nohup java -jar target/mnnu-assistant-1.0.0.jar > app.log 2>&1 &

# 前端部署到 Nginx
# 将 index.html 复制到 /usr/share/nginx/html/
# 配置 Nginx 反向代理 /api/chat → localhost:8080
```
