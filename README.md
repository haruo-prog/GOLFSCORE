# GOLFSCORE

Android向けのゴルフスコア管理アプリです。

## Version

- App version: 1.7.0
- versionCode: 18
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## 主な機能

- フロントページを追加
- 過去のプレイを一覧表示
- 履歴を選択してスコアカード詳細を確認
- 過去3カ月・過去1年の平均スコア、PAT平均、FW率を簡易表示
- 登録モードに入るとスコア登録画面に切り替え
- 全ホール入力完了まで登録画面を維持
- カート移動中でも押しやすい大きな候補ボタン入力
- 入力ボタンを押しても画面上部へ戻らないように修正
- 1ホールずつ表示して入力
- 18ホールの入力進捗と入力漏れをホールボタンで表示
- 最大4名まで同時入力
- スコアは1から15まで直接タップ入力
- Player1のみPAT数を入力
- Player1のみティーショット結果と方向性を1タップ入力
- Tee入力はFW左・FW中・FW右・ラフ左・ラフ中・ラフ右・OB左・OB中・OB右
- Player2からPlayer4はスコアのみの簡易入力
- ラウンド中にスコアカードプレビューを表示
- PDF保存先を指定可能
- PDF保存先を記憶
- Google DriveフォルダをPDF保存先に指定可能
- 履歴からスコアカードPDFを出力
- 登録中の現在スコアカードもPDF出力可能
- ラウンド最後に当日使ったクラブを撮影して記録
- クラブ写真をWebPへ自動変換して軽量保存
- すべてのアプリデータをバックアップ
- Google Driveへバックアップファイル保存可能
- Google Driveからバックアップファイルを選択してリストア可能
- NKTSロゴをアプリアイコンに設定
- 1秒間隔の自動保存
- バックグラウンド移行時、画面回転時、アプリ終了時にも保存

## PDF出力

Android標準の `PdfDocument` と Storage Access Framework を使用します。初回にPDF保存先フォルダを選択すると、その保存先を記憶します。Google Driveフォルダを選択すれば、以後PDFはGoogle Drive側へ保存できます。

## バックアップ / リストア

Android標準のファイル作成・ファイル選択機能を使用します。Google Driveアプリが入っている端末では、バックアップ保存先やリストア元としてGoogle Driveを選択できます。

## ビルド方法

1. Android Studioでこのリポジトリを開く
2. Gradle Syncを実行
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)` を実行

## GitHub Actions

`.github/workflows/build-apk.yml` により、pushまたは手動実行でdebug APKを生成します。

## Build trigger

- 2026-07-06: Build v1.7.0 storage backup and club photo features.
