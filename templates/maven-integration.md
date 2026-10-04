# Maven導入テンプレート

これは編集ガイドです。既存pom.xmlへの自動パッチではありません。バージョン例はreservation-demoで実行済みの組合せであり、最新版の推奨ではありません。既存の依存管理を優先してください。

プロジェクト直下のproperties/buildを調整する例:

```xml
<properties>
  <ut.sources>src/test/java</ut.sources>
  <ut.build>target</ut.build>
</properties>
<build>
  <testSourceDirectory>${ut.sources}</testSourceDirectory>
  <directory>${ut.build}</directory>
  <!-- 既存plugins、resources等は維持 -->
</build>
```

通常実行のソース/出力が上記と異なるなら既存値を既定値にします。プロファイルのbuild内へtestSourceDirectoryを置かず、プロジェクトのbuildでプロパティを参照します。

明示有効化するプロファイルの例:

```xml
<profile>
  <id>ut-automator</id>
  <dependencies>
    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>5.11.4</version><scope>test</scope></dependency>
    <dependency><groupId>org.mockito</groupId><artifactId>mockito-junit-jupiter</artifactId><version>4.11.0</version><scope>test</scope></dependency>
  </dependencies>
  <build><plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.5.2</version>
      <configuration><failIfNoTests>true</failIfNoTests><useModulePath>false</useModulePath></configuration>
    </plugin>
    <plugin>
      <groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId><version>0.8.12</version>
      <configuration><append>false</append></configuration>
      <executions><execution><id>ut-kit-agent</id><goals><goal>prepare-agent</goal></goals></execution></executions>
    </plugin>
  </plugins></build>
</profile>
```

既存JUnit5/Mockito依存があるなら二重追加しません。既存JaCoCoがあるなら二重エージェントを避けて既存設定を統合します。Surefireに独自argLineがある場合は、JaCoCoが設定するargLineを消さない設定が必要です。Java版とJaCoCo/Mockitoの対応は対象環境で確認してください。

試運転ではbuild/surefire-reports、build/site/jacoco/jacoco.xml、build/jacoco.execが実行フォルダ内に作成されることを確認します。非標準レポート位置や他のプラグインがソース追加する構成は、このまま適用せず導入設計を修正する必要があります。
