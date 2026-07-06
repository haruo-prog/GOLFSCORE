# GOLFSCORE

Android向けのゴルフスコア管理アプリです。

## Store Concept

**The fastest golf score app in the world. Works offline. No ads. No subscription. Just $1.**

## Version

- App version: 1.10.0
- V2.2 language selection and offline one-dollar concept
- versionCode: 22
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## V2.2変更点

- 全世界コース選択の表示を削除
- 初回起動時に言語選択画面を表示
- 最下部に言語選択を追加
- 対応言語
  - 日本語
  - English
  - 한국어
  - 中文
  - Deutsch
- ホーム画面に世界向けコンセプトを追加
- オフライン対応、広告なし、サブスクなし、アカウント不要、少ないタップを訴求
- スコア入力はテンキー式を継続
- 1+を押した後に0から5を押すと10から15を入力
- キャンセル終了後に登録画面へ戻れる導線を継続

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
- ラウンド中はスコア、PAT、使用クラブ、ティーショット結果を入力
- Player1のみ詳細分析
- Player2からPlayer4はスコアのみ
- クラブセット登録
- Player1のティーショットに使用クラブを記録
- クラブ別FW率、OB数を分析
- ラウンド後に自動分析コメントを生成
- スコアカードPDF出力
- NKTSロゴをアプリアイコンに設定

## ビルド方法

1. Android Studioでこのリポジトリを開く
2. Gradle Syncを実行
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)` を実行

## GitHub Actions

`.github/workflows/build-apk.yml` により、pushまたは手動実行でdebug APKを生成します。
