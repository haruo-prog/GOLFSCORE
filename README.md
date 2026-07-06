# GOLFSCORE

Android向けのゴルフスコア管理アプリです。

## Store Concept

**The fastest golf score app in the world. Works offline. No ads. No subscription. Just $1.**

## Version

- App version: 1.11.0
- V2.3 restore backup feature
- versionCode: 23
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## V2.3変更点

- 設定画面にバックアップ保存を追加
- 設定画面にバックアップから復元を追加
- Google Driveなどの保存先へバックアップファイルを作成可能
- Google Driveなどからバックアップファイルを選択して復元可能
- 復元後はホーム画面へ戻り、保存済み履歴・設定・クラブセットを再読込
- 多言語対応は継続
- 全世界コース選択は削除済み

## Product Positioning

- Works offline
- No ads
- No subscription
- No account required
- Fast launch
- Fewer taps during the round
- Buy once, lifetime access

## 主な機能

- ホーム / 履歴 / 分析 / 設定 の4画面構成
- 初回起動時の言語選択
- 最下部の言語選択
- 日本語 / English / 한국어 / 中文 / Deutsch
- ラウンド中はスコア、PAT、使用クラブ、ティーショット結果を入力
- Player1のみ詳細分析
- Player2からPlayer4はスコアのみ
- クラブセット登録
- Player1のティーショットに使用クラブを記録
- クラブ別FW率、OB数を分析
- ラウンド後に自動分析コメントを生成
- スコアカードPDF出力
- バックアップ保存 / バックアップから復元
- NKTSロゴをアプリアイコンに設定

## ビルド方法

1. Android Studioでこのリポジトリを開く
2. Gradle Syncを実行
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)` を実行

## GitHub Actions

`.github/workflows/build-apk.yml` により、pushまたは手動実行でdebug APKを生成します。
