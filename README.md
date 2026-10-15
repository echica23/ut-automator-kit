# UT自動化キット

既存Javaプロジェクトで、AIによるUT生成・Maven実行・JaCoCo計測・仕様レビューを行うキットです。自然言語の `ut` コマンドをAIが解釈し、選択した実行基盤（JavaまたはPowerShell）で結果を集計します。AIモデルのAPI呼び出しや、OSの `ut` コマンドを提供するものではありません。

## 最初に知っておくファイル

このキットには、AIへ渡す指示書、設定のひな形、AIが内部で使う実行基盤が入っています。「キットのフォルダ」と「UTを適用するJavaプロジェクトのフォルダ」は別です。

| ファイル | 置き場所・用意する人 | 役割と使うタイミング |
| --- | --- | --- |
| [prompts/setup.md](prompts/setup.md) | キットに同梱 | **導入指示書**。最初にAIへ読ませ、既存プロジェクトの調査・設定・試運転を進める |
| [prompts/ut-instructions.md](prompts/ut-instructions.md) | キットに同梱 | **実行指示書**。導入後にAIへ読ませ、coverage/spec/reviewの動作を定める |
| [templates/ut-kit.json](templates/ut-kit.json) | キットに同梱 | **設定のひな形**。AIが適用先の環境に合わせた設定を作るために使う |
| [rules/project.example.md](rules/project.example.md) | キットに同梱 | **任意のテスト方針のひな形**。導入先の命名・Mock・レビュー規則等を用意する |
| `ut-kit.json` | 導入時にAIが対象Javaプロジェクトのルートへ作成 | **そのプロジェクト専用の設定**。使用するMaven・Java・出力先などを記録し、毎回の実行で読む |
| `ut-kit-setup.md` | 導入時にAIが対象Javaプロジェクトのルートへ作成 | **導入結果の記録**。変更内容・検証結果・未対応事項を人間が確認する |
| [dist/ut-automator-runner.jar](dist/ut-automator-runner.jar) | キットに同梱 | **Java版の実行補助**。新規導入の既定。依存同梱で利用者のビルドは不要 |
| [docs/runner-spec.md](docs/runner-spec.md) | キットに同梱 | **方式選択と共通仕様**。Java/PowerShellの使い分け・互換性 |
| [scripts/Invoke-UtRun.ps1](scripts/Invoke-UtRun.ps1) | キットに同梱 | **AI用の実行補助**。フォルダ作成・Maven実行・結果集計を共通化する。通常は人間が直接操作しない |
| [docs/script-spec.md](docs/script-spec.md) | キットに同梱 | **補助スクリプトの仕様書**。仕組みの確認・保守・トラブル調査で読む |

対象プロジェクトに元からある `pom.xml` はMavenのビルド設定です。導入時に必要な箇所を調整します。`-D` で指定する業務仕様の参照パス（ファイルまたはディレクトリ）は利用者が用意します。代わりに `-J` でJavadocを根拠にできます。実行後に作る `report.md` は成果物で、事前に用意するファイルではありません。

通常の流れは、**導入指示書をAIに読ませる → プロジェクト専用設定を作る → 実行指示書を読ませてutを使う → レポートを人間が確認する**、です。

## 3つの機能

| コマンド | 期待値の根拠 | 出力 |
| --- | --- | --- |
| coverage | 実装の観測可能な振る舞い | UT、結果、カバレッジ、到達できない箇所の説明 |
| spec | 指定資料またはJavadocの契約 | 同上＋ケースと仕様の対応、期待値と実際値、不一致 |
| review | 指定資料またはJavadocの契約 | specと同じUT生成・実行＋原因、実装箇所、確度、修正案 |

順番の制約はありません。review単独でUT生成から実行します。specとreviewでテストの網羅方針や期待値を変えません。ただし生成AIの非決定性により、別々の実行で同じソースになる保証はありません。reviewは分析量が増えます。処理クラス数と経過時間をレポートに記録します。

人間向けの成果物は全モードで **report.md一つ**です。成功ケースは件数を集計し、
FAIL・未確定・未カバーなどを個別に説明します。reviewでは同じ指摘に原因と修正案を追加します。
生成UTとカバレッジ証跡、実行・変更確認に必要な機械データは保持します。
トークンログは探索せず、処理クラス数・作業全体の経過時間・Maven実行時間を記録します。

## 初版の対象

- 指示書を読み、対象ファイルを編集し、選択した実行基盤とMavenを起動できるAIエージェントを使用。特定製品の内部ログには依存しません。職場でのCopilot利用を想定していますが、この変更のCopilot上での動作・速度確認は別途必要です。
- Java版はJava 11以上、PowerShell版はWindows PowerShell 5.1またはPowerShell 7。対象プロジェクト用Java、Maven 3.9系、JUnit5、Mockito、JaCoCo。
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


`-D` は仕様参照パスで、ファイルとディレクトリの両方を指定できます。例えば `-D docs/` なら、配下の `basic-design/`、`api/`、`business-rules/` 等を再帰探索し、AIが対象クラスとの関連性を判断して必要な資料を読みます。全文を無条件に読み込まず、読んだ資料・関連理由・未読範囲を `report.md` に残します。

仕様から期待値を一意に決められないケースは、実装で穴埋めせず **SPEC_UNKNOWN** として `report.md` に記録します。生成可能なケースは継続し、未確定ケースは実行件数と分けて報告します。すべて未確定ならテスト未実行・カバレッジ未計測として報告します。

coverageも自己比較テストを許しません。実装を解析して入力と固定期待値を事前に決め、本番コードの実測戻り値を期待値生成に使いません。

`com.example.*` を指定すると、AIが `com.example` とその下位パッケージから対象を探し、実行前に具体的なクラス名の一覧を報告します。通常は報告後にそのまま進み、同名クラスなどで対象が特定できない場合に確認します。実行基盤へ渡すのは、この解決済みのクラス名です。インターフェース・enum等を含めた対象範囲は一覧で明示し、検証できない型や未計測対象を黙って除外して100%と報告しません。

導入時には、生成UTを分離して動かすために必要な `pom.xml` 等の変更を行います。一方、導入後の `ut` 実行中は、設定済みの仕組みを使い、テストを通すためにビルド設定や依存ライブラリを書き換えません。出力先をコマンド引数で切り替えることは可能です。設定不足が判明した場合は、導入設定の見直しが必要であることを報告します。

## Javadocを根拠にする場合

設計資料を指定する-Dの代わりに、spec/reviewで-Jを明示指定できます。

```text
ut -P "C:\projects\my-app" -spec com.example.MyService -J
ut -P "C:\projects\my-app" -review com.example.MyService -J
```

-Dと-Jは併用不可で、spec/reviewではどちらか一方が必須です。coverageでは-Jを使いません。
記載された契約から期待値が決まるケースだけ生成し、不足・矛盾はSPEC_UNKNOWNとして部分継続します。
report.mdに参照箇所と確認できた契約の範囲を示します。不一致はJavadocとの不一致であり、
実装の業務バグとは限りません。詳細は [Javadocモード](docs/javadoc-mode.md) を参照してください。

## プロジェクト固有ルール（任意）

[rules/project.example.md](rules/project.example.md)を参考に、導入先で `rules/project.md` を用意し、
そのプロジェクトの `ut-kit.json` に `"projectRules": ["rules/project.md"]` を追加します。
通常のcoverage/spec/reviewで自動参照します。省略・null・空配列なら従来の汎用ルールだけです。
ルールは「どうテストするか」を定め、選択した仕様（`-D`資料／`-J`契約）やキットの安全制約を上書きしません。
優先順位・矛盾時の扱い・適用記録は [Project Rulesの設計](docs/project-rules.md) を参照してください。

## 保存先

```text
<outputRoot>/<coverage|spec|review>/<日時-一意ID>/
  run.json                 # 対象・仕様・実行条件
  project-rules.json       # 実行基盤：登録ルールのパスとハッシュ
  java/                    # この実行の生成UT
  build/                   # Maven出力、Surefire、JaCoCo
  test.log / coverage.log
  summary.json             # 実行基盤による実測集計
  case-results.json        # テスト結果・固定IDを含む出力
  before-hashes.json / ut-hashes.json / change-check.json
  report.md                # AIによる説明を含む最終レポート
```

毎回新しいフォルダを作り、既存のUT・実行結果を上書きしません。再試行も新しい実行IDを作成し、生成UTを必要に応じコピーして修正します。specとreviewの実行結果は独立しています。デモのsamplesへの自動コピーやREADMEの自動書換えはしません。Gitへ含める成果物の選択と、コミット・pushを含むすべてのGit操作は、人間が手動で行います。AIは実行しません。

## 実行基盤の選択と役割

新規導入ではut-kit.jsonに `"runner": "java"` を設定し、同梱JARを使います。
PowerShell版を使う場合は `"runner": "powershell"`。項目省略の既存環境はPowerShellのままです。
コマンドごとの指定は不要で、失敗時に他方式へ自動で切り替えることはありません。
方式変更は導入設定の変更として行い、新しい実行IDから使用します。

AIは対象・仕様の読解、UT生成、失敗分析、report.mdを担当します。
実行基盤はフォルダ作成、ハッシュ照合、Maven実行、XML集計と時間計測を担当します。
通常は利用者が直接操作する必要はありません。

1. prepare：新しい実行フォルダと変更確認の基準値を作成。
2. AI：java/へUTを生成。
3. execute：生成UTを実行し、件数・カバレッジ・変更確認を集計。
4. AI：report.mdを作成。

Java版の直接実行例（保守・調査用）：

```text
java -jar dist/ut-automator-runner.jar prepare --project "C:\projects\my-app" --mode spec --targets com.example.MyService --spec docs/
java -jar dist/ut-automator-runner.jar execute --project "C:\projects\my-app" --run "<prepareで得たパス>"
```

実際は導入時に確認したjava.exeとキットJARの絶対パスを使用します。
補助ツール用Javaと、Maven用のjavaHomeは別に選択できます。
Java版の起動に.ps1は不要です。Maven自身の依存取得は従来どおり必要です。

終了コードは0=テスト・計測成功、1=テストFAIL/ERROR、2=実行不能・未計測等。
先行検査で停止するとsummary.jsonがない場合があります。
詳細は[実行基盤の仕様](docs/runner-spec.md)、[PowerShell版の仕様](docs/script-spec.md)を参照してください。

## 既存プロジェクトを守るルール

- **Git操作は必ず人間が行う。AIによるGit操作、特にcommit・pushは厳禁。** 読み取り用のstatus/diff/logや、clone/fetch/pull、初期化、ブランチ操作、設定変更もAIは実行しない。GitHub等のAPI・連携ツールやスクリプト経由での代行も行わない。AIは変更ファイルと検証結果を報告するところまでとし、変更確認にはファイルのハッシュ照合を使う。

- 既存UTはsrc/test等に残し、生成UTは実行フォルダに置く。
- 導入で通常実行の依存関係・プラグイン設定を維持する。JUnit/Mockito等は既存バージョンを優先。
- UT実行中は本番コード・既存UT・ビルド設定・仕様を変更しない。protectedPathsで追加の保護対象も指定可能。
- カバレッジ分母は指定クラスのみ。対象がXMLにない場合は未計測。branchが0なら割合はN/A。
- reviewも本番コードを直さず、修正案だけを出す。

詳細は [設計](docs/design.md)、[Maven導入テンプレート](templates/maven-integration.md)、[検証記録](docs/validation.md) を参照してください。

実行方式と利用するJavaまたはPowerShellの実行ファイルは導入時に選びます。
Java 25ではJaCoCo 0.8.14以降の対応版を候補とします。テンプレートの0.8.12はJava 21の検証例です。
既存依存との整合・試運転は[導入手順](prompts/setup.md)と[Mavenテンプレート](templates/maven-integration.md)を参照してください。
