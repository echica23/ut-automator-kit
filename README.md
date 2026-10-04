# UT自動化キット

既存Javaプロジェクトで、AIによるUT生成・Maven実行・JaCoCo計測・仕様レビューを行うキットです。自然言語の `ut` コマンドをAIが解釈し、付属スクリプトで結果を集計します。AIモデルのAPI呼び出しや、OSの `ut` コマンドを提供するものではありません。

## 最初に知っておくファイル

このキットには、AIへ渡す指示書、設定のひな形、AIが内部で使う補助スクリプトが入っています。「キットのフォルダ」と「UTを適用するJavaプロジェクトのフォルダ」は別です。

| ファイル | 置き場所・用意する人 | 役割と使うタイミング |
| --- | --- | --- |
| [prompts/setup.md](prompts/setup.md) | キットに同梱 | **導入指示書**。最初にAIへ読ませ、既存プロジェクトの調査・設定・試運転を進める |
| [prompts/ut-instructions.md](prompts/ut-instructions.md) | キットに同梱 | **実行指示書**。導入後にAIへ読ませ、coverage/spec/reviewの動作を定める |
| [templates/ut-kit.json](templates/ut-kit.json) | キットに同梱 | **設定のひな形**。AIが適用先の環境に合わせた設定を作るために使う |
| [rules/project.example.md](rules/project.example.md) | キットに同梱 | **任意のテスト方針のひな形**。導入先の命名・Mock・レビュー規則等を用意する |
| `ut-kit.json` | 導入時にAIが対象Javaプロジェクトのルートへ作成 | **そのプロジェクト専用の設定**。使用するMaven・Java・出力先などを記録し、毎回の実行で読む |
| `ut-kit-setup.md` | 導入時にAIが対象Javaプロジェクトのルートへ作成 | **導入結果の記録**。変更内容・検証結果・未対応事項を人間が確認する |
| [scripts/Invoke-UtRun.ps1](scripts/Invoke-UtRun.ps1) | キットに同梱 | **AI用の実行補助**。フォルダ作成・Maven実行・結果集計を共通化する。通常は人間が直接操作しない |
| [docs/script-spec.md](docs/script-spec.md) | キットに同梱 | **補助スクリプトの仕様書**。仕組みの確認・保守・トラブル調査で読む |

対象プロジェクトに元からある `pom.xml` はMavenのビルド設定です。導入時に必要な箇所を調整します。`-D` で指定する業務仕様の参照パス（ファイルまたはディレクトリ）は利用者が用意します。実行後に作る `report.md` と `review.md` は成果物で、事前に用意するファイルではありません。

通常の流れは、**導入指示書をAIに読ませる → プロジェクト専用設定を作る → 実行指示書を読ませてutを使う → レポートを人間が確認する**、です。

## 3つの機能

| コマンド | 期待値の根拠 | 出力 |
| --- | --- | --- |
| coverage | 実装の観測可能な振る舞い | UT、結果、カバレッジ、到達できない箇所の説明 |
| spec | 指定した仕様 | 同上＋ケースと仕様の対応、期待値と実際値、不一致 |
| review | 指定した仕様 | specと同じUT生成・実行＋原因、実装箇所、確度、修正案 |

順番の制約はありません。review単独でUT生成から実行します。specとreviewでテストの網羅方針や期待値を変えません。ただし生成AIの非決定性により、別々の実行で同じソースになる保証はありません。reviewは分析量が増えますが、トークン数の大小は実測で比較します。

## 初版の対象

- Windows PowerShell 7、Java（対象プロジェクトが要求する版）、Maven 3.9系、JUnit5、Mockito、JaCoCo。
- 単一Mavenモジュール、または単独でビルドできるサブモジュール。サブモジュールの場合はそのディレクトリを `-P` に指定。
- 標準の `src/main/java` を使用するJavaクラス。既存UTは保持し、通常実行と生成UT実行を分離します。
- Gradle、JUnit4だけの環境、リアクタ全体の同時ビルドが必要な依存関係、非標準ソース配置、Kotlinは初版で未対応。導入時に検出・報告し、黙って設定を置き換えません。

## 導入

以下はAIのチャットへ入力するプロンプト例です。キットと対象プロジェクトのパスは配置先に置き換えてください。

### 1. 導入可否と変更案を調査する

[導入プロンプト](prompts/setup.md)を読み込ませ、最初は読み取りだけで調査します。

```text
C:\demo\ut-automator-kit\prompts\setup.md を読み、
C:\projects\my-app への導入可否を調査してください。
まだファイルは変更せず、既存UTとビルド設定を確認し、
必要な設定変更と検証方法を提示してください。
```

### 2. 設定を含めて導入する

提示された変更案を確認してから実施を依頼します。対象ルートの `ut-kit.json` もこの段階で作成するので、手作業で別途コピーする必要はありません。[設定例](templates/ut-kit.json)をもとに、Maven・Java・ローカルリポジトリを対象環境に合わせます。

```text
提示した導入案を適用してください。
既存の本番コードとUTは保持し、通常のテスト実行に影響がないことを確認してください。
ut-kit.jsonも作成し、既定の出力先を D:\ut-results にしてください。
JavaとMavenは、このプロジェクトで利用しているものを使用してください。
```

### 3. 設定内容と導入結果を確認する

設定ファイル作成後の確認です。導入作業を重複して実行する指示ではありません。

```text
C:\projects\my-app\ut-kit.json と導入記録を確認し、
Java、Maven、出力先、既存UTと生成UTの分離方法、検証結果を報告してください。
未対応事項や追加で必要な作業があれば明示してください。
この確認ではファイルを変更しないでください。
```

### 4. 実行ルールを読み込ませ、必要なコマンドを使う

[実行プロンプト](prompts/ut-instructions.md)を読み込ませます。新しいチャットを使う場合も読み込ませてください。

```text
C:\demo\ut-automator-kit\prompts\ut-instructions.md を読み、
以後のutコマンドに従ってください。まだ実行せず待機してください。
```

次のコマンドは、目的に応じていずれかを実行します。順にすべて実行する必要はありません。

```text
ut -P "C:\projects\my-app" -coverage com.example.MyService
ut -P "C:\projects\my-app" -spec com.example.MyService -D docs/spec.md
ut -P "C:\projects\my-app" -review com.example.MyService -D docs/ -O "D:\ut-results"
```

`-P` は必須です。`-D`、`-O` の相対パスは対象プロジェクト基準で解決します。`-O` 省略時は `ut-kit.json` のoutputRoot（設定例ではut-output）を使います。


`-D` は仕様参照パスで、ファイルとディレクトリの両方を指定できます。例えば `-D docs/` なら、配下の `basic-design/`、`api/`、`business-rules/` 等を再帰探索し、AIが対象クラスとの関連性を判断して必要な資料を読みます。全文を無条件に読み込まず、読んだ資料・関連理由・未読範囲を `spec-sources.json` に残します。

仕様から期待値を一意に決められないケースは、実装で穴埋めせず **SPEC_UNKNOWN** として `spec-unknown.json` に記録します。生成可能なケースは継続し、未確定ケースは実行件数と分けて報告します。すべて未確定ならテスト未実行・カバレッジ未計測として報告します。

coverageも自己比較テストを許しません。実装を解析して入力と固定期待値を事前に決め、本番コードの実測戻り値を期待値生成に使いません。

`com.example.*` を指定すると、AIが `com.example` とその下位パッケージから対象を探し、実行前に具体的なクラス名の一覧を報告します。通常は報告後にそのまま進み、同名クラスなどで対象が特定できない場合に確認します。スクリプトへ渡すのは、この解決済みのクラス名です。インターフェース・enum等を含めた対象範囲は一覧で明示し、検証できない型や未計測対象を黙って除外して100%と報告しません。

導入時には、生成UTを分離して動かすために必要な `pom.xml` 等の変更を行います。一方、導入後の `ut` 実行中は、設定済みの仕組みを使い、テストを通すためにビルド設定や依存ライブラリを書き換えません。出力先をコマンド引数で切り替えることは可能です。設定不足が判明した場合は、導入設定の見直しが必要であることを報告します。

## プロジェクト固有ルール（任意）

[rules/project.example.md](rules/project.example.md)を参考に、導入先で `rules/project.md` を用意し、
そのプロジェクトの `ut-kit.json` に `"projectRules": ["rules/project.md"]` を追加します。
通常のcoverage/spec/reviewで自動参照します。省略・null・空配列なら従来の汎用ルールだけです。
ルールは「どうテストするか」を定め、`-D` の業務仕様やキットの安全制約を上書きしません。
優先順位・矛盾時の扱い・適用記録は [Project Rulesの設計](docs/project-rules.md) を参照してください。

## 保存先

```text
<outputRoot>/<coverage|spec|review>/<日時-一意ID>/
  run.json                 # 対象・仕様・実行条件
  project-rules.json       # スクリプト：登録ルールのパスとハッシュ
  rules-application.json   # AI：参照・適用・除外・矛盾の記録
  java/                    # この実行の生成UT
  build/                   # Maven出力、Surefire、JaCoCo
  test.log / coverage.log
  summary.json             # スクリプトによる実測集計
  execution.md             # 機械集計の概要
  case-results.json        # テスト結果・固定IDを含む出力
  spec-sources.json        # spec/review：AIが選んで読んだ仕様と探索範囲
  spec-unknown.json        # spec/review：期待値未確定のケース（なければ空配列）
  before-hashes.json / ut-hashes.json / change-check.json
  report.md                # AIによる説明を含む最終レポート
  review.md                # reviewのみ
  token-usage.json          # 取得可能な場合の概算、または未取得理由
```

毎回新しいフォルダを作り、既存のUT・実行結果を上書きしません。再試行も新しい実行IDを作成し、生成UTを必要に応じコピーして修正します。specとreviewの実行結果は独立しています。デモのsamplesへの自動コピーやREADMEの自動書換えはしません。Gitへ含める成果物の選択と、コミット・pushを含むすべてのGit操作は、人間が手動で行います。AIは実行しません。

## スクリプトの役割

付属の [Invoke-UtRun.ps1](scripts/Invoke-UtRun.ps1) は、**AIが作成したUTを既存UTから分離して実行し、結果を機械的に集計するための補助スクリプト**です。毎回のフォルダ作成・Maven起動・XML集計を共通化し、古い結果の取り違えや件数の読み間違いを減らします。

| 担当 | 行うこと |
| --- | --- |
| AI | 対象を解決する、仕様・実装を読む、UTを書く、失敗を分析する、最終レポートとトークン概算を書く |
| スクリプト | 実行フォルダを作る、保護対象のハッシュを記録・比較する、MavenとJaCoCoを実行する、XMLから結果を集計する |

通常、利用者が入力するのは上記の `ut` コマンドです。スクリプトは、AIが毎回ディレクトリ操作や集計方法を考え直さず、同じ手順で処理するための内部用部品です。利用者による直接の実行・編集は通常不要で、保守やトラブル調査時に扱います。AIが内部でスクリプトを次のように使います。

1. `prepare`：新しい実行フォルダと変更確認の基準値を作成する。
2. AI：そのフォルダの `java/` にUTを作成する。
3. `execute`：生成UTを実行し、結果・カバレッジ・変更確認を集計する。
4. AI：集計と仕様・実装を照合し、`report.md`、必要なら `review.md` を作成する。

スクリプト自体はUTを生成せず、仕様適合や失敗原因も判断しません。全件スキップや0件実行は成功として扱いません。MavenがテストFAILで終了しても、通常は続いてJaCoCoを生成します。

### 動作確認・トラブル調査用の直接実行例

通常の利用では、これらを手動で実行する必要はありません。スクリプトには `ut` と異なる引数名を使います。

```powershell
# 出力は新しい実行フォルダの絶対パス。UTはまだ生成されない。
& "C:\demo\ut-automator-kit\scripts\Invoke-UtRun.ps1" -Action prepare -Project "C:\projects\my-app" -Mode spec -Targets com.example.MyService -Spec docs/spec.md

# AIが作成したUTをjava/に置いた後、prepareが返したパスを指定する。
& "C:\demo\ut-automator-kit\scripts\Invoke-UtRun.ps1" -Action execute -Project "C:\projects\my-app" -Run "<作成された実行フォルダ>"
```

集計完了時の終了コードは0=テスト成功かつ計測成功、1=テストFAIL/ERROR、2=実行不能・未計測等です。事前検査のエラーなどでは集計前に停止し、`summary.json` が作られない場合もあります。終了コードだけで原因を判断せず、エラー出力・ログ・作成されていれば `summary.json` を確認してください。

引数、設定項目、処理順、成果物、終了条件、現状の制約は [スクリプト仕様](docs/script-spec.md) にまとめています。

## 既存プロジェクトを守るルール

- **Git操作は必ず人間が行う。AIによるGit操作、特にcommit・pushは厳禁。** 読み取り用のstatus/diff/logや、clone/fetch/pull、初期化、ブランチ操作、設定変更もAIは実行しない。GitHub等のAPI・連携ツールやスクリプト経由での代行も行わない。AIは変更ファイルと検証結果を報告するところまでとし、変更確認にはファイルのハッシュ照合を使う。

- 既存UTはsrc/test等に残し、生成UTは実行フォルダに置く。
- 導入で通常実行の依存関係・プラグイン設定を維持する。JUnit/Mockito等は既存バージョンを優先。
- UT実行中は本番コード・既存UT・ビルド設定・仕様を変更しない。protectedPathsで追加の保護対象も指定可能。
- カバレッジ分母は指定クラスのみ。対象がXMLにない場合は未計測。branchが0なら割合はN/A。
- reviewも本番コードを直さず、修正案だけを出す。

詳細は [設計](docs/design.md)、[Maven導入テンプレート](templates/maven-integration.md)、[検証記録](docs/validation.md) を参照してください。
