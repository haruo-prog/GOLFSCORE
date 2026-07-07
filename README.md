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

- App version: 1.16.0
- V2.8 home CSV export and senior-friendly large number UI
- versionCode: 28
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## V2.8変更点

- ホーム画面にCSVエクスポートを追加
- 開始日・終了日を指定して履歴CSVを保存可能
- Android標準の保存先選択に対応
- Google Drive / Downloads / 任意フォルダにCSV保存可能
- CSVはExcelで開きやすいUTF-8 BOM付き
- CSV項目に日付、コース、ティー、合計、PAT、FW、OB、H1〜H18、Par1〜Par18を出力
- 50代・60代でも見やすいように数字UIを大型化
- スコア入力テンキー、現在スコア、ホール番号、PAR、PATの文字サイズを拡大
- 8言語対応は継続
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
4. Large, Easy-to-Read Numbers
5. Export Scores to CSV

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
- Large numbers for easy input
- CSV export for score history
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
- 日付範囲指定CSVエクスポート
- バックアップ保存 / バックアップから復元
- NKTSロゴをアプリアイコンに設定

## ビルド方法

1. Android Studioでこのリポジトリを開く
2. Gradle Syncを実行
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)` を実行

## GitHub Actions

`.github/workflows/build-apk.yml` により、pushまたは手動実行でdebug APKを生成します。
