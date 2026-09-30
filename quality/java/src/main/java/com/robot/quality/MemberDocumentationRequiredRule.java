package com.robot.quality;

import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTEnumConstant;
import net.sourceforge.pmd.lang.java.ast.ASTExecutableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import net.sourceforge.pmd.lang.java.ast.JavadocCommentOwner;
import net.sourceforge.pmd.lang.java.ast.ModifierOwner.Visibility;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;

/** 检查成员说明和调用契约；语义是否准确以及短方法中的关键决策仍需人工审查。 */
public class MemberDocumentationRequiredRule extends AbstractJavaRulechainRule {

    /** 同时访问字段、枚举值、显式构造器和方法，避免只检查普通类声明。 */
    public MemberDocumentationRequiredRule() {
        super(ASTFieldDeclaration.class, ASTEnumConstant.class, ASTMethodDeclaration.class, ASTConstructorDeclaration.class);
    }

    /** 检查可客观识别的业务字段；依赖注入和简单实现细节不强制重复说明。 */
    @Override
    public Object visit(ASTFieldDeclaration node, Object data) {
        boolean exposed = node.getVisibility() == Visibility.V_PUBLIC
                || node.getVisibility() == Visibility.V_PROTECTED;
        boolean model = !node.isStatic() && (node.isAnyAnnotationPresent(Set.of(
                "io.swagger.v3.oas.annotations.media.Schema",
                "jakarta.persistence.Column",
                "org.springframework.beans.factory.annotation.Value"))
                || node.ancestors(ASTTypeDeclaration.class).any(type -> type.isAnyAnnotationPresent(Set.of(
                        "jakarta.persistence.Entity",
                        "org.springframework.boot.context.properties.ConfigurationProperties"))));
        if (exposed || model) {
            requireDescription(node, data);
        }
        return data;
    }

    /** 枚举值需要解释业务含义，而非只给枚举类型写总述。 */
    @Override
    public Object visit(ASTEnumConstant node, Object data) {
        requireDescription(node, data);
        return data;
    }

    /** 公开调用契约要求完整参数和返回说明；长方法额外要求说明职责与边界。 */
    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        boolean inherited = node.isAnnotationPresent("java.lang.Override");
        if (publicContract(node) && !inherited) {
            checkContract(node, !node.isVoid(), data);
        } else if (node.getBody() != null && node.getBody().getEndLine() - node.getBody().getBeginLine() >= 40) {
            requireDescription(node, data);
        }
        return data;
    }

    /** 显式公开构造器同样描述传入依赖或初始状态。 */
    @Override
    public Object visit(ASTConstructorDeclaration node, Object data) {
        if (publicContract(node)) {
            checkContract(node, false, data);
        }
        return data;
    }

    private boolean publicContract(ASTExecutableDeclaration node) {
        return node.getVisibility() == Visibility.V_PUBLIC || node.getVisibility() == Visibility.V_PROTECTED;
    }

    private void checkContract(ASTExecutableDeclaration node, boolean returnsValue, Object data) {
        if (!requireDescription(node, data)) {
            return;
        }
        String text = normalized(node);
        Set<String> parameterNames = new HashSet<>();
        for (var parameter : node.getFormalParameters()) {
            parameterNames.add(parameter.getVarId().getName());
            if (!hasTag(text, "param", parameter.getVarId().getName())) {
                asCtx(data).addViolationWithMessage(node, "Javadoc 缺少参数 {0} 的有效说明", parameter.getVarId().getName());
            }
        }
        for (String name : staleParameters(text, parameterNames)) {
            asCtx(data).addViolationWithMessage(node, "Javadoc 的参数 {0} 已不在方法签名中", name);
        }
        if (returnsValue && !hasTag(text, "return", null)) {
            asCtx(data).addViolationWithMessage(node, "Javadoc 缺少返回值说明");
        }
        if (node.getThrowsList() != null) {
            for (var exception : node.getThrowsList()) {
                String name = exception.getSimpleName();
                if (!hasTag(text, "throws", name) && !hasTag(text, "exception", name)) {
                    asCtx(data).addViolationWithMessage(node, "Javadoc 缺少声明异常 {0} 的说明", name);
                }
            }
        }
    }

    private boolean requireDescription(JavadocCommentOwner node, Object data) {
        String text = normalized(node);
        String summary = text.split("(?m)^\\s*@", 2)[0].trim();
        if (summary.isEmpty()) {
            asCtx(data).addViolationWithMessage(node, "声明需要说明用途与业务边界的 Javadoc");
            return false;
        }
        return true;
    }

    static String normalized(JavadocCommentOwner node) {
        if (node.getJavadocComment() == null) {
            return "";
        }
        return node.getJavadocComment().getText().toString()
                .replaceFirst("^/\\*\\*", "").replaceFirst("\\*/$", "")
                .replaceAll("(?m)^\\s*\\* ?", "").trim();
    }

    /** 按完整块标签读取续行；下一个块标签不能被误认为当前空标签的说明。 */
    static boolean hasTag(String text, String tag, String name) {
        String qualifier = "throws".equals(tag) || "exception".equals(tag) ? "(?:[\\w$]+\\.)*" : "";
        var tags = Pattern.compile("(?ms)^\\h*@([A-Za-z][A-Za-z0-9]*)\\b(.*?)(?=^\\h*@|\\z)").matcher(text);
        while (tags.find()) {
            if (!tag.equals(tags.group(1))) {
                continue;
            }
            String description = tags.group(2).trim();
            if (name != null) {
                var parameter = Pattern.compile("^" + qualifier + Pattern.quote(name) + "(?=\\s|$)").matcher(description);
                if (!parameter.find()) {
                    continue;
                }
                description = description.substring(parameter.end()).trim();
            }
            if (!description.isEmpty() && !description.equals(name)) {
                return true;
            }
        }
        return false;
    }

    static Set<String> staleParameters(String text, Set<String> actualNames) {
        Set<String> stale = new HashSet<>();
        var matcher = Pattern.compile("(?m)^\\h*@param\\h+(\\w+)\\b").matcher(text);
        while (matcher.find()) {
            if (!actualNames.contains(matcher.group(1))) {
                stale.add(matcher.group(1));
            }
        }
        return stale;
    }
}
