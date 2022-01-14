# scroll-linear-layout
the linearlayout which support scroll on current layout orientation

##  版本号说明

- local.properties 文件中的 PROJECT_VERSION 用于本地开发调试，优先级最高
- ./gradle/publish-ext.gradle 文件中的 PROJECT_VERSION 用于远程发布，优先级较低，当local.properties 文件存在 PROJECT_VERSION 值时将覆盖 publish-ext.gradle 文件中的值

**设置要求**

- local.properties 中的版本号推荐设置成 SNAPSHOT 版本
- publish-ext.gradle 中的版本号请务必设置成正式版，即不带SNAPSHOT后缀的，因为bintray不支持快照版本
- 理论上 local.properties 中的版本号可以自由设置，由开发者本人保证在本机上的唯一性即可。但是推荐设置成与publish-ext.gradle中的版本号相关联的。如publish-ext.gradle中为1.0.1，则在开发阶段，local.properties中推荐设置成 1.0.1.x-SNAPSHOT，x为0~99999

## 本地发布

```bash
./gradlew clean

./gradlew aR pMPTML -x :app:aR
```


## 远程发布

```bash
./gradlew clean

./gradlew aR pMPTMR -x :app:aR
```

