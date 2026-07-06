# GOLFSCORE

Android向けのゴルフスコア管理アプリです。

## Version

- App version: 1.2.0
- versionCode: 12
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## 主な機能

- 18ホールのスコア入力
- PAR / SCORE / Putt / FW / OB / Penalty / メモ管理
- OUT / IN / TOTAL の自動集計
- フェアウェイキープ率、平均パット、スコア差分の自動計算
- 下書き自動保存
- ラウンド履歴保存
- テキスト出力とクリップボードコピー

## ビルド方法

1. Android Studioでこのリポジトリを開く
2. Gradle Syncを実行
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)` を実行

## GitHub Actions

`.github/workflows/build-apk.yml` により、pushまたは手動実行でdebug APKを生成します。
