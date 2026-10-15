# 導入調査・設定プロンプト

## Git操作は人間が担当する

このキットの導入・実行・保守では、Git操作を必ず人間が行う。AIはgitコマンドを実行しない。status/diff/log等の読み取り、init/clone/fetch/pull、add/commit/push、ブランチ・履歴操作、Git設定変更を含む。特にAIによるcommit・pushは禁止する。GitHub等のAPI・連携ツール・別スクリプトへ迂回して同じ操作を代行しない。

Git操作が必要な場面では、AIは変更ファイル・検証結果・必要な手順を説明して人間へ引き継ぐ。導入やutの依頼をGit操作の許可と解釈しない。変更確認にはファイル集合とSHA-256を使う。Git操作を実行するための承認要求を出して代行する運用にもせず、人間が実行する。

このキットを既存Javaプロジェクトへ導入する。ユーザー指定の絶対パスだけを対象にする。最初は変更せず、ルート/対象モジュール、AGENTS.md、Java版、Maven Wrapper/PATH/保存済みMaven、親POM、依存関係、既存UT、Surefire/Failsafe/JaCoCo、argLine、ソース/リソースディレクトリ、test fixtures、並列実行設定を調べる。秘密情報をレポートへ転記しない。

初版の対応はREADMEに従う。リアクタ依存を単独モジュールと誤認しない。対象が複数候補なら確認する。設定不足は具体的に説明する。既存プロジェクトのpom.xml全体をサンプルで置き換えない。

導入計画には、変更する要素、現在の通常UTの実行方法と結果、生成UTの分離方法、バージョン整合、復元方法、検証方法を示す。ユーザーがすでに導入変更まで依頼しているなら重複確認は不要。調査だけの依頼なら適用しない。

templates/maven-integration.mdを参考に、明示指定時だけ有効なut-automatorプロファイルを追加する。通常UTのソースルート/出力ルートをプロパティ化し、既定値は従来の値のままにする。生成UT実行時だけut.sources、ut.buildが置き換わる。既存プロファイルとの競合、Surefire argLineとJaCoCoエージェントの重複、スキップ設定を確認する。

ut-kit.jsonを作り、runner/Java/Maven/出力先/追加保護対象を記録する。新規導入のrunnerはjavaを既定とし、明示的にPowerShellを選ぶ場合はpowershellとする。既存設定でrunner省略ならPowerShellを維持し、勝手に切り替えない。MavenがPATHにない場合はユーザーが指定した保存先を探す。グローバルGitやMaven設定、settings.xmlを勝手に変更しない。

Project Rulesは任意。キットのdocs/project-rules.mdを読み、利用者が指定したテスト方針がある場合は
rules/project.example.mdを参考に導入先のrules/project.md等へ記載し、ut-kit.jsonのprojectRules配列へ登録する。
既存のルールは保持し、固有方針を推測で作らない。未指定なら省略または空配列とし、追加確認だけで導入を止めない。
業務仕様の値をRulesへ移さず、公開キットへ実プロジェクト情報を転載しない。
登録パスの実在・可読性、適用範囲、必須制約との矛盾を確認する。設定例全体で既存設定を置き換えない。
試運転ではproject-rules.jsonの解決パス・ハッシュと、report.mdのルール適用概要・指摘を確認し、
命名等の代表的な適用結果をUT・レポートと照合する。導入記録に登録ファイルと未解決の矛盾を記載する。

設定後は通常UTを実行し、導入前との結果と件数を比較する。生成UTを1件以上で試運転し、既存UTが混入しないこと、指定された出力先でのみ結果が生成されることを確認する。テスト失敗時の計測も確認する。対象アプリの起動や実DB接続を必要とする構成を無断で実行しない。

最後に導入した設定、適用可能なコマンド、検証結果、未対応事項を対象プロジェクトのut-kit-setup.mdへ保存する。導入済みでも将来のビルド設定変更後は再確認する。

特定エージェントの内部ログやトークン取得は導入条件にしない。ファイル操作と選択した実行基盤/Mavenの起動の可否を確認する。試運転ではreport.mdへの説明集約、成功ケースの集計、問題の個別記載、Maven時間を確認する。

仕様資料が用意できない場合はspec/reviewの-Jを案内できる。-Dとの併用や暗黙の代替はせず、docs/javadoc-mode.mdの契約参照・SPEC_UNKNOWN・保証範囲に従う。新たな設定やJavadocの自動追記は不要。

## 実行環境・Java互換性の確認

まずdocs/runner-spec.mdで方式を確認する。Java版は同梱JARとJava 11以上を確認し、java.exeの絶対パスを導入記録へ残す。JAR起動にPowerShellスクリプトは使わない。対象MavenのjavaHomeとは別に確認する。失敗時の自動切替はしない。

PowerShell版の場合、現在のシェルだけで判断せず、$PSVersionTableとGet-Command powershell,pwshで利用可能な実行ファイルを確認する。
Windows PowerShell 5.1またはPowerShell 7を使用できる。選んだ実行ファイルの絶対パスと版を導入記録に残し、
実行時はそのホストを-NoProfile -Fileで明示する。既定ターミナル変更やPowerShell追加導入は不要。
スクリプトはUTF-8 BOM付きで保持する。設定JSON・生成JavaはUTF-8を使用する。
実行ポリシー等で起動できない場合は理由と必要な環境対応を提示し、勝手にポリシーを変更しない。

Java互換性は次を分けて調査する。
- java -versionとmvn -version（設定したJAVA_HOMEで実行）による実際のJDK。
- POM/親POM/プロファイルのcompiler release/source/target、ToolchainsとSurefireのJVM指定。
- 実効的なJaCoCoの版と、JUnit・Surefire・Mockito/Byte Buddy等の版。

Java 25の正式対応はJaCoCo 0.8.14から（公式リリース参照）。
https://github.com/jacoco/jacoco/releases/tag/v0.8.14
Java 25では0.8.14以降の対応版を候補とし、既存設定・社内取得可能な版との整合を確認する。
テンプレートの旧版を無条件にコピーしない。未知のJava版も公式対応情報を確認する。
ネットワークや社内リポジトリ制限で確認できなければ「確認不能」と「非対応」を区別し、
不足情報・候補版・必要な変更・試運転方法を提示する。

JaCoCoだけで環境全体の互換性を保証しない。通常UTと生成UTの小規模試運転で、
Mock利用、正当なFAIL後の計測、Surefire XML・JaCoCo HTML/XML/execの生成を確認する。
依存更新は導入時の変更案に含める。UT実行中に依存や本番Java版を勝手に変更しない。

日本語を含む出力先では、Maven/Surefire/JDKの組合せによりJaCoCoのdestfileが文字化けする場合がある。
試運転でexecとHTML/XMLが指定先にない場合はログと実際の保存先を確認し、英数字の出力先を提案する。
テスト成功だけで計測成功とは報告しない。出力先変更はoutputRoot/-Oを使い、既存結果を移動・削除しない。
