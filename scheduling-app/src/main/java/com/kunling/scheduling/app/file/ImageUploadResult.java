package com.kunling.scheduling.app.file;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "图片上传结果")
public class ImageUploadResult {

    private final String imageUrl;

    public ImageUploadResult(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @Schema(description = "图片相对访问地址，可直接提交给地图信息接口")
    public String getImageUrl() {
        return imageUrl;
    }
}
