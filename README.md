# GOLFSCORE

Android向けのゴルフスコア管理アプリです。

## Version

- App version: 1.5.0
- versionCode: 15
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## 主な機能

- フロントページを追加
- 過去のプレイを一覧表示
- 履歴を選択して詳細確認
- 過去3カ月・過去1年の平均スコア、PAT平均、FW率を簡易表示
- 登録モードに入るとスコア登録画面に切り替え
- 全ホール入力完了まで登録画面を維持
- カート移動中でも押しやすい大きなボタン入力
- 1ホールずつ表示して入力
- 最大4名まで同時入力
- スコアは1から15まで入力
- Player1のみPAT数を入力
- Player1のみティーショット結果をフェアウェイ・ラフ・OBで選択
- Player1のみ狙いに対する方向性を左・中央・右で選択
- Player2からPlayer4はスコアのみの簡易入力
- 1秒間隔の自動保存
- バックグラウンド移行時、画面回転時、アプリ終了時にも保存
- テキスト出力とクリップボードコピー

## ビルド方法

1. Android Studioでこのリポジトリを開く
2. Gradle Syncを実行
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)` を実行

## GitHub Actions

`.github/workflows/build-apk.yml` により、pushまたは手動実行でdebug APKを生成します。
