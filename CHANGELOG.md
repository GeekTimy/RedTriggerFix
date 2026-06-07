# Changelog

## 0.2.1 - 2026-06-07

- 增加正式 release 签名流程，GitHub Actions 使用 release keystore 构建可稳定升级的 APK。
- 签名很重要：Android 用签名证书识别应用身份，后续版本必须继续使用同一个 key 才能覆盖升级。
