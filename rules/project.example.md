# Project Rules（記入用テンプレート）

公開用の空のひな形です。導入先に rules/project.md 等として用意し、ut-kit.json の
projectRules に登録してください。未記入欄は追加制約なしです。
業務上の正解・金額・判定条件はここで定義せず、-D の業務仕様に記載してください。
安全制約・キットの基本ルールは変更できません。詳細はキットの docs/project-rules.md を参照。

各規則に一意なID、適用モード（coverage/spec/review または全モード）、
対象パッケージ・クラス・依存種別、方針、理由を記載してください。
独立した話題は節やファイルを分け、冒頭に適用範囲を示してください。

## Test naming

- 規則ID／適用範囲：
- テストメソッドの命名規則：
- @DisplayName の言語・書式（固定ケースIDは保持）：
- テストクラス名はキット共通の Test 末尾を維持：

## Mock policy

- 規則ID／適用範囲：
- Mock化する依存：
- 実体を利用する純粋な依存：
- 時刻等の固定方法（Clock等）：

## Excluded targets

- 規則ID／適用範囲：
- 除外するクラス・メソッド群と理由：
- 除外をレポートに明示する方法：

## Boundary value policy

- 規則ID／適用範囲：
- 境界ケースの選定方針：
- null・空文字・空白等の区別：

## Domain-specific test policy

- 規則ID／適用範囲：
- ドメイン特有のテスト設計観点：
- 業務期待値を決めず、入力の分類や検証方法だけを記載：

## Additional review rules

- 規則ID／適用範囲：
- 追加レビュー観点：
- レポートで示す根拠・証跡：
