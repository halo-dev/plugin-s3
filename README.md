# plugin-s3

为 Halo 2.0 提供 S3 协议的对象存储策略，支持阿里云、腾讯云、七牛云等兼容 S3 协议的对象存储服务商

## 开发环境

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
    - "path/to/plugin-s3"
```

启动 Halo 之后即可在后台插件管理看到此插件。

## 生产构建

```yaml
./gradlew build
```

构建完成之后，可以在 `build/libs` 目录得到插件的 JAR 包，在 Halo 后台的插件管理上传即可。

## 使用方法

1. 在 [Releases](https://github.com/halo-sigs/plugin-s3/releases) 下载最新的 JAR 文件。
2. 在 Halo 后台的插件管理上传 JAR 文件进行安装。
3. 进入后台附件管理。
4. 点击右上角的存储策略，在存储策略弹框的右上角可新建 S3 Object Storage 存储策略。 
5. 创建完成之后即可在上传的时候选择新创建的 S3 Object Storage 存储策略。
