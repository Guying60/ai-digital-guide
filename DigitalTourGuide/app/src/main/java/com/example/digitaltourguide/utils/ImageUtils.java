package com.example.digitaltourguide.utils;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ImageUtils {
    public static Bitmap compressBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        if (bitmap == null) return null;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float scale = Math.min((float) maxWidth / width, (float) maxHeight / height);
        if (scale >= 1) return bitmap;

        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    public static String bitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return "";

        byte[] bytes = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()){
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos); // 后端要求质量80
            bytes = baos.toByteArray();

            return Base64.encodeToString(bytes, Base64.NO_WRAP); // 绝对不能换行
        } catch (Exception e) {
            Log.e("ImageUtils", "bitmap转base64失败", e);
            return "";
        }
    }
}
