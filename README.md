# isa Workspace V1 1.0

isa Workspace 是一个 Windows 桌面工作台，用于在移动 SSD 上组织工作区和本地文件、目录的关联。界面入口为 `WorkspaceFx`，工作区及资源索引存储在 SQLite 中。V1 的设计见 [移动工作区架构](doc/V1_Goal.txt)。

## 构建依赖（Windows x64）

| 依赖 | V1 使用的版本 | 获取方式 |
| --- | --- | --- |
| JDK | Oracle JDK 21.0.13.0.3（Java 21） | 在 [Oracle Java 下载页](https://www.oracle.com/java/technologies/downloads/#java21) 获取 JDK 21 的 Windows x64 压缩包，解压后使用含 `bin\javac.exe` 的目录。 |
| JavaFX SDK | 21.0.13 | 在 [Gluon JavaFX 下载页](https://gluonhq.com/products/javafx/#downloads) 选择 21.0.13、Windows、x64、SDK，解压后使用含 `lib\javafx.controls.jar` 的目录。 |
| Xerial SQLite JDBC | 3.54.1.0 | 从 [Xerial 发布页](https://github.com/xerial/sqlite-jdbc/releases/tag/3.54.1.0) 下载 `sqlite-jdbc-3.54.1.0.jar`（也可从 Maven Central 获取 `org.xerial:sqlite-jdbc:3.54.1.0`），放入源码根目录下的 `lib`。 |

已有便携包时，也可以直接使用它的 `jdk` 和 `javafx` 目录，并把 `lib\sqlite-jdbc-3.54.1.0.jar` 复制到源码目录对应位置。依赖和构建产物不随 Git 提交。

## 从源码构建和启动

在源码根目录打开 PowerShell，将以下两个示例路径换成实际路径。脚本把应用 JAR 写入 `build\isa-workspace.jar`。

```powershell
$jdk = 'X:\path\to\jdk'
$javafx = 'X:\path\to\javafx-sdk-21.0.13'
.\build.ps1 -JdkHome $jdk -JavaFxHome $javafx

& "$jdk\bin\java.exe" --module-path "$javafx\lib" --add-modules javafx.controls -cp ".\build\isa-workspace.jar;.\lib\sqlite-jdbc-3.54.1.0.jar" WorkspaceFx
```

应用从 JAR 所在目录读取 `workspace.db`，所以此命令使用 `build\workspace.db`。首次启动时可点击「新建工作区」初始化数据库；如需用现有数据验证，可将已关闭应用的数据库副本放在 JAR 旁边。

## 源码与便携目录

- 移动盘上的 `Java\Project\isa Workspace` 是源码目录，含 `src`、`doc` 和构建脚本。
- 移动盘根目录下的 `isa-workspace-portable` 是独立的部署目录，包含 `jdk`、`javafx`、`lib`、`isa-workspace.jar`、`启动工作台.cmd` 和运行数据 `workspace.db`。可在该目录用以下命令直接启动，无需配置系统 Java：

```powershell
& '.\jdk\bin\java.exe' --module-path '.\javafx\lib' --add-modules javafx.controls -cp '.\isa-workspace.jar;.\lib\sqlite-jdbc-3.54.1.0.jar' WorkspaceFx
```

更新便携包时，先关闭工作台，备份部署目录的 `workspace.db`，再用构建生成的 JAR 替换部署目录的同名 JAR。数据库保留在部署目录。
