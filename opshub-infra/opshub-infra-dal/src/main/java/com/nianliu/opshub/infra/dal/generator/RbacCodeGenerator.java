package com.nianliu.opshub.infra.dal.generator;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.regex.*;

/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
public final class RbacCodeGenerator {
    private RbacCodeGenerator() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("参数: SQL文件 输出模块目录（建议使用临时目录预览）");
        String sql = Files.readString(Path.of(args[0]));
        Path root = Path.of(args[1]).toAbsolutePath().normalize().resolve("src/main/java");
        Matcher tables = Pattern.compile("CREATE TABLE `(?<table>sys_\\w+)`\\s*\\((?<columns>.*?)\\) ENGINE", Pattern.DOTALL).matcher(sql);
        int count = 0;
        while (tables.find()) {
            String table = tables.group("table");
            if (!java.util.Set.of("sys_account", "sys_role", "sys_menu", "sys_account_role", "sys_role_menu", "sys_login_log").contains(table)) continue;
            String name = camel(table, true);
            String pkg = "com.nianliu.opshub.infra.dal.dataobject.rbac";
            String metadata = "/**\n * @author nianliu-wjj\n * @since " + LocalDate.now() + "\n */\n";
            StringBuilder entity = new StringBuilder("package " + pkg + ";\n\nimport com.mybatisflex.annotation.*;\nimport lombok.Getter;\nimport lombok.Setter;\n\n" + metadata + "@Getter\n@Setter\n@Table(\"" + table + "\")\npublic class " + name + " {\n");
            Matcher fields = Pattern.compile("(?m)^\\s*`(\\w+)`\\s+(\\w+)").matcher(tables.group("columns"));
            while (fields.find()) {
                String column = fields.group(1), type = fields.group(2).toLowerCase();
                String javaType = switch (type) { case "bigint" -> "Long"; case "int", "tinyint" -> "Integer"; case "datetime", "timestamp" -> "java.time.LocalDateTime"; default -> "String"; };
                if (column.equals("id")) entity.append("    @Id(keyType = KeyType.Auto)\n");
                entity.append("    @Column(value = \"").append(column).append(column.equals("version") ? "\", version = true)\n    private " : column.equals("is_deleted") ? "\", isLogicDelete = true)\n    private " : "\")\n    private ").append(javaType).append(' ').append(camel(column, false)).append(";\n");
            }
            entity.append("}\n");
            Path entityPath = root.resolve(pkg.replace('.', '/')).resolve(name + ".java");
            String mapperPkg = "com.nianliu.opshub.infra.dal.daointerface.rbac";
            Path mapperPath = root.resolve(mapperPkg.replace('.', '/')).resolve(name + "Mapper.java");
            if (Files.exists(entityPath) || Files.exists(mapperPath)) throw new IllegalStateException("拒绝覆盖已有文件: " + name);
            Files.createDirectories(entityPath.getParent()); Files.createDirectories(mapperPath.getParent());
            Files.writeString(entityPath, entity, StandardOpenOption.CREATE_NEW);
            Files.writeString(mapperPath, "package " + mapperPkg + ";\n\nimport com.mybatisflex.core.BaseMapper;\nimport " + pkg + "." + name + ";\n\n" + metadata + "public interface " + name + "Mapper extends BaseMapper<" + name + "> { }\n", StandardOpenOption.CREATE_NEW);
            count++;
        }
        if (count != 6) throw new IllegalArgumentException("需要包含六张RBAC表，实际: " + count);
        System.out.println("已生成 " + count + " 组MyBatis-Flex实体与Mapper");
    }
    private static String camel(String text, boolean upperFirst) {
        StringBuilder result = new StringBuilder(); boolean upper = upperFirst;
        for (char c : text.toCharArray()) { if (c == '_') upper = true; else { result.append(upper ? Character.toUpperCase(c) : c); upper = false; } }
        return result.toString();
    }
}