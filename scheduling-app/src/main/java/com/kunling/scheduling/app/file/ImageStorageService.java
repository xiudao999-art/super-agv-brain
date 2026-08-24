package com.kunling.scheduling.app.file;

import com.kunling.scheduling.common.exception.InvalidRequestException;
import com.kunling.scheduling.common.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ImageStorageService {

    public static final String PUBLIC_PATH = "/files/";
    private static final int SIGNATURE_LENGTH = 12;

    private final Path storageDirectory;

    public ImageStorageService(
            @Value("${kunling.file.storage-directory:scheduling-app/src/main/resources/file}")
            String configuredDirectory) {
        this.storageDirectory = resolveStorageDirectory(configuredDirectory);
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException exception) {
            throw new ServiceUnavailableException("图片存储目录不可用", exception);
        }
    }

    public ImageUploadResult store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("上传图片不能为空");
        }

        try (BufferedInputStream input = new BufferedInputStream(file.getInputStream())) {
            input.mark(SIGNATURE_LENGTH);
            byte[] signature = new byte[SIGNATURE_LENGTH];
            int length = readSignature(input, signature);
            input.reset();

            String extension = detectExtension(signature, length);
            String storedName = UUID.randomUUID().toString() + extension;
            Path target = storageDirectory.resolve(storedName).normalize();
            if (!target.getParent().equals(storageDirectory)) {
                throw new InvalidRequestException("图片文件名不合法");
            }
            Files.copy(input, target);
            return new ImageUploadResult(PUBLIC_PATH + storedName);
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ServiceUnavailableException("图片保存失败", exception);
        }
    }

    public String resourceLocation() {
        return storageDirectory.toUri().toString();
    }

    private static Path resolveStorageDirectory(String configuredDirectory) {
        if (configuredDirectory == null || configuredDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("图片存储目录不能为空");
        }
        Path configured = Paths.get(configuredDirectory.trim());
        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
        if (workingDirectory.getFileName() != null
                && "scheduling-app".equalsIgnoreCase(workingDirectory.getFileName().toString())
                && configured.getNameCount() > 0
                && "scheduling-app".equalsIgnoreCase(configured.getName(0).toString())) {
            workingDirectory = workingDirectory.getParent();
        }
        return workingDirectory.resolve(configured).normalize();
    }

    private static int readSignature(InputStream input, byte[] signature) throws IOException {
        int total = 0;
        int read;
        while (total < signature.length
                && (read = input.read(signature, total, signature.length - total)) != -1) {
            total += read;
        }
        return total;
    }

    private static String detectExtension(byte[] value, int length) {
        if (length >= 8
                && unsigned(value[0]) == 0x89 && value[1] == 'P' && value[2] == 'N' && value[3] == 'G'
                && unsigned(value[4]) == 0x0D && unsigned(value[5]) == 0x0A
                && unsigned(value[6]) == 0x1A && unsigned(value[7]) == 0x0A) {
            return ".png";
        }
        if (length >= 3 && unsigned(value[0]) == 0xFF
                && unsigned(value[1]) == 0xD8 && unsigned(value[2]) == 0xFF) {
            return ".jpg";
        }
        if (length >= 6 && value[0] == 'G' && value[1] == 'I' && value[2] == 'F'
                && value[3] == '8' && (value[4] == '7' || value[4] == '9') && value[5] == 'a') {
            return ".gif";
        }
        if (length >= 12 && value[0] == 'R' && value[1] == 'I' && value[2] == 'F'
                && value[3] == 'F' && value[8] == 'W' && value[9] == 'E'
                && value[10] == 'B' && value[11] == 'P') {
            return ".webp";
        }
        throw new InvalidRequestException("仅支持 PNG、JPEG、GIF、WEBP 图片");
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }
}
