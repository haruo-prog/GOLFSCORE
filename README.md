# GOLFSCORE

Android向けのゴルフスコア管理アプリです。

## Version

- App version: 1.8.0
- V2 workflow redesign
- versionCode: 19
- compileSdk: 36
- targetSdk: 36
- minSdk: 26

## V2設計の主な変更

- 有償化を意識して、画面を機能追加型から商品化UIへ整理
- ラウンド中画面を入力中心に再設計
- ホーム / 履歴 / 分析 / 設定 の4画面構成
- ラウンド中はスコア、PAT、使用クラブ、ティーショット結果を1画面で入力
- Player1のみ詳細分析
- Player2からPlayer4はスコアのみ
- スコアはPAR基準の候補ボタンで入力
- 大叩き用に9から15を循環入力できるボタンを追加
- クラブセット登録を追加
- Player1のティーショットに使用クラブを記録
- クラブ別FW率、OB数、右ミス傾向を分析
- コーステンプレート保存を追加
- 最新保存コースの読込を追加
- コース別平均、ベスト、ラウンド回数を分析
- ホール別の苦手ホール分析を追加
- ラウンド後に自動分析コメントを生成
- スコアカードPDF出力
- PDF保存先指定と保存先記憶
- Google DriveフォルダをPDF保存先に指定可能
- Google Driveへバックアップ保存可能
- Google Driveからバックアップファイルを選択してリストア可能
- 当日使用クラブ写真を撮影してWebP軽量保存
- NKTSロゴをアプリアイコンに設定
- 1秒間隔の自動保存

## ラウンド中の思想

ラウンド中は余計なPDF、バックアップ、履歴操作を見せず、入力に集中できる画面にしています。設定、バックアップ、PDF保存先は設定画面へ整理しています。

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
