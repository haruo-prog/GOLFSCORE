# GOLFSCORE

Android向けのゴルフスコア管理アプリです。

## Store Concept

**The fastest golf score app in the world. Works offline. No ads. No subscription. Just $1.**

## Version

- App version: 1.14.0
- V2.6 language expansion
- versionCode: 26
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## V2.6変更点

- 言語を8言語に拡張
- スペイン語を追加
- フランス語を追加
- 繁体字中国語を追加
- 既存の中国語表示を簡体字として整理
- 最下部の言語ボタンを `JP / EN / ES / FR / KO / 简 / 繁 / DE` に変更
- 言語ボタンは固定幅・固定高・折り返しなしを維持
- 入力画面の保存遅延・軽量更新は継続
- バックアップ保存 / バックアップから復元は継続

## 対応言語

- 日本語
- English
- Español
- Français
- 한국어
- 简体中文
- 繁體中文
- Deutsch

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
- 8言語対応
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
