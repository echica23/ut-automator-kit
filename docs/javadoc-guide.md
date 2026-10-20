# UT自動生成に役立つJavadoc・コメントの書き方

## 目的と保証範囲

具体的な入力に対して期待結果を決められる契約を、少ない記述で残すためのガイドです。
主に公開メソッドを対象とし、重要な業務判断や境界・例外を持つものから整備します。
全メソッドの詳細化や、全テストケースの列挙は不要です。

-JはJavadocを第一、通常コメントを補助として読みます。
検証するのは記載された契約への適合であり、その契約が最新の業務仕様であることや、
未記載の要件を満たすことまで保証しません。
実装例は理解の補助です。spec/reviewの期待値は実装から補完しません。
判断規則は[Javadocモード](javadoc-mode.md)を参照してください。

## 最低限書くこと

| 観点 | 必要な内容 |
| --- | --- |
| 役割 | 何をするかを1～2文で説明 |
| 入力 | 有効範囲、必要に応じてnull・空文字の扱い |
| 結果 | 入力から具体的な期待結果を決められる条件 |
| 境界・例外 | 閾値を含むか、不正入力に対する動作 |
| 該当する場合の補足 | 丸め、日時基準、検索結果なし、順序、副作用 |

全項目を埋める必要はありません。そのメソッドの結果を左右する内容だけ書きます。
入力範囲だけでは範囲外の動作は決まりません。例外を期待する場合は種類と条件も書きます。

~~~java
/**
 * 役割と、結果を決める条件。
 * 必要な境界・補足。
 *
 * @param input 入力の意味と制約
 * @return 結果と、必要な未検出時の扱い
 * @throws SomeException 発生条件
 */
~~~

不要なタグは省略します。

## 例1：金額計算

### 悪い例

~~~java
/**
 * 割引後の金額を計算する。
 * @param amount 金額
 * @return 割引後の金額
 */
public long discountedAmount(long amount) {
    if (amount < 0) {
        throw new IllegalArgumentException("amount must be non-negative");
    }
    if (amount < 10_000) {
        return amount;
    }
    return BigDecimal.valueOf(amount)
            .multiply(new BigDecimal("0.9"))
            .setScale(0, RoundingMode.DOWN)
            .longValueExact();
}
~~~

割引条件・率・丸め・負数の扱いをJavadocから決められません。
実装を読めば分かることと、契約を根拠に検証できることは別です。

### 十分な例

~~~java
/**
 * 購入金額が10,000円以上の場合、10%引きした金額を返す。
 * 10,000円未満の場合は購入金額をそのまま返す。
 * 割引後の1円未満は切り捨てる。
 *
 * @param amount 購入金額（円、0以上）
 * @return 割引後の金額（円）
 * @throws IllegalArgumentException amountが負の場合
 */
public long discountedAmount(long amount) {
    if (amount < 0) {
        throw new IllegalArgumentException("amount must be non-negative");
    }
    if (amount < 10_000) {
        return amount;
    }
    return BigDecimal.valueOf(amount)
            .multiply(new BigDecimal("0.9"))
            .setScale(0, RoundingMode.DOWN)
            .longValueExact();
}
~~~

BigDecimal、RoundingModeはjava.mathの型です。以下のUTを導出できます。

| 観点 | 入力 | 固定期待値 |
| --- | ---: | --- |
| 下限 | 0 | 0 |
| 閾値直前 | 9,999 | 9,999 |
| 閾値一致 | 10,000 | 9,000 |
| 端数 | 10,001 | 9,000 |
| 通常 | 20,000 | 18,000 |
| 不正入力 | -1 | IllegalArgumentException |

~~~java
@Test
void thresholdAmountIsDiscounted() {
    assertEquals(9_000L, service.discountedAmount(10_000L));
}
~~~

本番と同じ式で期待値を計算したり、戻り値を期待値へ流用したりしません。

## 例2：null・空文字・長さ

「表示名が有効ならtrue」だけでは、有効の条件が分かりません。

~~~java
/**
 * nullでも空白だけでもなく、長さが1～20の場合にtrueを返す。
 * 空文字はfalseとする。前後の空白は除去せず、長さに含める。
 * 空白判定はString.isBlank()、長さはString.length()に従う。
 *
 * @param name 表示名。nullを許容する
 * @return 条件をすべて満たす場合true、それ以外はfalse
 */
public boolean isValidDisplayName(String name) {
    return name != null
            && !name.isBlank()
            && name.length() <= 20;
}
~~~

null、空文字、空白のみは各false、"A"と"A"20個はtrue、21個はfalseです。
" A "はtrueです。「長さ」はこの例の契約としてJavaの定義を選んでいます。
人間が見た文字数を要件とする場合は、要件に合う定義にしてください。
既存実装を正当化するために説明を合わせるのではなく、意図した契約を記述します。

## 例3：日時依存

「期限切れか判定する」だけでは、期限一致時の結果や現在時刻の基準が不明です。

~~~java
public final class ExpirationService {
    private final java.time.Clock clock;

    public ExpirationService(java.time.Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    /**
     * コンストラクタで渡されたClockの現在時刻が、
     * 期限と同じか、それより後ならtrueを返す。
     *
     * @param expiresAt 期限
     * @return 期限到達済みならtrue、それより前ならfalse
     * @throws NullPointerException expiresAtがnullの場合
     */
    public boolean isExpired(java.time.Instant expiresAt) {
        java.util.Objects.requireNonNull(expiresAt, "expiresAt");
        return !clock.instant().isBefore(expiresAt);
    }
}
~~~

Clockを固定し、期限直前はfalse、一致・直後はtrueと検証できます。
「テストではClockを固定する」はProject Rulesのテスト方針、
「期限と同時刻なら失効」は契約側の記載です。

## 例4：通常コメントを補助根拠にする

Javadocへの書換えを必須にせず、明確な契約が通常コメントにあれば採用できます。

~~~java
/**
 * 割引後の購入金額を返す。
 * @param amount 購入金額（円、0以上）
 * @return 割引後の金額（円）
 * @throws IllegalArgumentException amountが負の場合
 */
public long discountedAmount(long amount) {
    // 購入金額が10,000円以上の場合は10%引き、それ未満は割引しない。
    // 割引後の1円未満は切り捨てる。
    if (amount < 0) {
        throw new IllegalArgumentException("amount must be non-negative");
    }
    if (amount < 10_000) {
        return amount;
    }
    return BigDecimal.valueOf(amount)
            .multiply(new BigDecimal("0.9"))
            .setScale(0, RoundingMode.DOWN)
            .longValueExact();
}
~~~

通常コメントに業務条件と結果が明記されているため、例1と同じ期待値を決められます。
生成UTにコメントの参照位置を残し、report.mdに補助コメントの採用を記録します。
Javadocがない場合も、明確なコメントの契約だけで確定できるケースは生成可能です。

以下は期待値の根拠にしません。

~~~java
// amount >= 10000ならこの分岐へ入る。
// BigDecimalに変換して0.9を掛ける。
// TODO: 将来は10%割引に変更する。
// おそらく10,000円ちょうども対象。
~~~

前二つは処理手順、後二つは変更予定・推測です。
記載場所や「仕様」という単語だけで採否を決めず、現在の公開動作の契約かを判断します。
判断できなければ、そのコメントを使って期待値を確定しません。

## 例5：Javadocとコメントの矛盾

~~~java
/**
 * 10,000円以上の場合は10%引き、それ未満は元の金額を返す。
 * 割引後の1円未満は切り捨てる。
 */
public long discountedAmount(long amount) {
    // 10,000円ちょうどは割引対象外。それを超える場合に10%引きする。
    if (amount <= 10_000) {
        return amount;
    }
    return BigDecimal.valueOf(amount)
            .multiply(new BigDecimal("0.9"))
            .setScale(0, RoundingMode.DOWN)
            .longValueExact();
}
~~~

| 入力 | 判断 |
| --- | --- |
| 9,999 | 9,999で一致するため生成可能 |
| 10,000 | 9,000と10,000が競合するためSPEC_UNKNOWN |
| 20,000 | 18,000で一致するため生成可能 |

Javadoc優先だからといって矛盾を無視しません。
実装がコメントに一致していても、コメントが正しいとは決めません。
更新日時や記載位置から正しい方を推測することも避けます。

未確定ケースには固定IDを付け、双方の位置・主張、入力条件、
決められない期待値、確認事項をreport.mdへ記載します。
JUnitの@Disabledやskipにはせず、影響しないケースは継続します。
通常コメント同士やJavadoc同士の契約矛盾も同じ方針です。

## 不足がある場合

例1から丸めと例外の記載を除いた場合、9,999円・10,000円は確定できます。
10,001円の端数処理と、入力範囲外の-1円の動作はSPEC_UNKNOWNです。
不足を実装やProject Rulesから埋めません。
全ケース未確定なら実行せず、未実行・カバレッジ未計測と報告します。

## 記述コストを抑える

- メソッド名や型から明らかな説明を繰り返さず、重要な境界・例外を優先する。
- if文や変換の順番を文章にせず、利用者から見た条件と結果を書く。
- 共通契約はクラスやインターフェースに集約し、参照先を明確にする。
- 契約をJavadocと通常コメントへ重複転記しない。変更時に食い違いが生じやすい。
- privateメソッドまで一律に詳細化しない。UTも公開入口から検証する。
- 命名・Mock方針はProject Rulesへ、詳細な設計書がある場合は-Dで参照する。

実装を読まず、代表値・境界・重要な異常入力の期待結果を決められるか確認し、
答えられない部分だけを補います。

## 利用方法

~~~text
ut -P "C:\projects\my-app" -spec com.example.PriceService -J
ut -P "C:\projects\my-app" -review com.example.PriceService -J
~~~

-Dと-Jは併用しません。今回のコメント補助参照は-Jに限り、
-Dの根拠資料やcoverageの期待値方針は変更しません。
成功ケースは集計し、FAIL・SPEC_UNKNOWNなどの問題を個別に説明します。

## 必要なクラスだけ契約を照合する

-mix -Jなら無指定クラスはcoverage、クラス自身のJavadocの@ut.mode specまたは
@ut.mode reviewで契約照合へ振り分けられます。これは業務契約ではなく実行設定です。
タグだけでは期待値は決まりません。契約不足はSPEC_UNKNOWNとなり、
coverageへ自動で切り替わりません。メソッド単位・継承は非対応です。
Javadoc生成には独自タグの登録が必要です。[混合実行の仕様](mix-mode.md)を参照してください。
