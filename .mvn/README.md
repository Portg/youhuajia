# Maven 配置说明

## 阿里云仓库校验和告警

若构建时出现：

```text
[WARNING] Could not validate integrity of download from https://maven.aliyun.com/repository/public/.../maven-metadata.xml
Checksum validation failed, expected ... but is ...
```

说明阿里云镜像的元数据校验和与预期不一致（多为镜像同步或缓存导致）。处理方式：

1. **先清理本地缓存**（已对 `commons-math3` 做过一次）：
   ```bash
   rm -f ~/.m2/repository/org/apache/commons/commons-math3/maven-metadata-aliyun.xml*
   ```
   然后重新执行 `mvn compile -DskipTests` 或相应命令。

2. **若告警仍出现**，可在本机 `~/.m2/settings.xml` 里为阿里云仓库关闭校验和校验。在对应 `<repository>` 的 `<releases>` / `<snapshots>` 中增加：
   ```xml
   <releases>
     <checksumPolicy>ignore</checksumPolicy>
   </releases>
   <snapshots>
     <checksumPolicy>ignore</checksumPolicy>
   </snapshots>
   ```
   保存后重新构建即可消除该告警。
