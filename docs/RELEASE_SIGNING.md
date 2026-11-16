# 发布与签名（GitHub Actions）

> 目标：以后每次打 `v*` tag，GitHub Actions 用仓库 Secrets 里的 release keystore 签名，并自动创建 GitHub Release。

## 1. 一次性配置

release keystore 已生成在仓库外：

```
C:\Users\Administrator\Documents\Default Project\shigu-signing\shigu-release.jks
C:\Users\Administrator\Documents\Default Project\shigu-signing\credentials.txt
```

`credentials.txt` 里有 storePassword / keyPassword / keyAlias。**不要提交到 Git。**

在 GitHub 仓库 Settings → Secrets and variables → Actions 添加：

| Secret | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `shigu-release.jks` 的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | storePassword |
| `ANDROID_KEY_ALIAS` | `shigu` |
| `ANDROID_KEY_PASSWORD` | keyPassword |

`gh` 命令（仓库已创建后）：

```powershell
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Users\Administrator\Documents\Default Project\shigu-signing\shigu-release.jks"))
gh secret set ANDROID_KEYSTORE_BASE64 --repo <owner>/<repo> --body $b64
gh secret set ANDROID_KEYSTORE_PASSWORD --repo <owner>/<repo> --body "<storePassword>"
gh secret set ANDROID_KEY_ALIAS --repo <owner>/<repo> --body "shigu"
gh secret set ANDROID_KEY_PASSWORD --repo <owner>/<repo> --body "<keyPassword>"
```

> 备份 `shigu-release.jks` 和密码。keystore 丢了就无法给同一包名签更新版本；只能换包名，旧用户无法覆盖升级。

## 2. 发布流程

```bash
git tag v0.1.0
git push origin v0.1.0
```

触发 `.github/workflows/release.yml`：

1. JDK 17 + Android SDK 37.0 / build-tools 36
2. 从 `ANDROID_KEYSTORE_BASE64` 解码 keystore
3. `./gradlew :app:testDebugUnitTest :app:assembleRelease`
4. `apksigner verify --print-certs` 验证签名
5. 生成 `app-release.apk.sha256`
6. 创建 GitHub Release，附 APK + SHA256

版本号：

- `versionName` = tag 去掉 `v`（如 `v0.1.0` → `0.1.0`）
- `versionCode` = GitHub Actions 的 run number（递增）

## 3. 本地构建

不设置环境变量时，`assembleRelease` 会用 debug keystore 签名，仅用于本地自测，**不能用于发布**。

要本地验证正式签名：

```powershell
$env:KEYSTORE_FILE="C:\Users\Administrator\Documents\Default Project\shigu-signing\shigu-release.jks"
$env:KEYSTORE_PASSWORD="<storePassword>"
$env:KEY_ALIAS="shigu"
$env:KEY_PASSWORD="<keyPassword>"
$env:GITHUB_REF_NAME="v0.1.0"
$env:GITHUB_RUN_NUMBER="1"
.\gradlew.bat :app:assembleRelease
& "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

## 4. 注意

- 不要提交 `*.jks` / `*.keystore`（仓库 `.gitignore` 已排除，`app/debug.keystore` 是公开的 debug key，例外保留）。
- 不要提交 `local.properties`、`build/`、APK。
- Release workflow 只由 tag 触发；普通 push 走 CI（测试 + debug APK）。
- 如果 Secrets 缺失，workflow 会在解码 keystore 步骤直接失败，不会产出未签名/错签的 release。
