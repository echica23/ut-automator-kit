package kit;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @TempDir Path dir;

    Main fixture(String runner) throws Exception {
        Files.createDirectories(dir.resolve("src/main/java/example"));
        Files.writeString(dir.resolve("src/main/java/example/Foo.java"), "package example; public class Foo {}");
        Files.writeString(dir.resolve("pom.xml"),
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><build>" +
            "<testSourceDirectory>$" + "{ut.sources}</testSourceDirectory><directory>$" + "{ut.build}</directory>" +
            "</build><profiles><profile><id>ut-automator</id></profile></profiles></project>");
        JsonObject config = Main.obj("schemaVersion", 1, "profile", "ut-automator", "outputRoot", "output",
                "maven", "mvn", "protectedPaths", List.of("src"));
        if (runner != null) {
            config.addProperty("runner", runner);
        }
        Main.write(dir.resolve("ut-kit.json"), config);
        return new Main("prepare", "--project", dir.toString(), "--mode", "coverage", "--targets", "example.Foo");
    }

    @Test void missingRunnerNeverSelectsJavaImplicitly() throws Exception {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> fixture(null)).getMessage().contains("powershell"));
    }

    @Test void invalidRunnerRejected() {
        assertThrows(IllegalArgumentException.class, () -> fixture("unknown"));
    }

    @Test void prepareRecordsSelectionAndDoesNotOverwriteRuns() throws Exception {
        Main runner = fixture("java");
        runner.prepare();
        runner.prepare();
        try (var runs = Files.list(dir.resolve("output/coverage"))) {
            var paths = runs.toArray(Path[]::new);
            assertEquals(2, paths.length);
            for (Path path : paths) {
                JsonObject manifest = Main.read(path.resolve("run.json"));
                assertEquals("java", manifest.get("runner").getAsString());
                assertEquals("implementation", manifest.get("specSource").getAsString());
                assertFalse(Files.exists(path.resolve("execution-started.json")));
                assertTrue(Main.read(path.resolve("before-hashes.json")).has(dir.resolve("pom.xml").toString()));
            }
        }
    }

    @Test void outputCannotOverlapProtectedFiles() throws Exception {
        fixture("java");
        Main runner = new Main("prepare", "--project", dir.toString(), "--mode", "coverage",
                "--targets", "example.Foo", "--output", "src/output");
        assertThrows(IllegalArgumentException.class, runner::prepare);
    }

    @Test void javadocAndDocumentAreExclusive() throws Exception {
        fixture("java");
        Main runner = new Main("prepare", "--project", dir.toString(), "--mode", "spec",
                "--targets", "example.Foo", "--javadoc", "--spec", "does-not-exist");
        assertTrue(assertThrows(IllegalArgumentException.class, runner::prepare).getMessage().contains("mutually exclusive"));
    }

    @Test void malformedAndRepeatedOptionsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Main.parse(new String[]{"prepare", "--project"}));
        assertThrows(IllegalArgumentException.class, () -> Main.parse(new String[]{"execute", "--wat", "x"}));
        assertThrows(IllegalArgumentException.class, () -> Main.parse(new String[]{"prepare", "--project", "a", "--project", "b"}));
    }

    @Test void snapshotDetectsAddChangeAndDelete() throws Exception {
        Files.writeString(dir.resolve("a"), "before");
        JsonObject before = Main.snapshot(dir, List.of("."));
        Files.delete(dir.resolve("a"));
        Files.writeString(dir.resolve("b"), "after");
        assertEquals(2, Main.changes(before, Main.snapshot(dir, List.of("."))).size());
    }

    @Test void externalDtdIsNotRequiredAndCdataIsPreserved() throws Exception {
        Path xml = dir.resolve("report.xml");
        Files.writeString(xml, "<!DOCTYPE report SYSTEM \"absent.dtd\"><report><system-out><![CDATA[日本語\r\n]]></system-out></report>");
        assertEquals("日本語\n", Main.text(Main.xml(xml).getDocumentElement(), "system-out"));
    }

    @Test void externalEntityDoesNotExposeLocalFile() throws Exception {
        Path secret = dir.resolve("secret.txt");
        Files.writeString(secret, "not-for-xml");
        Path xml = dir.resolve("entity.xml");
        Files.writeString(xml, "<!DOCTYPE report [<!ENTITY x SYSTEM \"" + secret.toUri() +
                "\">]><report><system-out>&x;</system-out></report>");
        assertFalse(Main.text(Main.xml(xml).getDocumentElement(), "system-out").contains("not-for-xml"));
    }

    @Test void jsonRoundTripRetainsArraysNullAndUnicode() throws Exception {
        Path json = dir.resolve("設定.json");
        JsonObject value = Main.obj("empty", List.of(), "one", List.of("日本語"), "unknown", null);
        Main.write(json, value);
        assertEquals(value, Main.read(json));
        byte[] bytes = Files.readAllBytes(json);
        assertEquals('{', bytes[0]);
        Files.writeString(json, "\uFEFF" + Files.readString(json), StandardCharsets.UTF_8);
        assertEquals(value, Main.read(json));
    }

    @Test void aggregationDistinguishesTestSuccessFromMissingCoverage() throws Exception {
        Main runner = fixture("java");
        Path run = dir.resolve("run");
        Files.createDirectories(run.resolve("build/surefire-reports"));
        Files.writeString(run.resolve("build/surefire-reports/TEST-Foo.xml"),
                "<testsuite name=\"example.FooTest\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\">" +
                "<testcase name=\"case01\" classname=\"example.FooTest\"><system-out>CASE-01</system-out></testcase></testsuite>");
        int code = runner.aggregate(run, Main.obj("runId", "test", "mode", "coverage", "targets", List.of("example.Foo")),
                List.of("example.FooTest"), 0, 0, List.of(), null, Main.obj());
        assertEquals(2, code);
        JsonObject summary = Main.read(run.resolve("summary.json"));
        assertEquals("PASS", summary.get("status").getAsString());
        assertEquals("example.Foo", summary.getAsJsonArray("missingCoverage").get(0).getAsString());
    }
}
