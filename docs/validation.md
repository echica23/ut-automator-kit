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
