# GOLFSCORE

Android向けのゴルフスコア管理アプリです。

## Version

- App version: 1.4.0
- versionCode: 14
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## 主な機能

- 1ホールずつ表示して入力
- 最大4名まで同時入力
- スコアは1〜15までスクロール入力
- 未入力は `-` 表示
- 各プレイヤーごとに SCORE / Putt / FW / OB / Penalty / メモを管理
- ホールごとにPARを設定可能
- OUT / IN / TOTAL の自動集計
- プレイヤー別のフェアウェイキープ率、平均パット、スコア差分の自動計算
- 1.2秒間隔の自動保存
- バックグラウンド移行時、画面回転時、アプリ終了時にも保存
- テキスト出力とクリップボードコピー

## ビルド方法

1. Android Studioでこのリポジトリを開く
2. Gradle Syncを実行
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)` を実行

## GitHub Actions

`.github/workflows/build-apk.yml` により、pushまたは手動実行でdebug APKを生成します。

## Build trigger

- 2026-07-06: Build APK for v1.4.0 four-player one-hole input.
