# UT混合実行レポート

## 実行概要

元の-mix -Jコマンド、batch ID、対象/G、出力先、runner、開始/終了/経過時間。
完了/部分完了/未完了、停止理由。coverageは業務契約の適合を保証しない。
候補数、glob一致数、Rules除外数、MODE_INVALID保留数、最終対象数を区別する。

## クラスの振り分け

| 完全修飾クラス | モード | 明示/既定 | タグ位置 | 除外/保留理由 | 子run・再試行ID | 実行確認 |
| --- | --- | --- | --- | --- | --- | --- |

指定漏れを確認できるよう、既定coverageも明記する。成功テスト個別一覧は不要。

## グループ別結果

| モード | 対象/実行確認済みクラス数 | テスト成功/FAIL/ERROR/SKIP | SPEC_UNKNOWN/RULE_CONFLICT | 未実行・未計測 |
| --- | --- | --- | --- | --- |
| coverage | | | SPEC_UNKNOWNは対象外 | |
| spec | | | | |
| review | | | | |

空グループは対象なし、全ケース未確定なら未実行・未計測。
最終試行を集計し、再試行履歴は別記して重複加算しない。
機械summaryの状態と、契約評価・MODE_INVALID・全体完了状態を混同しない。

| モード・子run | 対象クラス | Line covered/missed/% | Branch covered/missed/% |
| --- | --- | --- | --- |

全体の割合に合算しない。未計測と分母0（N/A）を区別する。

## 指摘・確認事項

FAIL/ERROR/SKIP/SPEC_UNKNOWN/RULE_CONFLICT/MODE_INVALID/未カバー/未計測/除外。
クラス・モード・固定ケースID・入力・固定期待値・実測・根拠・証跡を対応付ける。
MODE_INVALIDはタグ位置・値・理由を記録し、JUnit件数に含めない。
reviewの指摘のみ原因・箇所・確度・未適用の修正案を追加する。
他グループの成功で問題を相殺せず、正当なFAILを維持する。

## 参照・保護・実行証跡

spec/reviewの契約参照範囲、Javadoc/補助コメントの採否、Project Rules適用概要。
各子runの生成UT、summary、change-check、Surefire、JaCoCo、必要ログへのリンク。
初期基準との保護照合結果、未実行グループ、制約と再試行理由。
全試行のMaven test/coverage時間・起動回数。全体時間との二重加算をしない。
