# 実行方式の選択と共通契約

## 設定と互換性

ut-kit.jsonのrunnerは"java"または"powershell"。省略時のみpowershellとする。
null・空文字・未知の値は設定エラー。新規導入テンプレートはjava。
schemaVersion=1を維持し、既存設定は自動変更しない。

AIは設定を読んで該当する実行基盤だけを呼ぶ。方式は毎回のut引数には追加しない。
両実装も設定を検査し、設定と違う方式での呼出しを拒否する。
JAR不足、Java不足、実行ポリシー、起動失敗があっても他方式へ自動切替しない。
必要な導入変更を報告し、方式変更は別の導入/保守依頼として行う。

run.jsonにrunnerを記録する。runner省略の過去manifestはpowershell。
prepareとexecuteを異なる方式で混用しない。方式変更後は新しいprepareから実行する。
旧manifestをJava用へ書き換える運用は行わない。

## Java版（新規導入の既定）

dist/ut-automator-runner.jarを配布する。Gsonを同梱し、起動時の依存ダウンロードは不要。
対象プロジェクトのPOMにこのJARやGsonの依存を追加しない。
Java 11以上が必要（--release 11でビルド）。実測したJava版はvalidation.mdを参照。
補助ツール用java.exeの絶対パスを導入記録に残す。
対象MavenのJavaは既存設定javaHomeで別途指定する。古いJDKの対象でも補助ツール用Javaは必要。
補助ツール用Javaは設定済みjavaHomeが条件を満たせば共用できるが、無確認で同一視しない。

以下はPowerShellからの例だが、.ps1やPowerShellの機能を利用する起動ではない。

```powershell
& "<補助ツール用java.exe>" -jar "<キット>/dist/ut-automator-runner.jar" prepare --project "<対象>" --mode spec --targets com.example.Service --spec docs/
& "<補助ツール用java.exe>" -jar "<キット>/dist/ut-automator-runner.jar" prepare --project "<対象>" --mode review --targets com.example.Service --javadoc
& "<補助ツール用java.exe>" -jar "<キット>/dist/ut-automator-runner.jar" execute --project "<対象>" --run "<prepareで得たパス>"
```

| Java引数 | PowerShell引数 | 共通の意味 |
| --- | --- | --- |
| prepare / execute | -Action | 処理段階 |
| --project | -Project | 対象モジュール |
| --mode | -Mode | coverage/spec/review |
| --targets A B | -Targets配列 | 解決済み完全修飾クラス一覧 |
| --spec | -Spec | -Dのファイル/ディレクトリ |
| --javadoc | -Javadoc | -J、値なし |
| --output | -Output | -O、設定outputRootより優先 |
| --run | -Run | execute対象 |
| --config | -Config | 省略時ut-kit.json。相対はproject基準 |

Java版は--help/--versionも使用可能。引数は値ごとに分離して渡す。
実行用引数名は自然言語utコマンドとは異なる。

## PowerShell版（継続利用可能）

scripts/Invoke-UtRun.ps1をWindows PowerShell 5.1/PowerShell 7で実行する。
設定省略の既存環境はこれまでどおり。実行ポリシーを自動変更しない。
詳細は[PowerShell仕様](script-spec.md)を参照。

## 両方式で維持するもの

- prepare/execute分離、新規実行ID、再利用拒否、生成UTと既存UTの分離。
- 設定、src、POM、仕様、Rules等の集合とSHA-256による変更検出。
- coverage/spec/review、-D/-J排他、Project Rules、出力先選択。
- 正当なFAIL後のJaCoCo呼出し、0件/全件skipを成功としない判定。
- 既存JSON項目・意味と終了コード0/1/2。先行検査の失敗ではsummaryがない場合がある。
- summaryのPASSは計測成功や業務適合を意味しない。詳細は既存スクリプト仕様の集計規則。
- report.mdはAIが作成。SPEC_UNKNOWN、業務期待値、Rulesの意味は実行基盤が判定しない。
- 本番コード、既存UT、仕様、設定をUT中に編集しない。Git操作・公開・過去成果物削除をしない。
- private methodの直接テスト、実DB接続、FAILを消す期待値変更は引き続き禁止。

JSONの空白・キー順、実行ID、時刻、時間、絶対パスはバイト一致を要求しない。
件数・状態・対象別カバレッジ・固定IDを含む本文・保護結果を意味で比較する。
Java版ログは子プロセスの生バイトを保存する。JSON/JavaはUTF-8、XMLは宣言に従う。
PowerShell版のUTF-8ログと同じ文字コードとは限らない。ログが文字化けした場合は生成元の
文字コードを確認し、元ファイルを勝手に書き換えない。

## Maven起動と制約

Java版はProcessBuilderを使用する。JAVA_HOME・作業ディレクトリは子プロセスにだけ設定。
Windowsのmvn.cmd/mvnw.cmdはcmd.exe /d /v:off /s /c経由で起動する。
PowerShell実行ポリシーには依存しないが、Java・cmd・Mavenが利用可能な環境を前提とする。
自動的にPowerShellを子プロセスとして起動しない。

空白を含む引数は引用する。cmdの展開で値が変わる%、!、引用符、改行を含む引数は
Java版では安全に受け渡せないためMaven起動前に拒否し、設定・パスの変更が必要と報告する。
既存の日本語出力先とMaven/Surefireの問題はJava化で解消したと扱わない。
まず英数字の出力先で試運転する。

出力先の検査は正規化したパス文字列による。シンボリックリンクやジャンクションの実体境界、
悪意あるmanifest改ざんを防ぐサンドボックスではない。
Java版は実行開始マーカーを排他的に作成するが、同じrunを並列実行する運用はしない。
内部クラス・集約JaCoCo・親POMだけの導入設定・非標準ソース配置の自動対応は追加しない。

## ビルド・配布と検証

Javaソースとテストはrunner/。利用者は同梱JARを使い、再ビルドは不要。
保守時はJDK 11以上とMavenで以下を実行する（実測環境は検証記録を参照）。

```text
mvn -f runner/pom.xml clean verify
```

成功後、runner/target/ut-automator-runner.jarをdist/へコピーしSHA-256も更新する。
ソース変更だけで古いJARを配布しない。依存の取得はビルド時のみ必要。
Gsonのライセンスと出典はdist/THIRD-PARTY-NOTICES.mdとJAR内META-INF/licenses/に保持する。
Java版の公開ユニットテストに加え、同じMaven fixtureを両方式で実行して
成功・FAIL・ERROR・未計測・保護・Rules・Javadoc・引数境界を比較する。
## 対象フィルターとの境界

自然言語utの-GはAIがprepare前に解決する。
--targets/-Targetsには具体的な完全修飾名だけを渡し、-Gやglobを渡さない。
run.json.targetsは最終対象。元の要求範囲とフィルターはreport.mdに記録する。
[対象選択の仕様](target-selection.md)を参照。

## 混合実行との境界

-mixはAIが既存coverage/spec/reviewのprepare/executeへ分解する。
mode=mixはランナーに渡さない。coverageはJavadocフラグなし、
spec/reviewはJavadocフラグあり。--output/-Outputは新しいbatchディレクトリ。
機械JSONのmode/specSourceや終了コードは変更せず、全体の意味付けはAIが行う。
[混合実行仕様](mix-mode.md)の事前prepareと全期間の保護照合に従う。
