# GOLFSCORE

Android向けのゴルフスコア管理アプリです。

## Store Concept

**The fastest golf score app in the world. Works offline. No ads. No subscription. Just $1.**

## Version

- App version: 1.12.0
- V2.4 smooth input screen update
- versionCode: 24
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## V2.4変更点

- 入力画面のカクつきを改善
- スコア入力時に画面全体を再生成しない方式へ変更
- スコア表示、進捗、PAT、Tee結果のみを部分更新
- スクロール位置の強制復元を減らし、入力中の引っかかりを軽減
- ホール移動、人数変更、PAR変更など必要な場面のみ画面再生成
- バックアップ保存 / バックアップから復元は継続
- 多言語対応は継続

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
