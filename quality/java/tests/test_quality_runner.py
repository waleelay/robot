"""验证规则覆盖和失败行为，避免检查脚本静默放行。"""

from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts"))
import quality_check as quality


class ModulePathTest(unittest.TestCase):
    def test_legacy_backend_baseline_maps_to_media_service(self):
        roots = {"media-common", "backend", "control-service", "bigscreen-bff"}
        self.assertEqual("backend", quality.base_module_names(roots)["media-service"])

    def test_current_baseline_uses_media_service(self):
        roots = {"media-common", "media-service", "control-service", "bigscreen-bff"}
        self.assertEqual("media-service", quality.base_module_names(roots)["media-service"])


class JavaRulesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.classpath = str(ROOT / "quality/java/target/classes") + quality.os.pathsep + (
            ROOT / "quality/java/target/classpath.txt").read_text().strip()

    def scan(self, source):
        with tempfile.TemporaryDirectory() as name:
            directory = Path(name)
            file = directory / "Sample.java"
            file.write_text(source)
            return quality.scan(directory, [file], self.classpath, directory / "report.xml")

    def test_java17_positive_example(self):
        self.assertEqual([], self.scan('''
            /** 正例覆盖文本块、record、模式匹配和 switch 表达式。 */
            class ValidExample {
                /** 结果值。
                 * @param text 显示内容
                 */
                record Result(String text) {}
                long count = 1L;
                String describe(Object input) {
                    if (input instanceof String text) {
                        return switch (text) { case "one" -> "first"; default -> "other"; };
                    }
                    return """
                            empty
                            """;
                }
            }
        '''))

    def test_every_enabled_rule_finds_a_violation(self):
        findings = self.scan('''
            class invalid_class {
                long Bad_Method(boolean condition) { if (condition) return 1l; return 0L; }
                public boolean equals(Object other) { return this == other; }
                int masked() { try { return 0; } finally { return 1; } }
                java.math.BigDecimal decimal() { return new java.math.BigDecimal(0.1); }
                record bad_record(String text) {}
            }
        ''')
        self.assertEqual({"ClassNamingConventions", "MethodNamingConventions", "ControlStatementBraces",
                          "LongSuffixUppercase", "OverrideBothEqualsAndHashcode", "ReturnFromFinallyBlock",
                          "AvoidDecimalLiteralsInBigDecimalConstructor", "CommentRequired", "RecordCommentRequired",
                          "MemberDocumentationRequired"},
                         {item["rule"] for item in findings})

    def test_member_documentation_covers_enum_record_and_public_contracts(self):
        findings = self.scan('''
            /** 枚举用途不能替代每个值的解释。 */
            enum Mode { START, STOP }
            /** 类型用途不能替代组成字段说明。 */
            record Lease(String owner, long expiresAt) {}
            /** 验证公开及接口方法。 */
            interface Operations {
                String submit(String id);
                /** 查询任务。 */
                String find(String id);
            }
            /** 验证显式构造器。 */
            class Worker { public Worker(String name) {} }
        ''')
        rules = [item["rule"] for item in findings]
        self.assertEqual(2, rules.count("RecordCommentRequired"))
        self.assertEqual(6, rules.count("MemberDocumentationRequired"))

    def test_documented_members_and_inherited_methods_are_accepted(self):
        self.assertEqual([], self.scan('''
            /** 任务状态。 */
            enum Mode { /** 已开始。 */ START, /** 已停止。 */ STOP }
            /** 保留期。
             * @param owner 持有者 ID
             * @param expiresAt 过期时间，毫秒
             */
            record Lease(String owner, long expiresAt) {}
            /** 查询协议。 */
            interface Operations {
                /** 查找任务。
                 * @param id 任务 ID
                 * @return 当前状态，未找到返回 null
                 */
                String find(String id);
            }
            /** 继承查询协议。 */
            class Worker implements Operations {
                /** 创建工作器。
                 * @param name 工作器名称
                 */
                public Worker(String name) {}
                @Override public String find(String id) { return null; }
            }
        '''))

    def test_business_fields_require_documentation_even_with_schema(self):
        findings = self.scan('''
            /** 配置说明。 */
            @org.springframework.boot.context.properties.ConfigurationProperties(prefix = "sample")
            class Settings {
                private long timeoutMillis;
                /** 嵌套配置也继承字段要求。 */
                static class Nested { private String endpoint; }
            }
            /** 持久化实体。 */
            @jakarta.persistence.Entity
            class Stored { private String id; }
            /** 接口字段必须同时具备 Java 业务说明。 */
            class Payload {
                @io.swagger.v3.oas.annotations.media.Schema(description = "说明")
                private String code;
                @org.springframework.beans.factory.annotation.Value("${sample.timeout:10}")
                private long timeout;
                public static final int LIMIT = 10;
            }
        ''')
        self.assertEqual(6, sum(item["rule"] == "MemberDocumentationRequired" for item in findings))

    def test_documented_fields_and_clear_implementation_details_are_accepted(self):
        self.assertEqual([], self.scan('''
            /** 接口载荷。 */
            class Payload {
                /** 业务状态编码。 */
                @io.swagger.v3.oas.annotations.media.Schema(description = "业务状态编码")
                private String code;
                private Object dependency;
                private static final long serialVersionUID = 1L;
                private int index;
                /** 最大记录数。 */
                public static final int LIMIT = 10;
            }
        '''))

    def test_empty_member_tags_and_long_private_methods_are_rejected(self):
        statements = "\\n".join(["                count++;" for _ in range(41)])
        # 使用实际换行，让长方法阈值按源码位置生效。
        statements = statements.replace("\\n", "\n")
        findings = self.scan('''
            /** 注释反例。 */
            class Example {
                /** 查询。
                 * @param id
                 * @return
                 */
                public String find(String id) { return id; }
                private void process() {
                    int count = 0;
        ''' + statements + '''
                }
            }
        ''')
        self.assertEqual(3, sum(item["rule"] == "MemberDocumentationRequired" for item in findings))

    def test_declared_exceptions_require_explanation(self):
        template = '''
            /** 文件读取协议。 */
            interface Reader {
                /** 读取内容。
                 * @return 文件字节
                 %s
                 */
                byte[] read() throws java.io.IOException;
            }
        '''
        findings = self.scan(template % '')
        self.assertEqual(["MemberDocumentationRequired"], [item["rule"] for item in findings])
        self.assertEqual([], self.scan(template % '* @throws IOException 文件读取失败'))
        self.assertEqual([], self.scan(template % '* @throws java.io.IOException 文件读取失败'))

    def test_multiline_tags_describe_methods_records_and_exception_aliases(self):
        self.assertEqual([], self.scan('''
            /** 租约信息。
             * @param owner
             *     持有者 ID，
             *     用于识别当前任务。
             */
            record Lease(String owner) {}
            /** 查询协议。 */
            interface Lookup {
                /** 查询。
                 * @param id
                 *     业务记录的标识。
                 * @return
                 *     当前记录的文本内容。
                 * @throws java.io.IOException
                 *     读取存储失败时抛出。
                 */
                String find(String id) throws java.io.IOException;
                /** 读取。
                 * @return
                 *     {@link String} 表示的原始内容。
                 * @exception java.io.IOException
                 *     读取源不可用时抛出。
                 */
                String read() throws java.io.IOException;
            }
        '''))

    def test_empty_multiline_tags_do_not_consume_following_tags(self):
        findings = self.scan('''
            /** 查询协议。 */
            interface Lookup {
                /** 查询。
                 * @param id
                 *
                 * @param idSuffix 不是 id 的说明。
                 * @return
                 *
                 * @see String
                 * @throws java.io.IOException
                 *
                 */
                String find(String id, String idSuffix) throws java.io.IOException;
            }
        ''')
        self.assertEqual(3, len(findings))
        self.assertEqual({"MemberDocumentationRequired"}, {item["rule"] for item in findings})

    def test_multiline_parameter_names_alone_are_not_descriptions(self):
        findings = self.scan('''
            /** 租约信息。
             * @param owner
             *     owner
             */
            record Lease(String owner) {}
            /** 查询协议。 */
            interface Lookup {
                /** 查询。
                 * @param id
                 *     id
                 * @return 查询结果。
                 */
                String find(String id);
            }
        ''')
        self.assertEqual(2, len(findings))
        self.assertEqual({"MemberDocumentationRequired", "RecordCommentRequired"},
                         {item["rule"] for item in findings})

    def test_renamed_parameters_do_not_leave_stale_documentation(self):
        findings = self.scan('''
            /** 租约。
             * @param owner 当前持有者
             * @param oldOwner 已删除的旧名称
             */
            record Lease(String owner) {}
            /** 查询协议。 */
            interface Lookup {
                /** 查询。
                 * @param id 当前 ID
                 * @param oldId 已删除的旧名称
                 * @return 结果
                 */
                String find(String id);
            }
        ''')
        self.assertEqual({"MemberDocumentationRequired", "RecordCommentRequired"},
                         {item["rule"] for item in findings})
        self.assertEqual(2, len(findings))

    def test_record_purpose_and_parameter_meaning_cannot_be_placeholders(self):
        findings = self.scan('''
            /**
             * @param id id
             */
            record Result(String id) {}
            /** 查询协议。 */
            interface Lookup {
                /** 查询。
                 * @param id id
                 * @return 查询结果
                 */
                String find(String id);
            }
        ''')
        self.assertEqual(2, sum(item["rule"] == "RecordCommentRequired" for item in findings))
        self.assertEqual(1, sum(item["rule"] == "MemberDocumentationRequired" for item in findings))

    def test_integration_test_suffix_preserves_naming_checks(self):
        for name in ("RuntimeLockMySqlIT", "RuntimeLockTest", "TestRuntimeLock"):
            with self.subTest(name=name):
                self.assertEqual([], self.scan(f'''
                    import org.junit.jupiter.api.Test;
                    /** 验证测试命名约定。 */
                    class {name} {{ @Test void verifiesLock() {{}} }}
                '''))
        for name in ("runtimeLockIT", "Runtime_LockIT", "RuntimeLock"):
            with self.subTest(name=name):
                findings = self.scan(f'''
                    import org.junit.jupiter.api.Test;
                    /** 反例仍必须被命名规则拒绝。 */
                    class {name} {{ @Test void verifiesLock() {{}} }}
                ''')
                self.assertEqual(["ClassNamingConventions"], [item["rule"] for item in findings])

    def test_syntax_error_fails_instead_of_passing_empty_report(self):
        with self.assertRaises(RuntimeError):
            self.scan("public class Broken { invalid java !!! }")

    def test_empty_scan_is_an_error(self):
        with self.assertRaises(RuntimeError):
            quality.scan(ROOT, [], self.classpath, ROOT / "target/quality/empty.xml")

    def test_equal_totals_do_not_hide_replaced_or_duplicate_violations(self):
        old = {"file": "A.java", "rule": "Rule", "line": 1, "fingerprint": "old"}
        new = dict(old, line=2, fingerprint="new")
        self.assertEqual([new], quality.new_findings([new], [old]))
        self.assertEqual([old], quality.new_findings([old, old], [old]))
        self.assertEqual([], quality.new_findings([dict(old, line=20)], [old]))


if __name__ == "__main__":
    unittest.main()
