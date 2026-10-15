package kit;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Standalone helper. Test design and final reports remain the AI's responsibility. */
public final class Main {
    static final Gson JSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create();
    static final boolean WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    static final Pattern FQCN = Pattern.compile("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+");
    static final Pattern TEST = Pattern.compile("@(?:ParameterizedTest|Test|RepeatedTest|TestFactory|TestTemplate)\\b");
    static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    final Map<String, List<String>> options;
    final Path root, config;
    final JsonObject settings;
    final List<String> protectedPaths = new ArrayList<>();
    final JsonArray rules = new JsonArray();

    public static void main(String[] args) {
        try {
            if (args.length == 1 && (args[0].equals("--help") || args[0].equals("--version"))) {
                System.out.println(args[0].equals("--version") ? "ut-automator-runner 1.0.0" :
                    "prepare --project PATH --mode coverage|spec|review --targets FQCN [FQCN...] " +
                    "[--spec PATH | --javadoc] [--output PATH] [--config PATH]\n" +
                    "execute --project PATH --run PATH [--config PATH]");
                return;
            }
            System.exit(new Main(args).run());
        } catch (Exception e) {
            System.err.println("UT runner: " + e.getMessage());
            System.exit(2);
        }
    }

    Main(String... args) throws Exception {
        options = parse(args);
        root = Paths.get(required("project")).toRealPath();
        config = resolve(root, value("config", "ut-kit.json"));
        settings = read(config);
        require(settings.has("schemaVersion") && settings.get("schemaVersion").getAsInt() == 1,
                "Unsupported config schema.");
        String runner = string(settings, "runner", "powershell");
        require(runner.equals("java"), "Runner mismatch: configured runner is " + runner + "; no automatic fallback.");
        Path pomPath = root.resolve("pom.xml");
        require(Files.isRegularFile(pomPath), "pom.xml missing; Maven only.");
        Element pom = xml(pomPath).getDocumentElement();
        Element build = child(pom, "build");
        require(("$" + "{ut.sources}").equals(text(build, "testSourceDirectory")) &&
                ("$" + "{ut.build}").equals(text(build, "directory")), "Setup incomplete: ut.sources and ut.build required.");
        require(children(child(pom, "profiles"), "profile").stream()
                .anyMatch(p -> text(p, "id").equals(string(settings, "profile", ""))),
                "Configured opt-in profile is missing from module POM.");
        protectedPaths.addAll(strings(settings.get("protectedPaths")));
        protectedPaths.addAll(Arrays.asList("src", "pom.xml", ".mvn", config.toString()));
        JsonElement entries = settings.get("projectRules");
        if (entries != null && !entries.isJsonNull()) {
            require(entries.isJsonArray(), "projectRules must be an array of Markdown file paths.");
            Set<String> seen = new HashSet<>();
            for (JsonElement entry : entries.getAsJsonArray()) {
                require(entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString() &&
                        !entry.getAsString().isBlank(), "Each projectRules entry must be a non-empty string.");
                Path rule = resolve(root, entry.getAsString());
                require(rule.toString().toLowerCase(Locale.ROOT).endsWith(".md") && Files.isRegularFile(rule),
                        "Project Rules must reference an existing Markdown file: " + rule);
                require(seen.add(key(rule)), "Duplicate Project Rules path: " + rule);
                rules.add(obj("path", rule.toString(), "sha256", hash(rule)));
                protectedPaths.add(rule.toString());
            }
        }
    }

    static Map<String, List<String>> parse(String[] args) {
        require(args.length > 0 && Set.of("prepare", "execute").contains(args[0]), "Action must be prepare or execute.");
        Map<String, List<String>> out = new LinkedHashMap<>();
        out.put("action", List.of(args[0]));
        Set<String> allowed = Set.of("project", "mode", "targets", "spec", "javadoc", "output", "run", "config");
        for (int i = 1; i < args.length; i++) {
            require(args[i].startsWith("--"), "Expected named option: " + args[i]);
            String name = args[i].substring(2);
            require(allowed.contains(name) && !out.containsKey(name), "Unknown or duplicate option: " + name);
            List<String> values = new ArrayList<>();
            if (!name.equals("javadoc")) {
                require(i + 1 < args.length && !args[i + 1].startsWith("--"), "Value missing: " + name);
                values.add(args[++i]);
                if (name.equals("targets")) {
                    while (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        values.add(args[++i]);
                    }
                }
            }
            out.put(name, values);
        }
        return out;
    }

    int run() throws Exception {
        return value("action", "").equals("prepare") ? prepare() : execute();
    }

    int prepare() throws Exception {
        String mode = required("mode");
        require(Set.of("coverage", "spec", "review").contains(mode), "Invalid mode.");
        List<String> targets = options.getOrDefault("targets", List.of());
        require(!targets.isEmpty(), "Mode and concrete Targets are required.");
        for (String target : targets) {
            require(FQCN.matcher(target).matches(), "Expected exact qualified class name: " + target);
            require(Files.isRegularFile(root.resolve("src/main/java/" + target.replace('.', '/') + ".java")),
                    "Source not found in standard source root: " + target);
        }
        boolean javadoc = options.containsKey("javadoc");
        require(!(javadoc && options.containsKey("spec")), "Spec and Javadoc are mutually exclusive.");
        require(!(javadoc && mode.equals("coverage")), "Javadoc is only available for spec/review.");
        String source = mode.equals("coverage") ? "implementation" : javadoc ? "javadoc" : "document";
        Path spec = null;
        if (source.equals("document")) {
            require(options.containsKey("spec") && !required("spec").isBlank(),
                    "Spec required for spec/review unless Javadoc is selected.");
            spec = resolve(root, required("spec")).toRealPath();
            require(Files.isDirectory(spec) || Files.isRegularFile(spec), "Spec must be a file or directory.");
            protectedPaths.add(spec.toString());
        }
        Path output = resolve(root, value("output", string(settings, "outputRoot", "")));
        for (String entry : protectedPaths) {
            require(!within(output, resolve(root, entry)), "Output overlaps protected path.");
        }
        require(!same(output, root) && !key(output).startsWith(key(root.resolve(".git"))), "Unsafe output root.");
        String id = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) +
                "-" + UUID.randomUUID().toString().substring(0, 8);
        Path run = output.resolve(mode).resolve(id);
        Files.createDirectories(run.getParent());
        Files.createDirectory(run); // Fail on collision, never overwrite an earlier run.
        Files.createDirectory(run.resolve("java"));
        write(run.resolve("before-hashes.json"), snapshot(root, protectedPaths));
        write(run.resolve("run.json"), obj("schemaVersion", 1, "runner", "java", "project", root.toString(),
                "config", config.toString(), "mode", mode, "targets", targets,
                "spec", spec == null ? null : spec.toString(), "specSource", source, "projectRules", rules,
                "runId", id, "runPath", run.toString(), "protectedPaths", protectedPaths,
                "startedAt", Instant.now().toString()));
        write(run.resolve("project-rules.json"), obj("schemaVersion", 1, "files", rules,
                "status", rules.size() == 0 ? "NOT_CONFIGURED" : "PENDING_AI_REVIEW"));
        System.out.println(run);
        return 0;
    }

    int execute() throws Exception {
        Path run = Paths.get(required("run")).toRealPath();
        JsonObject manifest = read(run.resolve("run.json"));
        require(same(root, Paths.get(string(manifest, "project", ""))) &&
                same(config, Paths.get(string(manifest, "config", ""))) &&
                same(run, Paths.get(string(manifest, "runPath", ""))), "Run/project/config mismatch.");
        require("java".equals(string(manifest, "runner", "powershell")),
                "Run runner mismatch; prepare a new run using the configured runner.");
        Path marker = run.resolve("execution-started.json");
        require(!Files.exists(marker), "Run already used. Prepare a new run; never reuse old coverage.");
        List<String> paths = strings(manifest.get("protectedPaths"));
        JsonObject before = read(run.resolve("before-hashes.json"));
        require(changes(before, snapshot(root, paths)).isEmpty(), "Protected files changed before execution.");
        Path java = run.resolve("java");
        List<Path> files = files(java).stream().filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        require(!files.isEmpty(), "No generated tests; refusing zero-test success.");
        List<String> names = new ArrayList<>();
        for (Path file : files) {
            String code = readText(file);
            if (!TEST.matcher(code).find()) {
                continue;
            }
            String name = file.getFileName().toString().replaceFirst("\\.java$", "");
            require(name.matches(".+Test"), "Generated test classes must end in Test.");
            Matcher pkg = PACKAGE.matcher(code);
            require(pkg.find(), "Test package required.");
            names.add(pkg.group(1) + "." + name);
        }
        require(!names.isEmpty(), "No supported test annotations found.");
        String maven = string(settings, "maven", "");
        require(!maven.isBlank(), "Maven executable not configured.");
        Path build = run.resolve("build");
        List<String> base = new ArrayList<>(Arrays.asList("-B", "-P" + string(settings, "profile", ""),
                "-Dut.sources=" + java, "-Dut.build=" + build, "-Dtest=" + String.join(",", names)));
        String localRepo = string(settings, "localRepository", "");
        if (!localRepo.isBlank()) {
            base.add("-Dmaven.repo.local=" + localRepo);
        }
        // Atomically claim the run before any child process starts.
        Files.writeString(marker, JSON.toJson(obj("startedAt", Instant.now().toString(), "maven", maven,
                "arguments", base, "testClasses", names)), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        write(run.resolve("ut-hashes.json"), snapshot(run, List.of("java")));
        int testExit = -1, coverageExit = -1;
        Double testSeconds = null, coverageSeconds = null;
        String executionError = null;
        try {
            long start = System.nanoTime();
            try {
                testExit = maven(maven, base, "test", run.resolve("test.log"));
            } finally {
                testSeconds = seconds(start);
            }
            start = System.nanoTime();
            try {
                coverageExit = maven(maven, base, "jacoco:report", run.resolve("coverage.log"));
            } finally {
                coverageSeconds = seconds(start);
            }
        } catch (Exception e) {
            executionError = e.toString();
        }
        List<String> changed = changes(before, snapshot(root, paths));
        write(run.resolve("change-check.json"), obj("unchanged", changed.isEmpty(), "changedPaths", changed));
        JsonObject timing = obj("testSeconds", testSeconds, "coverageSeconds", coverageSeconds,
                "totalSeconds", round((testSeconds == null ? 0 : testSeconds) +
                        (coverageSeconds == null ? 0 : coverageSeconds), 3));
        return aggregate(run, manifest, names, testExit, coverageExit, changed, executionError, timing);
    }

    /** Set JAVA_HOME only on the child; preserve the helper process environment. */
    int maven(String executable, List<String> base, String goal, Path log) throws Exception {
        Path command = executable(executable);
        List<String> args = new ArrayList<>();
        args.add(command.toString());
        args.addAll(base);
        args.add(goal);
        ProcessBuilder builder;
        if (WINDOWS && command.toString().toLowerCase(Locale.ROOT).matches(".*\\.(cmd|bat)$")) {
            // cmd expands % and ! even in quotes. Reject those paths instead of silently altering them.
            List<String> quoted = new ArrayList<>();
            for (String arg : args) {
                require(!arg.matches("(?s).*[\"%!\\r\\n].*"), "Unsupported cmd argument character: quote, %, ! or newline.");
                quoted.add("\"" + arg + "\"");
            }
            String comspec = System.getenv().getOrDefault("ComSpec", "C:\\Windows\\System32\\cmd.exe");
            builder = new ProcessBuilder(comspec, "/d", "/v:off", "/s", "/c",
                    "\"" + String.join(" ", quoted) + "\"");
        } else {
            builder = new ProcessBuilder(args);
        }
        builder.directory(root.toFile());
        String home = string(settings, "javaHome", "");
        if (!home.isBlank()) {
            builder.environment().put("JAVA_HOME", home);
        }
        // Preserve raw output bytes; do not buffer Maven output in a pipe.
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        Process process = builder.start();
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    Path executable(String name) throws IOException {
        Path path = Paths.get(name);
        if (path.isAbsolute() || name.contains("/") || name.contains("\\")) {
            path = resolve(root, name);
            require(Files.isRegularFile(path), "Maven executable not found: " + path);
            return path;
        }
        List<String> suffixes = WINDOWS ? List.of("", ".cmd", ".bat", ".exe") : List.of("");
        for (String dir : System.getenv().getOrDefault("PATH", "").split(Pattern.quote(File.pathSeparator))) {
            if (dir.isBlank()) {
                continue;
            }
            for (String suffix : suffixes) {
                path = Paths.get(dir.replace("\"", ""), name + suffix);
                if (Files.isRegularFile(path)) {
                    return path.toAbsolutePath();
                }
            }
        }
        throw new IOException("Maven executable not found: " + name);
    }

    int aggregate(Path run, JsonObject manifest, List<String> names, int testExit, int coverageExit,
                  List<String> changed, String error, JsonObject timing) throws Exception {
        Path build = run.resolve("build");
        int tests = 0, failures = 0, errors = 0, skipped = 0;
        JsonArray cases = new JsonArray(), coverage = new JsonArray();
        List<String> unexpected = new ArrayList<>(), missing = new ArrayList<>();
        Path surefire = build.resolve("surefire-reports");
        if (Files.isDirectory(surefire)) {
            for (Path file : files(surefire)) {
                if (!file.getFileName().toString().matches("TEST-.*\\.xml")) {
                    continue;
                }
                Element suite = xml(file).getDocumentElement();
                if (!names.contains(suite.getAttribute("name"))) {
                    unexpected.add(suite.getAttribute("name"));
                }
                tests += number(suite, "tests");
                failures += number(suite, "failures");
                errors += number(suite, "errors");
                skipped += number(suite, "skipped");
                for (Element c : children(suite, "testcase")) {
                    Element failure = child(c, "failure"), err = child(c, "error");
                    String outcome = failure != null ? "FAIL" : err != null ? "ERROR" :
                            child(c, "skipped") != null ? "SKIP" : "PASS";
                    cases.add(obj("name", c.getAttribute("name"), "class", c.getAttribute("classname"),
                            "outcome", outcome, "failure", failure == null ? "" : failure.getAttribute("message"),
                            "error", err == null ? "" : err.getAttribute("message"), "output", text(c, "system-out")));
                }
            }
        }
        List<String> targets = strings(manifest.get("targets"));
        Path jacoco = build.resolve("site/jacoco/jacoco.xml");
        if (Files.isRegularFile(jacoco)) {
            Element report = xml(jacoco).getDocumentElement();
            Map<String, Element> classes = new HashMap<>();
            for (Element pkg : children(report, "package")) {
                for (Element c : children(pkg, "class")) {
                    classes.put(c.getAttribute("name"), c);
                }
            }
            for (String target : targets) {
                Element c = classes.get(target.replace('.', '/'));
                if (c == null) {
                    missing.add(target);
                    continue;
                }
                for (Element counter : children(c, "counter")) {
                    String metric = counter.getAttribute("type");
                    if (!Set.of("LINE", "BRANCH").contains(metric)) {
                        continue;
                    }
                    int covered = number(counter, "covered"), missed = number(counter, "missed");
                    coverage.add(obj("target", target, "metric", metric, "covered", covered, "missed", missed,
                            "percent", covered + missed == 0 ? null : round(100.0 * covered / (covered + missed), 2)));
                }
            }
        } else {
            missing.addAll(targets);
        }
        String status;
        if (error != null || tests == 0 || tests == skipped || !unexpected.isEmpty() || !changed.isEmpty() ||
                (testExit != 0 && testExit != 1) || (testExit != 0 && failures + errors == 0)) {
            status = "EXECUTION_ERROR";
        } else if (errors > 0) {
            status = "TEST_ERROR";
        } else if (failures > 0) {
            status = "TEST_FAILURE";
        } else {
            status = "PASS";
        }
        JsonObject summary = obj("runId", string(manifest, "runId", ""), "mode", string(manifest, "mode", ""),
                "status", status, "testExit", testExit, "coverageExit", coverageExit, "tests", tests,
                "passed", tests - failures - errors - skipped, "failures", failures, "errors", errors,
                "skipped", skipped, "coverage", coverage, "missingCoverage", missing,
                "unexpectedSuites", unexpected, "protectedChanges", changed, "executionError", error,
                "mavenTiming", timing, "aiReportComplete", false);
        write(run.resolve("case-results.json"), cases);
        write(run.resolve("summary.json"), summary);
        System.out.println(run.resolve("summary.json"));
        return status.equals("EXECUTION_ERROR") || !missing.isEmpty() || coverageExit != 0 ? 2 :
                status.equals("PASS") ? 0 : 1;
    }

    static Document xml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.parse(path.toFile());
    }

    static Element child(Element parent, String name) {
        List<Element> found = children(parent, name);
        return found.isEmpty() ? null : found.get(0);
    }

    static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        if (parent != null) {
            for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element && name.equals(n.getLocalName() == null ? n.getNodeName() : n.getLocalName())) {
                    result.add((Element) n);
                }
            }
        }
        return result;
    }

    static String text(Element e, String name) {
        Element c = child(e, name);
        return c == null ? "" : c.getTextContent();
    }

    static int number(Element e, String name) {
        return e.hasAttribute(name) ? Integer.parseInt(e.getAttribute(name)) : 0;
    }

    static List<Path> files(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        }
    }

    static JsonObject snapshot(Path root, List<String> paths) throws Exception {
        JsonObject result = new JsonObject();
        for (String item : paths) {
            Path path = resolve(root, item);
            List<Path> entries = Files.isRegularFile(path) ? List.of(path) : files(path);
            for (Path file : entries) {
                result.addProperty(file.toString(), hash(file));
            }
        }
        return result;
    }

    static String hash(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[65536];
            for (int count; (count = in.read(buf)) >= 0;) {
                md.update(buf, 0, count);
            }
        }
        StringBuilder out = new StringBuilder();
        for (byte b : md.digest()) {
            out.append(String.format("%02X", b));
        }
        return out.toString();
    }

    static List<String> changes(JsonObject before, JsonObject after) {
        Set<String> paths = new LinkedHashSet<>(before.keySet());
        paths.addAll(after.keySet());
        return paths.stream().filter(p -> !Objects.equals(before.get(p), after.get(p))).collect(Collectors.toList());
    }

    static Path resolve(Path base, String value) {
        require(value != null && !value.isBlank(), "Empty path.");
        return base.resolve(value).toAbsolutePath().normalize();
    }

    static String key(Path p) {
        String value = p.toAbsolutePath().normalize().toString();
        return WINDOWS ? value.toLowerCase(Locale.ROOT) : value;
    }

    static boolean same(Path a, Path b) {
        return key(a).equals(key(b));
    }

    static boolean within(Path path, Path parent) {
        return same(path, parent) || key(path).startsWith(key(parent) + File.separator);
    }

    static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(readText(path)).getAsJsonObject();
    }

    static String readText(Path path) throws IOException {
        String value = Files.readString(path, StandardCharsets.UTF_8);
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    static void write(Path path, Object value) throws IOException {
        Files.writeString(path, JSON.toJson(value), StandardCharsets.UTF_8);
    }

    static JsonObject obj(Object... pairs) {
        JsonObject result = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            result.add((String) pairs[i], JSON.toJsonTree(pairs[i + 1]));
        }
        return result;
    }

    static List<String> strings(JsonElement array) {
        if (array == null || array.isJsonNull()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonElement e : array.getAsJsonArray()) {
            result.add(e.getAsString());
        }
        return result;
    }

    String value(String name, String fallback) {
        return options.containsKey(name) ? options.get(name).get(0) : fallback;
    }

    String required(String name) {
        String result = value(name, "");
        require(!result.isBlank(), "Required option: " + name);
        return result;
    }

    static String string(JsonObject value, String name, String fallback) {
        JsonElement entry = value.get(name);
        return entry == null || entry.isJsonNull() ? fallback : entry.getAsString();
    }

    static double seconds(long start) {
        return round((System.nanoTime() - start) / 1_000_000_000.0, 3);
    }

    static double round(double value, int digits) {
        double factor = Math.pow(10, digits);
        return Math.rint(value * factor) / factor;
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
