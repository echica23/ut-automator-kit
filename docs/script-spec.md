# Invoke-UtRun.ps1 スクリプト仕様

対象: [scripts/Invoke-UtRun.ps1](../scripts/Invoke-UtRun.ps1)。この文書は現行実装の仕様であり、未実装の機能を含む将来計画ではありません。

## 1. 目的と責務

AIが生成したUTを既存UTから分離して実行し、実際のSurefire/JaCoCo結果を機械集計する。実行フォルダと変更確認の方法を統一し、過去の結果の使い回しを避ける。

AIが担当する対象選定、ワイルドカード展開、UT生成、仕様根拠の確認、固定ケースIDの照合、原因分析、report.md/review.md、トークン概算は実装しない。ファイルを削除・移動する処理、Git操作、設定の自動導入も行わない。

## 2. 前提

PowerShell 7、設定済みのMavenモジュール、Java、JUnit5、Mockito、JaCoCoを使用する。標準ソースルートsrc/main/java、単独モジュール、テスト結果build/surefire-reports、計測結果build/site/jacoco/jacoco.xmlを前提とする。

対象モジュールのpom.xmlに以下が直接記載されていることを検査する。

- build.testSourceDirectoryが文字列 `${ut.sources}`。
- build.directoryが文字列 `${ut.build}`。
- profilesに設定ファイルで指定したprofileのidが存在する。

有効POMを生成して親・継承・プラグイン設定を解決する機能はない。この検査だけで実際のビルドの分離を保証するものではなく、導入時の試運転も必要。profileの完全な内容や全依存の互換性は検証しない。

## 3. 引数

| 引数 | 必須・用途 | 解決方法 |
| --- | --- | --- |
| Action | 必須。prepareまたはexecute | PowerShellのValidateSetで検査 |
| Project | 必須。対象モジュール | Resolve-Pathで実在確認。運用上は絶対パスを渡す |
| Config | 共通。省略時ut-kit.json | 相対ならProject基準。executeにも同じ設定を渡す |
| Mode | prepareで必須。coverage/spec/review | executeではrun.jsonの値を使用 |
| Targets | prepareで必須。完全修飾クラス名の配列 | `*`は不可。Project/src/main/java以下の対応する.javaの存在を検査 |
| Spec | prepareのspec/reviewで必須 | 相対ならProject基準。既存のファイルまたはディレクトリ |
| Output | prepareの出力ルート指定 | 相対ならProject基準。未指定なら設定outputRoot |
| Run | executeで必須。prepareの返却パス | Resolve-Pathで解決。相対なら呼出し時の作業ディレクトリ基準なので絶対パス推奨 |

execute時のMode/Targets/Spec/Outputは実行設定を上書きしない。run.jsonに保存された値を使う。coverageではSpecは使用しない。

AIコマンドとの対応: `-P`→Project、`-coverage/-spec/-review`→Mode、対象→解決済みTargets、`-D`→Spec、`-O`→Output。

## 4. 設定ファイル

[設定例](../templates/ut-kit.json)。schemaVersion以外も含め、任意の設定を完全にスキーマ検証する仕組みではない。導入時に必要な値を設定する。

| 項目 | 用途 |
| --- | --- |
| schemaVersion | 1以外を拒否 |
| maven | 実行可能ファイルのパスまたはコマンド名。executeで空を拒否。空白を含む追加引数を連結して設定しない |
| javaHome | 指定時だけJAVA_HOMEを一時設定。通常のMaven実行後に元へ戻す |
| localRepository | 指定時、-Dmaven.repo.localとして渡す。絶対パス推奨 |
| profile | -Pに渡すプロファイル名。初版は1個のidを指定 |
| outputRoot | prepareのOutput省略時に使用。相対ならProject基準 |
| projectRules | 任意のMarkdownファイルパス配列。省略/null/[]は無効。相対はProject基準。絶対も可。空文字・非配列・重複・非.md・不存在を拒否。詳細は[Project Rules](project-rules.md) |
| protectedPaths | 変更を検出するファイル/フォルダ。相対ならProject基準。ディレクトリは再帰走査 |

設定値に加え、src、pom.xml、.mvn、利用したConfigファイルを必ず保護対象とする。登録された全Project Rulesファイルも加える。spec/reviewでは指定仕様参照パスも加える。ディレクトリの場合は全配下ファイルを再帰的にハッシュ記録し、変更・追加・削除を検出する。AIによる本文読解は関連資料に限定し、この全ファイルのハッシュ処理とは区別する。

## 5. 処理の流れ

### 共通の事前確認

ProjectとConfigを解決し、JSON、schemaVersion、pom.xml、導入用プロパティとprofileを検査する。Project Rulesの型・参照先を検査しSHA-256を取得する。ここで失敗するとMavenは起動しない。

### prepare

1. ModeとTargetsを確認。各クラス名をパスに変換し、標準ソースルートに存在するか検査する。
2. spec/reviewの場合はSpecを確認し、保護対象へ追加する。
3. Outputまたは設定outputRootを絶対化する。保護対象自身・その配下、Project自身、Project/.gitで始まるパスへの出力を拒否する。
4. `<出力ルート>/<Mode>/<yyyyMMdd-HHmmss>-<GUID先頭8文字>/` を作る。日時部分は実行ホストのローカル時刻。一意性はランダム値と既存フォルダ検査でも確保する。
5. java/を作り、保護対象のパスとSHA-256をbefore-hashes.jsonへ保存する。
6. run.jsonへ条件を保存。標準出力へ実行フォルダの絶対パスを返し、終了コード0。

実行IDには推測したテスト成否等を含めない。prepareだけではUTやテスト結果は作成されない。

### AIによる生成

AIが実行フォルダのjava/へUTを配置する。この間に保護対象が変わればexecute前の検査で拒否する。helperを含むJavaファイルはこのフォルダ内に置く。

### execute

1. run.jsonを読み、project/config/runPathが今回の引数と一致するか確認する。
2. execution-started.jsonがあれば再利用を拒否する。一度開始した実行は成功/失敗にかかわらず新IDで再試行する。
3. before-hashes.jsonと現在のファイル集合・SHA-256を比較。追加・削除・変更があれば開始前に停止する。
4. java/以下の.javaを走査し、JUnitアノテーションを含むUTのパッケージ＋ファイル名を取得する。UTが0件なら拒否する。
5. JAVA_HOMEとMaven引数を準備し、execution-started.jsonとut-hashes.jsonを保存する。
6. Projectを作業ディレクトリにして、Maven test、次にjacoco:reportを呼ぶ。標準出力・標準エラーを別々のログへ保存する。通常のテストFAILは後者を妨げない。
7. Maven呼出し区間のfinallyで作業ディレクトリとJAVA_HOMEを戻す。起動例外はexecutionErrorに記録する。
8. 保護対象を再照合してchange-check.jsonへ記録する。変更を元に戻す処理は行わない。
9. Surefire XMLとJaCoCo XMLを読み、case-results.json、summary.json、execution.mdを作成する。
10. 標準出力にsummary.jsonの絶対パスを返し、集計結果に応じた終了コードで終了する。

再試行用のフォルダをスクリプト自身が自動作成することはない。AI/利用者がprepareから開始する。

## 6. Mavenへの引数

共通で `-B`、プロファイル、ut.sources、ut.build、testを渡す。testには抽出したUTクラス名をカンマ区切りで渡す。localRepository指定時だけその引数を追加する。

```text
-P<profile>
-Dut.sources=<実行フォルダ>/java
-Dut.build=<実行フォルダ>/build
-Dtest=<パッケージ付きUTクラス名,...>
```

実装では引数配列を `& $maven @argsBase test` 等で渡すため、PowerShellソースにカンマ入り引数を直接書く構文問題を避ける。Maven Wrapperの探索・インストールは行わず、mavenに設定したものを使用する。

Maven内部の依存取得、コンパイル、プラグイン処理はMavenの責任である。スクリプトはプラグイン実行を隔離するサンドボックスではない。

## 7. 成果物

| ファイル/フォルダ | 作成時点 | 内容 |
| --- | --- | --- |
| java/ | prepare | AIが後から置く生成UTのルート |
| before-hashes.json | prepare | 絶対ファイルパス→SHA-256の対応表 |
| run.json | prepare | schemaVersion/project/config/mode/targets/spec/projectRules（path・sha256配列）/runId/runPath/protectedPaths/startedAt |
| project-rules.json | prepare | schemaVersion、files（path・sha256配列）、status。未設定はNOT_CONFIGURED、登録ありはPENDING_AI_REVIEW。内容適用の完了を意味しない |
| execution-started.json | execute開始前 | 開始時刻、maven、引数、UTクラス一覧。再利用防止マーカー |
| ut-hashes.json | Maven実行前 | 生成JavaファイルのSHA-256。実行後の自動照合はしない |
| test.log / coverage.log | Maven実行 | 各呼出しの出力。起動できなければ片方が存在しない場合がある |
| build/ | Maven | コンパイル出力、Surefire、JaCoCo。Maven設定に依存 |
| change-check.json | Maven呼出し後 | unchangedとchangedPaths |
| case-results.json | 集計時 | name/class/outcome/failure/error/output。outputにはsystem-outを格納 |
| summary.json | 集計時 | 実行状態、終了コード、件数、coverage、missingCoverage、unexpectedSuites、protectedChanges、executionError等 |
| execution.md | 集計時 | 上記の人間向け概要。仕様照合を含む最終レポートではない |

report.md、review.md、token-usage.json、spec-sources.json、spec-unknown.json、rules-application.jsonはAIが作成する。ルール内容の解釈・矛盾判断・適用検証はスクリプトでは行わない。SPEC_UNKNOWNはJUnitの実行状態ではなく、スクリプトはこれをFAIL/SKIPとして集計しない。全ケースの期待値が未確定ならAIはexecuteを呼ばず、prepareのフォルダへ未実行レポートを残す。summary.jsonのaiReportCompleteはfalseで固定し、AIレポートが完成したと自動判定しない。

## 8. 集計・判定

Surefireの各TEST-*.xmlのtests/failures/errors/skippedを加算し、成功件数はtests−failures−errors−skipped。予定UTクラスにないsuite名はunexpectedSuitesに記録する。ケースの状態はfailure→error→skipped→PASSの順で判定する。固定IDの独立フィールドは抽出せず、system-out経由でAIが確認する。

JaCoCoではtargetsのドットをスラッシュへ変換し、クラス名が完全一致するLINE/BRANCHだけを収集する。percentは小数第2位に丸める。分母0はnull（最終レポートではN/A）。XMLにクラスがなければmissingCoverageへ記録する。カウンタそのものが欠落している場合の追加検査は実装していない。

statusの優先順位:

| status | 条件 |
| --- | --- |
| EXECUTION_ERROR | Maven起動例外、0件、全件skip、予期しないsuite、保護対象変更、test終了コードが0/1以外、または非0終了なのにfailure/errorが0 |
| TEST_ERROR | 上記以外でerrors>0 |
| TEST_FAILURE | 上記以外でfailures>0 |
| PASS | 上記以外 |

statusはテスト側の状態であり、カバレッジ取得成功を表さない。例えばテストPASS・JaCoCo未生成ならstatus=PASSでも終了コードは2。カバレッジ100%も業務仕様適合を意味しない。

## 9. 終了コードと停止時の注意

集計末尾に到達した場合:

| コード | 条件 |
| --- | --- |
| 2 | status=EXECUTION_ERROR、missingCoverageあり、またはcoverageExitが非0 |
| 1 | 上記以外でstatusがPASS以外 |
| 0 | 上記以外 |

上表は全エラーを一律に捕捉する保証ではない。引数エラー、事前検査のthrow、ファイル権限エラー、破損JSON/XML等は集計前に停止し得る。`pwsh -File`では非0で終了し、summary.jsonがない場合もある。エラー出力を保存して確認する。

Maven呼出しで例外が出た場合、未実施の終了コードは-1。test起動時の例外ではjacoco:reportに進まない。通常のテストFAILで非0終了する場合とは区別する。

## 10. 保護の範囲・実装上の制約

- ハッシュ比較は変更検出であり書込み禁止ではない。差分の復元や既存結果の削除はしない。
- Resolve-Underは絶対化の補助であり、必ず基準ディレクトリ内に留める関数ではない。出力の保護確認はprepareで別途行う。
- 出力パスの判定は文字列に基づく。シンボリックリンクやジャンクションをたどった実体の検査、敵対的なrun.jsonの改ざん対策、同時executeの排他制御はない。同じ実行フォルダへ並列でexecuteしない。
- JUnitの抽出は正規表現。独自の合成アノテーション、完全修飾アノテーション、特殊なネスト構成などを網羅しない。@Test等とパッケージ宣言を通常形式で書き、Test末尾のファイル名を使う。
- 予期しないsuiteは検出するが、予定UTクラスのすべてが実行されたかの完全な集合一致や、全固定IDの存在までは検査しない。AIが実測ケースを照合する。
- 内部クラス・集約JaCoCo・親POMだけの導入設定・独自ソースルートには対応しない。親や他モジュールの保護ファイルは必要ならprotectedPathsに追加する。
- 全件skipは拒否するが、一部skipは件数として残す。許容できるかはAI/利用者が判断する。
- JAVA_HOMEの復元はMaven呼出し区間のfinallyで行う。それ以前のマーカー書込み等でエラーになった場合の復元は保証しないため、独立したpwshプロセスでの呼出しが望ましい。
- 非0ネイティブ終了を例外化するPowerShell設定を利用すると挙動に影響する。検証時はpwsh -NoProfileで実行している。

## 11. 検証・保守

初版の実行検証は [検証記録](validation.md) を参照。コメント追加時はPowerShell構文解析を行い、コメント・改行を除くトークン列が変更前後で一致することを確認した。説明だけの変更ではMavenを再実行していない。

実行ロジックを修正する場合は、通常UTの保持、生成UTの分離、出力先変更、FAIL後の計測、0件/全件skip、実行ID再利用拒否、保護対象変更検出を優先して検証する。
