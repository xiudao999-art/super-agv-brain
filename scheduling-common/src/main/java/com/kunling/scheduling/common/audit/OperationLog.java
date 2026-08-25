package com.kunling.scheduling.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要记录操作审计日志的业务方法。
 *
 * <p>注解放在公共模块，Action、AGV Flow、Workflow 等业务模块无需反向依赖应用模块。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 业务模块，例如“动作定义”。 */
    String module();

    /** 具体操作，例如“发布动作定义”。 */
    String operation();

    OperationType type() default OperationType.OTHER;

    boolean recordRequest() default true;

    boolean recordResponse() default true;

    /** 除默认敏感字段外，本接口需要额外脱敏的字段名。 */
    String[] sensitiveFields() default {};
}
