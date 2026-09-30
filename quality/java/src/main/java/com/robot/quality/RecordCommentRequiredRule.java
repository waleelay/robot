package com.robot.quality;

import java.util.Set;
import java.util.HashSet;
import net.sourceforge.pmd.lang.java.ast.ASTRecordDeclaration;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;

/**
 * 补齐 PMD CommentRequired 对 Java record 声明的覆盖。
 *
 * @author Codex
 * @date 2026-09-29
 */
public class RecordCommentRequiredRule extends AbstractJavaRulechainRule {

    /** 创建仅访问 record 声明的规则。 */
    public RecordCommentRequiredRule() {
        super(ASTRecordDeclaration.class);
    }

    /** 检查 record 的用途及每个组成字段；字段注解不替代 Java 调用契约。 */
    @Override
    public Object visit(ASTRecordDeclaration node, Object data) {
        if (node.getJavadocComment() == null) {
            asCtx(data).addViolation(node);
        } else {
            String text = MemberDocumentationRequiredRule.normalized(node);
            if (text.split("(?m)^\\s*@", 2)[0].trim().isEmpty()) {
                asCtx(data).addViolationWithMessage(node, "record 需要说明用途，不能只填写参数标签");
            }
            Set<String> names = new HashSet<>();
            for (var component : node.getRecordComponents()) {
                String name = component.getVarId().getName();
                names.add(name);
                if (!MemberDocumentationRequiredRule.hasTag(text, "param", name)) {
                    asCtx(data).addViolationWithMessage(node, "record 的 Javadoc 缺少组成字段 {0} 的有效说明", name);
                }
            }
            for (String name : MemberDocumentationRequiredRule.staleParameters(text, names)) {
                asCtx(data).addViolationWithMessage(node, "record 的 Javadoc 参数 {0} 已不在组成字段中", name);
            }
        }
        return data;
    }
}
