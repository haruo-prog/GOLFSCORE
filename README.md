# GOLFSCORE

Android向けのゴルフスコア管理アプリです。

## Version

- App version: 1.9.0
- V2.1 keypad input update
- versionCode: 21
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## V2.1変更点

- スコア入力をテンキー式に変更
- 0から9の数字ボタンを追加
- 1+ボタンを追加
- 1+を押した後に0から5を押すと10から15を入力
- 0単独入力は未入力クリアとして扱う
- 登録画面最下部にキャンセル終了を追加
- キャンセル終了後に再確認を表示
- 登録画面へ戻る、ホームへ戻るを選択可能
- 全世界コース選択は商用利用可能なコースDB/APIが必要なため今回は未実装
- コーステンプレート保存と読込を継続

## 主な機能

- ホーム / 履歴 / 分析 / 設定 の4画面構成
- ラウンド中はスコア、PAT、使用クラブ、ティーショット結果を入力
- Player1のみ詳細分析
- Player2からPlayer4はスコアのみ
- クラブセット登録
- Player1のティーショットに使用クラブを記録
- クラブ別FW率、OB数、右ミス傾向を分析
- コーステンプレート保存と読込
- コース別平均、ベスト、ラウンド回数を分析
- ホール別の苦手ホール分析
- ラウンド後に自動分析コメントを生成
- スコアカードPDF出力
- PDF保存先指定と保存先記憶
- Google DriveフォルダをPDF保存先に指定可能
- Google Driveへバックアップ保存可能
- Google Driveからバックアップファイルを選択してリストア可能
- 当日使用クラブ写真を撮影してWebP軽量保存
- NKTSロゴをアプリアイコンに設定
- 1秒間隔の自動保存

## ビルド方法

1. Android Studioでこのリポジトリを開く
2. Gradle Syncを実行
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)` を実行

## GitHub Actions

`.github/workflows/build-apk.yml` により、pushまたは手動実行でdebug APKを生成します。

## Build trigger

- 2026-07-06: Build V2.1 keypad cancel flow.
