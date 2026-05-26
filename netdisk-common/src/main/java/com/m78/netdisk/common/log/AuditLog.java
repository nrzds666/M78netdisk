package com.m78.netdisk.common.log;

import java.lang.annotation.*;

/**
 * 审计日志注解。标注在需要记录操作日志的方法上。
 * <p>
 * 示例：
 * <pre>{@code
 * @AuditLog(action = "FILE_UPLOAD")
 * public R<ItemVO> upload(...) { ... }
 *
 * @AuditLog(action = "FOLDER_CREATE", detail = "#dto.name")
 * public R<Void> createFolder(@RequestBody CreateFolderDTO dto) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 操作类型，如 FILE_UPLOAD、FOLDER_CREATE、FILE_DELETE、SHARE_CREATE 等 */
    String action();

    /** SpEL 表达式，用于提取 itemId。留空则不关联文件/文件夹。 */
    String itemId() default "";

    /** SpEL 表达式，用于动态提取 detail 字段（JSON）。留空则不记录 detail。 */
    String detail() default "";
}
