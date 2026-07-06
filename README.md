# Golf Scorecard Offline

Android向けのオフライン対応ゴルフスコアカードアプリです。

## Store Concept

**Golf Scorecard Offline**

**No ads. No login. Works offline. Simple golf scorekeeping.**

## Search Positioning

検索ヒットを狙う主軸は以下です。

- golf scorecard
- golf score
- golf score app
- golf score tracker
- offline golf scorecard
- simple golf scorecard
- ゴルフ スコア
- ゴルフ スコアカード
- ゴルフ スコア 管理

## Version

- App version: 1.15.0
- V2.7 store positioning and app label update
- versionCode: 27
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## V2.7変更点

- アプリ表示名を **Golf Scorecard Offline** に変更
- Google Play向けの検索戦略をREADMEに反映
- ストアコンセプトを `No ads / No login / Works offline / Simple golf scorekeeping` に統一
- 8言語対応は継続
- 入力画面の保存遅延・軽量更新は継続
- バックアップ保存 / バックアップから復元は継続

## Store Copy Draft

### Short Description EN

No ads. No login. Works offline. A simple golf scorecard for fast scorekeeping.

### Short Description JP

広告なし・ログイン不要・オフライン対応。シンプルに使えるゴルフスコアカード。

### Main Message

Too many golf apps are complicated. This one is simple.

多機能すぎるゴルフアプリが苦手な人へ。スコア管理だけ、すぐ使える。

## Screenshot Copy

1. Simple Golf Scorecard
2. Works Offline Anywhere
3. No Ads. No Login.
4. Fast Score Input
5. One-Time Purchase

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
