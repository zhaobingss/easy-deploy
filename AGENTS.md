# Easy Deploy 协作指南

## 交流与实现原则

- 所有回复、计划和交付说明必须使用中文。
- 编码前先说明假设和成功标准；需求存在歧义且会影响实现时，先询问用户。
- 只修改完成当前任务所必需的文件和代码，不顺手重构、改格式或清理既有代码。
- 优先复用仓库现有实现、JDK/IntelliJ Platform API 和已安装依赖，不为单次需求新增抽象或依赖。
- 新增或修改的函数必须写详细的 Javadoc/KDoc，说明函数用途、每个参数的含义和返回值；无参数或无返回值时也要明确其行为。复杂或容易误解的分支应补充原因说明。
- 开始修改前检查 `git status`；保留用户已有的未提交改动，不覆盖或回退无关内容。

## 项目概览

- 本项目是 IntelliJ Platform 插件，用于 SSH/SFTP 服务器管理、文件上传、命令执行和部署。
- 构建系统为 Gradle Wrapper 8.1，构建脚本使用 Groovy DSL。
- 主要代码是 Java，少量 Kotlin 文件也位于 `src/main/java`；JVM Toolchain 为 Java 17。
- 目标 IDE 为 IntelliJ Platform 2024.1（241），插件 ID 为 `tech.lin2j.simple-deployment`。
- `build.gradle` 是依赖、插件版本和目标 IDE 配置的事实来源；`src/main/resources/META-INF/plugin.xml` 是扩展点、服务和依赖声明的事实来源。

## 目录职责

- `src/main/java/tech/lin2j/idea/plugin/action`：IDE 与界面操作入口。
- `src/main/java/tech/lin2j/idea/plugin/model`：配置、持久化和领域模型。
- `src/main/java/tech/lin2j/idea/plugin/service`：服务接口及实现。
- `src/main/java/tech/lin2j/idea/plugin/ssh`：SSH/SFTP 连接、进程和上传逻辑。
- `src/main/java/tech/lin2j/idea/plugin/runner`：Run/Debug Configuration 集成。
- `src/main/java/tech/lin2j/idea/plugin/file`：本地/远程文件模型、类型和过滤器。
- `src/main/java/tech/lin2j/idea/plugin/ui`：Swing 界面、对话框、表格和编辑器。
- `src/main/java/tech/lin2j/idea/plugin/uitl`：现有通用工具包；包名虽为 `uitl`，除非任务明确要求，否则不要顺带重命名。
- `src/main/resources`：插件描述符、国际化文本、图标和运行时资源。
- `src/test/java`：自动化测试。
- `build/`、`.gradle/`、`.idea/`：生成内容或本地状态，不手工修改或提交。

## 常用命令

统一使用仓库自带的 Wrapper：

```bash
./gradlew test
./gradlew build
./gradlew runIde
./gradlew buildPlugin
./gradlew verifyPlugin
```

- 单个测试可使用 `./gradlew test --tests '完整类名'`。
- `runIde` 用于必须在真实 IntelliJ 沙箱中验证的界面或平台集成改动。
- `buildPlugin` 的产物位于 `build/distributions/`。
- 未经用户明确要求，不执行 `publishPlugin`、签名或发布操作。

## 修改约束

- 修复缺陷前搜索目标方法的所有调用者，优先在共享根因处做一次最小修复。
- 修改插件扩展、服务或 IDE 依赖时，同步检查 `plugin.xml`；Java 专属的可选集成还需检查 `plugin-java.xml`。
- 新增或修改用户可见文本时，通过 `MessagesBundle` 和资源文件处理，并同步维护 `messages_en.properties` 与 `messages_zh.properties`。
- SSH 认证信息、私钥口令和密码属于敏感数据，不得写入日志、测试数据或版本库。
- 保持与 241 平台和 Java 17 兼容；不要使用更高版本 JDK 才提供的 API。

## 验证与交付

- 缺陷修复应尽量添加能复现问题的最小回归测试；非平凡分支或解析逻辑至少保留一个可运行检查。
- 纯逻辑改动至少运行相关测试；构建、依赖或插件描述符改动运行 `./gradlew build`，必要时再运行 `./gradlew verifyPlugin`。
- UI 或 IntelliJ 生命周期相关行为无法由单元测试覆盖时，使用 `./gradlew runIde` 手工验证并说明验证范围。
- 交付前运行 `git diff --check` 和 `git status --short`，确认没有无关改动、生成文件或凭据。
