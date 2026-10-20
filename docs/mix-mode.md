# クラス単位の混合実行（-mix -J）

## コマンドと責務

~~~text
ut -P "C:\projects\my-app" -mix com.example.* -J
ut -P "C:\projects\my-app" -mix com.example.* -J -G "*ServiceImpl.java" -O "D:\ut-results"
~~~

-mixはAIによる振り分け・グループ実行。新しい期待値生成方式ではない。
coverage/spec/reviewのいずれかを各クラスに割り当て、既存ルールで実行する。
-P、対象、-Jが必須。-Dとの併用、-J省略、他のモードとの併用は入力エラー。
-G/-Oは既存どおり使用できる。初版は-mix -Dを提供しない。

-Jはspec/reviewグループの契約参照元を指定する。
coverageグループでは実装の観測可能な振る舞いを固定期待値とし、
Javadoc・通常コメントを業務期待値の根拠にしない。
coverageランナーへ--javadoc/-Javadocを渡してはいけない。

## クラス自身の指定

~~~java
/**
 * 予約料金と各種割引を計算する。
 *
 * @ut.mode review
 */
public class ReservationServiceImpl {
    // 公開メソッドの契約は別途記載する。
}
~~~

タグは業務仕様ではなく、キット専用の実行メタデータ。
対象クラス自身に付いたJavadocのブロックタグとして、独立した行に
@ut.mode coverage、@ut.mode spec、@ut.mode reviewのいずれか一つを書く。
前後の空白は許容するが、キー・値は大文字小文字を区別する。
余分な語、値なし、不正値、同一値を含む重複指定はMODE_INVALIDとして当該クラスを保留する。
似た綴りの@ut.mode系タグも無指定へ黙って落とさず、誤記として確認事項にする。

指定がなければcoverage。AIがクラスの複雑さから勝手に変更しない。
継承元・インターフェース・package-info・メソッド・フィールド・通常コメントの指定、
Javadoc本文中の引用やコード例はクラス指定として採用しない。
単純な全文検索で先に見つかったタグを使わず、実際の宣言への所属を確認する。
非対応位置の指定を発見したら無視した位置と理由を報告する。
読取り不能・所属不明は無指定と扱わず当該クラスを保留する。
privateやメソッド単位のモード振り分け、タグの継承は行わない。

明示的な-coverage/-spec/-reviewではメタデータを使用せず、コマンドが指定するモードを使う。
不正タグがあっても単一モード実行の振り分けを妨げない。
タグ自体を契約・期待値やSPEC_UNKNOWNの根拠にはしない。
reviewと指定しただけでは業務契約は増えない。契約不足や矛盾は従来どおり
ケース単位のSPEC_UNKNOWNとし、coverageへ自動で切り替えない。

## Javadoc生成との互換性

独自タグは標準docletに登録しないとエラーになり得る。
JDK 21では、未登録の@ut.modeはエラー、以下の登録で生成成功を実測した。

~~~text
javadoc -tag "ut.mode:t:UT mode:" ...
~~~

これは型用タグの登録例。導入先でJavadocを生成する場合は既存設定を調べ、
同等のカスタムタグ登録をJavadoc生成設定へ追加する導入作業を別途行う。
Mavenプラグインや独自docletの設定形式は導入先の版に従う。
doclintを無効化して問題を隠したり、UT実行中にPOMやソースを書き換えたりしない。
本キットはタグを対象ソースへ自動追記しない。通常のコンパイル成功だけで
Javadoc生成との互換性確認済みとは扱わない。

## 振り分け順

1. 引数・設定・保護条件を確認し、対象パッケージ/クラスと-Gで候補を解決する。
2. 共通Project Rulesの除外を適用する。
3. 残る各クラス自身のタグを解決して、指定モードまたは既定coverageへ振り分ける。
4. 振り分け先モードのProject Rulesを適用する。除外対象を別モードで復活させない。
5. 完全修飾名順の「クラス、選択モード、明示/既定、タグ位置、除外/保留理由」を
   実行前に報告し、実行可能なクラスは通常そのまま続ける。

-G不一致や共通Rules除外クラスのタグを、実行対象を増やす目的で読まない。
MODE_INVALIDはクラス単位の設定不備であり、SPEC_UNKNOWNやJUnit SKIPではない。
有効なクラスは続行する。対象0件・全件除外・全件MODE_INVALIDなら
フォルダもprepareも作らず、最終回答に一覧・理由と未実行/未計測を示す。

## 出力と実行手順

AIが調整し、ランナーの既存prepare/executeをモードごとに使用する。
ランナーへmixを渡したり、run.json.modeを書き換えたりしない。
クラスごとではなく同一モードのクラスを一グループへまとめる。
初版はcoverage → spec → reviewの順に逐次実行する。

~~~text
<outputRoot>/mix/<日時-UUID>/
  report.md
  coverage/<runId>/java/, build/, run.json, summary.json, ...
  spec/<runId>/java/, build/, run.json, summary.json, ...
  review/<runId>/java/, build/, run.json, summary.json, ...
~~~

-Oまたは設定outputRootの配下へ、今回だけのbatchディレクトリをAIが作る。
作成前に出力ルートを正規化し、プロジェクト自身、.git、src、POM、設定、
Rules、追加protectedPaths等の保護範囲と重ならないことを既存prepareと同等に確認する。
保護確認不能なら作成しない。日時とUUIDを使い、既存ディレクトリは再利用しない。
作成・追記はこのbatch配下だけに限定する。

1. batch/report.mdに振り分け一覧と「処理中」を記録する。
2. 空でないグループすべてのprepareを、UT生成・Maven実行より前に行う。
   --output/-Outputはbatchの絶対パス、modeはcoverage/spec/review、
   targetsは各グループの完全修飾名。spec/reviewだけJavadocフラグを付ける。
3. 各prepareのbefore-hashes.jsonを比較し、同じ保護対象集合・ハッシュを持つ
   ことを確認する。途中でソース・設定・Rulesが変化したら全体を停止する。
   最初の基準値をbatchの全期間で維持し、後続prepareで変化を追認しない。
4. 各グループで従来のUT生成・execute・結果確認を行う。
   全ケースSPEC_UNKNOWN/RULE_CONFLICTならそのグループのexecuteは呼ばない。
   一部クラスだけ全件未確定なら対象一覧から黙って消さず未実行として残す。
5. 実行前後・グループ間・全体終了時に保護対象の集合とハッシュを初期値と照合。
   prepare/executeの拒否、例外、summary欠落時も保護確認を省略しない。
6. UT不備の再試行は同一batch下へ新しいprepareで作る。初期基準との一致を
   先に確認する。正当な契約FAILは修正しない。再試行はグループ内で完結させる。
7. batch/report.mdを更新して終了状態・実測・指摘・証跡リンクをまとめる。
   子runのreport.mdは作らず、説明を一箇所へ集約する。
   これはmixのみの規則で、単一モードの出力配置は従来どおり。

グループのテストFAIL、生成UTのコンパイル不備、未計測だけを理由に、
独立した後続グループを止めない。ただし保護対象変更・保護確認不能・
共通設定や実行環境の不備では全体を停止し、残りを未実行とする。
失敗原因不明の場合は安全に継続可能と確認できるまで停止する。
共有本番コードのコンパイル失敗は他グループも影響する共通不備。
summary.statusだけを見て次へ進めず、ログと保護結果で原因を区別する。

## 統合レポート

templates/mix-report.mdを使用し、追加の説明用JSONやreview.mdは作らない。
各子runの機械JSON/XMLは従来どおり保存し、全体用の偽のsummary.jsonは作らない。

- 元のコマンド、batch ID、対象/G、候補・除外・保留・最終対象数。
- 全クラスの選択モード、明示/既定、タグ位置、子run/再試行IDとの対応。
- グループ別の対象数、実行確認済み数、PASS/FAIL/ERROR/SKIP、
  SPEC_UNKNOWN/RULE_CONFLICT件数、未計測・未実行理由。
  coverageのSPEC_UNKNOWNは「対象外」であり、契約確認済み0件と混同しない。
- 最終試行と過去試行を区別し、同じケース/クラスを重複加算しない。
- カバレッジは子runの対象クラス別Line/Branch。グループを跨いで割合や
  covered/missedを合算せず、依存として通過したクラスを別グループの成果にしない。
- FAIL等の詳細は個別に一度だけ記載。原因分析・修正案はreviewの指摘に限る。
- 未完了/部分完了/完了と、テスト結果・契約未確定・設定不備を別軸で示す。
  coverageのPASSを業務契約の適合と表現せず、全体を単純なPASSにしない。
- 全体の経過時間を一度だけ計測。各runのMaven時間と起動回数は全試行を含める。
  グループ時間と全体時間を足さない。未実行の起動回数を推測しない。
- 保護確認、停止理由、未実行グループ、契約参照範囲と証跡への相対リンク。
