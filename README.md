# MoodEcho

轻量记录、私密保存、温和回顾、匿名共鸣的日记 Web 应用。

- Vue 3 + Vite + Element Plus + Pinia + Vue Router
- Spring Boot + MyBatis-Plus + MySQL 8 + JWT
- OpenAI-compatible Embeddings API，默认连接阿里云百炼 DashScope

## 当前功能

邮箱注册与验证码、登录与密码管理、账号注销；日记草稿、发布、编辑、删除、按日期浏览；图片上传与权限校验；个人统计；匿名片段投递与撤回；文字搜索、分块语义搜索和相关日记。

智能搜索使用文字与片段向量融合。新建、编辑、发布日记后在后台生成索引，失败自动重试。搜索按用户隔离，只检索正式且未删除的日记；页面仍按日期分组。当前没有候选重排或大模型事实核验。

## 目录

```text
MoodEcho/
├── backend/
│   ├── src/main/java/         # 接口、业务、后台任务及存储
│   ├── src/main/resources/    # 配置、初始化 SQL 和增量迁移
│   ├── src/test/              # 检索、模型客户端、任务和数据库测试
│   ├── .mvn/、mvnw、mvnw.cmd  # Maven Wrapper
│   ├── pom.xml
│   └── uploads/               # 本地上传数据，不纳入版本管理
├── frontend/                 # 页面、组件、前端配置和依赖锁文件
├── docs/
│   ├── semantic-search.md    # 搜索实现、接口和维护
│   └── privacy.md            # 隐私与实际数据处理说明
└── README.md                 # 安装、运行和验证入口
```

`target/`、`dist/`、`node_modules/` 是构建或依赖目录，可由命令生成，不作为源码提交。

## 环境

JDK 17 或以上、Maven（或使用 `backend/mvnw`）、MySQL 8.x；Node.js 20.19+ 或 22.12+、npm。首次安装依赖需要网络。

## 数据库

全新安装，在项目根目录执行以下命令。脚本创建并选择 `moodecho_app` 数据库；只用于新数据库，不用于升级已有数据。

```sh
mysql -u root -p < backend/src/main/resources/sql/resonance.sql
```

已有安装在启动后端前执行任务表增量迁移，不要重跑初始化脚本：

```sh
mysql -u root -p moodecho_app < backend/src/main/resources/sql/migrations/20261008_embedding_jobs.sql
```

迁移可重复执行，不改写已有日记或向量。

## 后端配置与启动

在启动后端的终端设置以下环境变量，替换示例值：

```sh
export DB_PASSWORD='你的数据库密码'
export JWT_SECRET='替换为至少32字节的随机私密字符串'
export MAIL_USERNAME='你的QQ邮箱地址'
export MAIL_PASSWORD='你的QQ邮箱SMTP授权码'
# 可选：开启外部语义处理前，请阅读 docs/privacy.md。
export DASHSCOPE_API_KEY='你的百炼API Key'

cd backend
./mvnw spring-boot:run
```

也可以使用已安装的 `mvn spring-boot:run`；Windows 使用 `mvnw.cmd`。后端默认运行在 `http://localhost:8080`，数据库默认为本机 `moodecho_app`、用户 `root`。通过 `SPRING_DATASOURCE_URL` 和 `SPRING_DATASOURCE_USERNAME` 可覆盖数据库地址和用户。

QQ 邮箱需要启用 SMTP。请使用自己的授权码和 JWT 密钥，不要把真实配置写入源码。图片默认保存在后端工作目录下的 `uploads/diary-images`，迁移部署时需要一并保存数据库和图片目录。

语义模型默认是 `text-embedding-v4`。兼容服务可通过 `DASHSCOPE_BASE_URL`、`DASHSCOPE_EMBEDDING_MODEL` 覆盖地址与模型；换模型后需要重建索引。

未配置 `DASHSCOPE_API_KEY` 时，普通日记功能仍可用。语义搜索有文字命中时返回文字结果，没有文字命中时返回服务错误。部署者可设置 `MOODECHO_EMBEDDING_ENABLED=false` 完全关闭外部向量调用；后台任务保留，重新启用后继续处理。当前没有个人级 AI 开关。

## 前端启动

另开终端，在项目根目录执行：

```sh
cd frontend
npm ci
npm run dev
```

访问 Vite 输出的地址。开发服务器将 `/api` 代理到本机 8080 端口。

## 验证与构建

```sh
cd backend
./mvnw test
./mvnw package -DskipTests
```

默认测试不调用外部模型、不访问业务数据库。MySQL 集成测试默认跳过；设置独立测试库的 `SEMANTIC_TEST_JDBC_URL`、`SEMANTIC_TEST_DB_USER`、`SEMANTIC_TEST_DB_PASSWORD` 后可用 `./mvnw -Dtest=EmbeddingJobSqlIntegrationTest test` 执行，测试只创建和删除自己的随机命名表。

```sh
cd frontend
npm run build
```

前端输出为 `frontend/dist`，后端可执行包为 `backend/target/MoodEcho-0.0.1-SNAPSHOT.jar`。部署时需提供数据库、SMTP 和 JWT 配置；静态服务器需将 `/api` 转发至后端，并为 Vue Router 配置回退到 `index.html`。

## 维护说明

- [语义搜索](docs/semantic-search.md)：分块、任务重试、索引重建和当前限制。
- [隐私与数据处理](docs/privacy.md)：外部模型发送内容、处理时机、删除边界。
