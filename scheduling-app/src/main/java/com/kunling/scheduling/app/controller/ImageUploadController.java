package com.kunling.scheduling.app.controller;

import com.kunling.scheduling.app.domain.ImageUploadResult;
import com.kunling.scheduling.app.service.ImageStorageService;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@Tag(name = "文件管理", description = "上传实验室地图图片")
public class ImageUploadController extends BaseController {

    private final ImageStorageService imageStorageService;

    public ImageUploadController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "上传图片", description = "支持 PNG、JPEG、GIF、WEBP，最大 10MB")
    public ApiResult<ImageUploadResult> upload(
            @Parameter(description = "图片文件", required = true)
            @RequestPart("file") MultipartFile file) {
        return created(imageStorageService.store(file));
    }
}
