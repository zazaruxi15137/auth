package com.example.gateway_service.gateway_service.common.tool;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

public class FlieUtil {
        private FlieUtil() {
            // Prevent instantiation
        }
        private static final List<String> IMAGE_MIME_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp"
    );
    // 判断是否为图片
    public static boolean isImage(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;
        String contentType = file.getContentType();
        return contentType != null && IMAGE_MIME_TYPES.contains(contentType.toLowerCase());
    }



    public static boolean hasImageMagicNumber(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int len = is.read(header);
            if (len < 8) return false;

            // 检查JPEG
            if (header[0] == (byte)0xFF && header[1] == (byte)0xD8) return true;
            // 检查PNG
            if (header[0] == (byte)0x89 && header[1] == (byte)0x50 && header[2] == (byte)0x4E && header[3] == (byte)0x47) return true;
            // 检查GIF
            if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F') return true;
            // 检查BMP
            if (header[0] == 'B' && header[1] == 'M') return true;
            // 检查WEBP
            if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F') return true;

            return false;
        } catch (IOException e) {
            return false;
        }
    }
    public static MediaType getMediaType(String filename) {
    String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    switch (ext) {
        case "png": return MediaType.IMAGE_PNG;
        case "gif": return MediaType.IMAGE_GIF;
        case "jpeg","jpg": return MediaType.IMAGE_JPEG;
        case "bmp": return MediaType.valueOf("image/bmp");
        case "webp": return MediaType.valueOf("image/webp");
        default: return MediaType.APPLICATION_OCTET_STREAM; // 默认二进制
    }
}
    
}
