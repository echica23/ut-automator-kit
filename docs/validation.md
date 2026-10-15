# 初版の検証記録

実行日: 2026-10-04（Asia/Tokyo）。キット: C:\demo\ut-automator-kit。適用先: C:\demo\ut-automator-kit-test。

Java 21.0.11、Maven 3.9.9、JUnit5 5.11.4、Mockito 4.11.0、JaCoCo 0.8.12、PowerShell 7で実施。

## 適用先

reservation-demoから本番ソースと仕様だけをコピーし、デモの実行設定を持ち込まず、通常UTが存在するMavenプロジェクトとして構成した。通常のExistingTestを1件用意し、生成UTのソースルートとは分離した。

検証用の生成UTにはreservation-demoで生成・検証済みのJavaソースをコピーして使った。これは実行基盤の検証であり、このキットのプロンプトから新しくAI生成したUTの品質評価ではない。review用UTもその実行のjava/に置き、先行spec出力は使用していない。

## 実行結果

| 実行 | 件数 | 成功 | 失敗 | Line | Branch |
| --- | ---: | ---: | ---: | --- | --- |
| 通常UT（開始時） | 1 | 1 | 0 | 対象外 | 対象外 |
| review（最初に単独実行） | 48 | 42 | 6 | 32/32 | 24/24 |
| coverage | 35 | 35 | 0 | 32/32 | 24/24 |
| spec | 48 | 42 | 6 | 32/32 | 24/24 |
| 通常UT（終了時） | 1 | 1 | 0 | 対象外 | 対象外 |

reviewは-O相当のOutputで `custom results` を指定、coverage/specは設定のut-outputへ出力。失敗6件はPREMIUM繁忙期とSTANDARD数量5に関する元の仕様不一致。実行スクリプトは失敗を1、成功を0で返した。全生成実行のunexpectedSuitesとprotectedChangesは空。既存UTは混入していない。

## 拒否動作

実行済みIDの再利用、specの仕様省略、本番ソース配下への出力、生成UTなし、全件skipを試し、すべて成功として扱わないことを確認。全件skipは実際にMavenを実行し、1件skip、EXECUTION_ERROR、終了コード2となった。

証跡は適用先のvalidation-runs.json、guard-checks.json、baseline.log、after-normal.log、および各実行フォルダのsummary.json / XML / ハッシュ。

## 限界

- 単一の小さな検証プロジェクトによる初版の試験。任意の既存プロジェクトや複数モジュールへの適用を保証しない。
- AIによる新規UT生成、自由形式の仕様解釈、最終review.mdの品質、今回のトークンログ計測の自動化はこの機械実行試験には含めない。
- 導入テンプレートは自動パッチではなく、既存設定を読んで適用するためのガイド。未知のプロジェクトで即座にpomを置き換えない。
- 公開・pushは行っていない。reservation-demo本体は変更していない。

## 仕様参照パス拡張の検証（2026-10-04）

-D/Specをファイルまたはディレクトリに拡張した。テスト環境のspec-path-fixture/business-rules/とapi/に資料を置き、以下を確認した。

- 従来の単一ファイル指定はprepare成功。
- reviewのディレクトリ指定はprepare成功。
- 入れ子の仕様ファイル2件をbefore-hashes.jsonに記録。
- 配下ファイルの変更後はexecuteがMaven開始前に拒否。
- 新規実行IDでディレクトリを指定し、既知の仕様UT fixtureを実行。48件中42件成功・6件失敗、JaCoCo生成成功、保護対象変更なし。

証跡: テスト環境のspec-path-validation.json、spec-directory-change-check.log、および記録された実行フォルダ。

関連資料の選択、自己参照テスト禁止、SPEC_UNKNOWNでの部分継続はAI向けルールと最終レポートひな形に反映した。上記の機械試験はAIが資料を選ぶ品質や仕様不足判定を評価したものではない。

## Project Rules拡張の検証（2026-10-04）

設定schemaVersion=1を維持し、projectRules省略と登録ありの両方で、
既存の実行基盤用fixtureを新規IDで実行した。reviewを先に単独実行した。
登録ありでは検証専用の命名規則（明示的なDisplayNameに「検証:」を付加しIDは維持）を
fixtureへ適用した。業務期待値は変更していない。

| 設定 | モード | 件数 | 成功 | 失敗 | Line | Branch |
| --- | --- | ---: | ---: | ---: | --- | --- |
| 未設定 | coverage | 35 | 35 | 0 | 32/32 | 24/24 |
| 未設定 | spec / review（各） | 48 | 42 | 6 | 32/32 | 24/24 |
| 登録あり | coverage | 35 | 35 | 0 | 32/32 | 24/24 |
| 登録あり | spec / review（各） | 48 | 42 | 6 | 32/32 | 24/24 |

全6実行でJaCoCo生成、既存UT非混入、保護対象変更なしを確認。
通常UTも終了時に1件成功し、既存結果を維持した。

自動確認51項目には次を含む。

- モード別の件数・終了コード・計測・分離、参照記録の状態。
- 省略/null/空配列、相対/絶対パス、複数ファイル。
- 非配列、空文字、非文字列、ディレクトリ、不存在、重複、非Markdownの拒否。
- prepare後のルール変更・一時的な欠落をMaven開始前に拒否。
- 既存ガード：実行ID再利用、仕様省略、保護対象への出力、UTなしを拒否。
- 全件skipを実行しEXECUTION_ERROR・終了コード2。
- 本番ソース、既存UT、仕様、元の設定、POMのハッシュ一致。

追加のAI確認では、ルールあり3実行のDisplayNameと固定IDを読み返した。
reviewの追加規則「nullと空文字を別ケースとして確認する」を適用し、
UTおよびSurefire XMLの2ケースIDを照合した。rules-application.jsonと
project-rules-review.mdへ結果を保存し、対象外モードではreview節を非適用と記録した。

証跡はテスト環境の `project-rules-validation-20261004-202924/`。
Validate.ps1、checks.json、runs.json、protected-before/after.json、normal-ut.log、
final-check.jsonおよび各実行フォルダを保存した。最新版への参照は
project-rules-validation-latest.txt。検証専用設定を-Configで渡し、通常のut-kit.jsonは変更していない。

再検証時は新しい検証フォルダを用意し、Validate.ps1へそのパスを-Workspaceで渡す。
既存fixtureのパスとローカル環境を参照するテスト環境専用スクリプトであり、
公開キットの汎用実行機能ではない。通常UTとAIの適用証跡照合は別途実施する。

限界：新しいAIセッションによる自動読解・自由形式ルール全体の解釈品質、
複雑な矛盾解消・大規模ルール選択は今回の自動試験の対象外。
キットのプロンプト・設計で方針を定義し、代表的な命名と追加レビューのみ適用確認した。
また、既存case-results.jsonのoutputがXML要素名の文字列になるケースを確認したため、
ケースIDの照合には元のSurefire XMLを使用した。この既存の集計課題は今回変更していない。

## レポート簡素化・時間計測の検証（2026-10-15）

人間向け説明をreport.mdへ統合し、成功ケースを集計表示に変更した。
AI作成の重複JSON・review.md・トークン記録と、機械生成のexecution.mdを新規出力から外した。
過去の検証記録と成果物は履歴として保持している。上記2026-10-04のファイル名は当時の仕様である。

既存Validate.ps1を新規フォルダで実行し、51項目すべて成功。
Rules未設定/登録あり × coverage/spec/reviewの6実行で以前と同じ結果となった。

| 設定 | モード | 件数 | 成功 | 失敗 | Line | Branch |
| --- | --- | ---: | ---: | ---: | --- | --- |
| 未設定 / 登録あり（各） | coverage | 35 | 35 | 0 | 32/32 | 24/24 |
| 未設定 / 登録あり（各） | spec / review（各） | 48 | 42 | 6 | 32/32 | 24/24 |

正当な6件の仕様FAIL、FAIL後のJaCoCo生成、生成UTの分離を維持した。
既存ガード（実行ID再利用、仕様省略、保護先出力、UTなし、全件skip、
ルール形式・変更・欠落の拒否）も維持した。

追加のValidate-ReportChanges.ps1は25項目すべて成功。

- 全6実行のtest/JaCoCo時間が記録され、合計と一致。
- execution.mdを生成しない。
- 全6実行のcase-results.json出力を元Surefire XMLと照合し、件数・本文が一致。
  以前確認していたCDATAの型名文字列化を修正し、固定IDを含む本文を保持した。
- 存在しないMaven指定でEXECUTION_ERROR・終了コード2。
  起動試行の時間を記録し、未着手のJaCoCo時間はnull、合計も整合。
- 通常UTは1件成功。本番ソース・既存UT・仕様・元設定・POMのハッシュ一致。

PowerShell構文解析、差分の空白検査、実行プロンプト・README・設計・ひな形の
出力ルール整合性も確認した。トークンログを読む指示は削除し、
Project Rules、SPEC_UNKNOWN、安全制約、private直接テスト禁止を維持した。

証跡はテスト環境の report-validation-20261015-092315/。
Validate.ps1、Validate-ReportChanges.ps1、checks.json、additional-checks.json、
runs.json、protected-before/after.json、normal-ut.log、および各runのXML・JSON・ログを保存。
同じ検証を行う場合は新しい検証フォルダへ両スクリプトをコピーし、
Validate.ps1、Validate-ReportChanges.ps1の順に-Workspaceでそのフォルダを渡す。
既存fixtureを利用するテスト環境専用の検証であり、公開キットの実行機能ではない。

限界：この検証は実行基盤と指示書の整合性の確認であり、新しいAIセッションでの
UT生成・レポート品質や、Copilot上の動作・速度改善を実測したものではない。
作業全体の時間・実行確認済み対象クラス数はAIが根拠を確認してレポートへ記載する責務であり、
今回追加したスクリプトの自動計測はMaven呼出し区間に限る。

## Javadoc指定の検証（2026-10-15）

-J（スクリプトではprepareの-Javadoc）をspec/reviewへ追加。
以前のレポート簡素化に対する未コミット変更を保持して検証した。

既存Validate.ps1の51項目とValidate-ReportChanges.ps1の25項目が成功。
-Dのファイル/ディレクトリ指定、coverage、Rules有無の3モード、実行保護、
時間計測、XML本文集計、通常UT1件成功は維持された。

追加のValidate-Javadoc.ps1は17項目成功。独立したfixtureモジュールを作成し、
元のテスト環境の本番ソースや設定は変更していない。

- spec/reviewで-Javadocのみのprepareが成功し、specSource=javadoc、spec=null。
- 記載契約「入力1は10、入力2は20」から固定期待値の2ケースを用意。
  各モード1件成功・1件FAIL（実測99）、JaCoCo生成成功、保護対象変更なし。
- SpecとJavadocの併用、両方省略を両モードで拒否。coverageのJavadocも拒否。
- Javadocのみの変更もexecute前に検出し、Mavenを起動しない。
- 文書指定のspecSource=documentと既存パス記録を確認。
- 新規fixtureで旧形式相当のspecSource省略manifestを用意し、execute可能であることを確認。
- prepareだけではMavenを起動しない。全件未確定時にexecuteを呼ばない判断はAI側の責務。

代表的なAI確認としてspec/reviewのreport.mdを作成し、成功は集計、
JD-02の契約不一致（期待20、実測99）と、JD-U01/JD-U02の未確定を個別に記載した。
未記載の入力・変換規則を実装から補完せず、reviewの案は記載契約に従う場合の案とした。
これは固定fixtureに対する確認であり、独立したAIセッションやCopilotでの
自由形式Javadoc解析の品質評価ではない。継承・複雑な契約矛盾・外部資料選択の
自動読解品質は未検証。Javadocの品質基準・執筆ガイドは今回追加していない。

証跡: テスト環境 javadoc-validation-20261015-131056/。
Validate.ps1、Validate-ReportChanges.ps1、Validate-Javadoc.ps1、
checks.json、additional-checks.json、javadoc-checks.json、javadoc-runs.json、
各runのUT・report.md・XML・ハッシュ・ログ。合計93チェック成功。
再検証は新しいWorkspaceに3スクリプトをコピーして順に実行する。
既存検証fixtureとローカル環境を使用するテスト環境専用スクリプトである。

## PowerShell 5.1／7両対応の検証（2026-10-15）

Windows PowerShell 5.1.26100.9711とPowerShell 7.6.5で、同じ実行スクリプトを検証した。
Java 21.0.11 / Maven 3.9.9 / JaCoCo 0.8.12等は既存fixtureを維持した。

各ホストで既存51項目・レポート25項目・Javadoc17項目、計93項目が成功（合計186）。
coverage35件成功、spec/review各48件中42成功・6FAILを維持。
Javadoc fixture各2件中1成功・1FAIL、通常UT1件成功。
保護機構、Rules、-D/-J、起動失敗、全件skip、再利用拒否、XML本文・時間計測も維持した。

追加確認は各ホスト2項目、合計4項目成功。
- 日本語・空白を含むプロジェクトパス、UTF-8 BOMなしの設定JSON・規則・Java、
  日本語のテスト名/標準出力、空白入りASCII出力先でテスト1件成功、JaCoCo生成、保護対象変更なし。
- 診断stderrを出して終了コード0を返すローカルcmd fixture。
  test/coverage終了コードは0、起動例外なし、stderrをログに保存。
  実テスト/XMLを作らないfixtureなので全体は正しくEXECUTION_ERROR・終了コード2。

最終チェックは計190項目成功。途中で見つかった.NET Framework側の外部DTD参照、
ネイティブ終了コードのスコープ差、XML改行差を修正して最終セットを再実行した。
PowerShellスクリプトはBOM付き、JSONはUTF-8 BOMなしで保存する。

検証証跡: テスト環境 powershell-validation-20261015-140505/。
powershell/とpwsh/以下に、実際の子プロセスホストを切り替えた3検証スクリプトと
checks.json / additional-checks.json / javadoc-checks.json、各runの証跡を保存。
検証ハーネス自体はPowerShell 7で実行し、キット本体を対象ホストで起動した。
Validate-Unicode.ps1、unicode-checks.json、stderr-checks.jsonも同フォルダに保存。

このPCの5.1は実行ポリシー未設定によるRestricted既定値だったため、
組織ポリシー未指定を確認し、検証子プロセスのみRemoteSignedで起動した。
永続的な実行ポリシーは変更していない。キットは実行ポリシーを変更しない。

制約:
- 日本語の出力先では、このMaven/Surefire/JDKの組合せでJaCoCoデータの保存先が
  文字化けし未計測となった。JSON/Java/テスト本文は正常。ASCIIの出力先へ切り替えると
  同じ日本語プロジェクトで両ホストとも成功した。導入手順に切り分けを記載した。
- Java 25の実行環境はこのPCにない。0.8.14での正式対応は公式リリースを根拠とする
  導入方針であり、Java 25・対象企業プロジェクトの組合せを実動検証したものではない。
- Mockito等を含む実際の依存構成、社内取得可否、実行ポリシーは導入先で確認する。

## Javaランナー追加時の回帰確認（2026-10-15）

新規導入の既定をJava、runner未指定の既存設定をPowerShellとした。
配布JARはJDK 21.0.11でrelease 11としてビルドし、Maven clean verify成功。
Java 11/25の実機実行は未確認。Windows以外も未検証。

- Javaユニットテスト11件成功。引数・設定、保護、JSON/BOM、
  XML/CDATA・外部エンティティ遮断、未計測時の終了判定を確認。
- Java版で既存51項目、レポート25項目、Javadoc17項目、計93項目成功。
- 既存設定（runner省略）のPowerShell 5.1版で既存51項目成功。
- 追加13項目成功。PowerShell 5.1/7それぞれで明示powershellを受理し、
  java/不正値を拒否。RestrictedのPowerShellからポリシー上書きなしで
  JARのversionコマンドを直接起動。Rules有無×3モードの6実行について、
  Java/PowerShellのテスト集計・終了コード・カバレッジ件数の一致を確認。

実Mavenでcoverageは35件成功、spec/reviewは各48件中42成功・6FAIL。
Javadocは2件中1成功・1FAIL。正当なFAILは維持している。
試験用のPowerShellアダプターは既存検証ハーネス再利用のためだけに使用し、
製品Javaランナーの起動にps1は不要。生成UTのAI品質を再評価した試験ではない。

証跡はテスト環境 java-runner-validation-20261015-162126/ の
checks.json、additional-checks.json、javadoc-checks.json、
powershell/checks.json、runner-selection-checks.json、および各run。
ユニットテスト結果はrunner/target/surefire-reports/。

配布JARとソースのビルド結果のSHA-256一致、ライセンス同梱も確認。
職場独自のJava/JAR実行制限やウイルス対策による制限を回避する機能ではない。
日本語出力先に関する既存の制約は解消したと扱わない。
