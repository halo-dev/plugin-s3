# plugin-alioss

为 Halo 2.0 提供阿里云 OSS 的存储策略

## 开发环境

```bash
git clone git@github.com:halo-sigs/plugin-alioss.git
```

```bash
./gradlew build
```

修改 Halo 的配置文件

```yaml
plugin:
  runtime-mode: development # development, deployment
  classes-directories:
    - "build/classes"
    - "build/resources"
  lib-directories:
    - "libs"
  fixedPluginPath:
    - "path/to/plugin-alioss"
```

启动 Halo 之后即可在后台插件管理看到此插件。

## 生产构建

```yaml
./gradlew build
```

构建完成之后，可以在 `build/libs` 目录得到插件的 JAR 包，在 Halo 后台的插件管理上传即可。

## 使用方法

> 目前设置了 GitHub Action 的 Push 构建，你可以在 https://github.com/halo-sigs/plugin-alioss/actions 的每个构建详情中下载最新构建的 JAR 文件。然后在 Halo 后台的插件管理上传即可。

- 上传并启用插件。
- 进入后台附件管理。
- 点击右上角的存储策略，在存储策略弹框的右上角可新建阿里云 OSS 存储策略。
- 创建完成之后即可在上传的时候选择新创建的阿里云 OSS 存储策略。
