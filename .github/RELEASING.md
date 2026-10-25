# Android 发布

GitHub Actions 只在推送 `v` 开头的版本标签时构建正式版本，例如：

```bash
git tag v1.1.0
git push origin v1.1.0
```

标签触发后，Actions 会构建签名 release APK、生成 SHA-256 校验文件，并创建 GitHub Release。Release 正文由上一个版本标签到本次标签之间的提交生成，按提交类型（`feat`、`fix`、`ui`……）分节，所以提交信息请保持 `type(scope): summary` 的形式。首次发布前，需要在仓库的 `Settings > Secrets and variables > Actions` 中配置：

- `ANDROID_KEYSTORE_BASE64`：release keystore 文件的 Base64 内容
- `ANDROID_STORE_PASSWORD`：keystore 密码
- `ANDROID_KEY_ALIAS`：签名 key alias
- `ANDROID_KEY_PASSWORD`：签名 key 密码

Windows PowerShell 可用以下命令生成 keystore 的 Base64 内容：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("sextant-release.jks"))
```

keystore 和密码只能保存在安全位置或 GitHub Secrets 中，不要提交到仓库。
