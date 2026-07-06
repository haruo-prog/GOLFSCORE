# GOLFSCORE

Android向けのゴルフスコア管理アプリです。

## Store Concept

**The fastest golf score app in the world. Works offline. No ads. No subscription. Just $1.**

## Version

- App version: 1.13.0
- V2.5 aligned language buttons and smoother input
- versionCode: 25
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## V2.5変更点

- 言語選択ボタンを一直線に綺麗に整列
- 最下部の言語ボタンを JP / EN / KO / 中文 / DE の固定幅・固定高に変更
- 言語ボタンの折り返しを禁止
- 入力時の保存処理を遅延・集約
- スコア入力のたびに全データを即保存しないように変更
- スコア入力時は現在ホールだけ更新
- 18ホール分のボタン再更新を停止
- 画面離脱、ホール移動、バックアップ時は確実に保存
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
