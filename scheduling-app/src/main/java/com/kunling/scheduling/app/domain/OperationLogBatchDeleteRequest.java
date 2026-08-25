package com.kunling.scheduling.app.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.List;

/** 操作日志批量删除请求。 */
public class OperationLogBatchDeleteRequest {

    @NotEmpty
    @Size(max = OperationLogConstraints.MAX_BATCH_DELETE_SIZE)
    @Schema(description = "待删除日志 ID，单次最多 200 条", required = true)
    private List<@NotNull @Positive Long> ids;

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }
}
