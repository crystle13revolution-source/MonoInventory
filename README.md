# 物集 · MonoInventory Android MVP

Android 原生 Java + SQLite 项目。当前为**可导入 Android Studio 的源码工程**，非已编译 APK。

## 功能（v0.1）

- 物品新增、编辑、搜索、软删除
- 拍照或从相册选取；图片等比例采样并压缩成 JPEG（质量 82），保存在应用私有目录
- 分类（日用品、衣服、家具、爱好、生产力装备等；可自定义文字）
- 自由填写存放地点、状态、备注
- 单件物品 / 可计数库存两种数量管理模式（hybrid）
- 到期日选择；首页统计过期及未来 7 天内到期的记录
- SQLite 离线保存；无服务器、无账户、无网络权限
- 数据库内预留 groups、members、change_log、version、deleted_at，供后续共享同步

## 导入与运行

1. 安装 Android Studio，安装 Android SDK Platform 35、Build Tools 和 JDK 17。
2. 用 Android Studio 的 **Open** 打开本项目目录。
3. 本归档未附带 Gradle Wrapper JAR（当前构建环境没有 Gradle/Android SDK，无法联网下载），因此需要本机 Gradle 8.9 或通过 Android Studio 生成 Gradle Wrapper；项目使用 Android Gradle Plugin 8.7.3。
4. Gradle 同步后运行 `app`，选择 Android 10（API 29）或更新的设备/模拟器。
5. 若 Android Studio 缺少对应 SDK，按其提示安装。

命令行如已有 Gradle 8.9：`gradle :app:assembleDebug`，APK 输出位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 架构

- `MainActivity`: 首页清单、搜索、保质期汇总
- `EditItemActivity`: 录入/编辑、日期选择、相机/相册
- `PhotoStore`: 图片解码、EXIF 方向处理、压缩、内部保存
- `InventoryDb`: SQLite 数据与变更日志
- `Item`: 本地数据实体

## 当前边界

- **多人共享尚未可用**：仅单设备 local-default 空间，数据库为将来同步做准备；没有伪装成实时同步。
- **AI 辅助识别尚未接入**：未来可以基于用户确认的识别候选填写表单。
- 到期提示仅在打开 App 时展示；未接通知/后台任务。
- 暂不支持多照片、条码、批量导入、库存流水、可视化位置树或 Windows 端。
- 未经本环境编译与真机测试。上线前需补充自动化测试、同步冲突处理、数据导出备份和隐私控制。
- 数据处于 Android 应用私有存储；卸载 App 可能导致数据丢失，请勿录入不可恢复的重要资料，待备份功能完成后再作为正式资产档案使用。

## 下一阶段建议

v0.2：照片多图 + 分类/位置管理 + SQLite JSON 导出备份 + 提醒通知。  
v0.3：局域网同步服务 + 成员权限 + 冲突解决。  
v0.4：AI 识别建议 + Windows 客户端。
