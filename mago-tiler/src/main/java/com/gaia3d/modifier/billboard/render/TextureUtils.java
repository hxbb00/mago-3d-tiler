package com.gaia3d.modifier.billboard.render;

import org.lwjgl.BufferUtils;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;

public class TextureUtils {

    public static int createTextureFromBufferedImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);

        // OpenGL은 보통 아래->위 순서로 기대하니 y를 뒤집어서 넣는 경우가 많음
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];

                // ARGB -> RGBA
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                buffer.put((byte) (pixel & 0xFF));         // B
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
            }
        }

        buffer.flip();

        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        // wrap
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // filter
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        // 업로드
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA8,
                width,
                height,
                0,
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                buffer
        );

        // mipmap 필요하면 생성
        glGenerateMipmap(GL_TEXTURE_2D);

        // 업로드 후 unbind
        glBindTexture(GL_TEXTURE_2D, 0);

        return textureId;
    }
}