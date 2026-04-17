package com.gaia3d.modifier.billboard.atlas;

import java.awt.image.BufferedImage;

public class ImageDilate {

    public static BufferedImage dilateAlphaRGB(BufferedImage src, int iterations) {
        int width = src.getWidth();
        int height = src.getHeight();

        BufferedImage current = deepCopy(src);

        for (int iter = 0; iter < iterations; iter++) {
            BufferedImage next = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {

                    int argb = current.getRGB(x, y);
                    int a = (argb >> 24) & 0xFF;

                    if (a > 0) {
                        // 이미 유효한 픽셀 → 그대로 유지
                        next.setRGB(x, y, argb);
                        continue;
                    }

                    // 주변에서 색 찾기
                    int rSum = 0, gSum = 0, bSum = 0;
                    int count = 0;

                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {

                            if (dx == 0 && dy == 0) continue;

                            int nx = x + dx;
                            int ny = y + dy;

                            if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;

                            int neighbor = current.getRGB(nx, ny);
                            int na = (neighbor >> 24) & 0xFF;

                            if (na > 0) {
                                int nr = (neighbor >> 16) & 0xFF;
                                int ng = (neighbor >> 8) & 0xFF;
                                int nb = neighbor & 0xFF;

                                rSum += nr;
                                gSum += ng;
                                bSum += nb;
                                count++;
                            }
                        }
                    }

                    if (count > 0) {
                        int r = rSum / count;
                        int g = gSum / count;
                        int b = bSum / count;

                        // alpha는 그대로 0 유지
                        int newArgb = (0 << 24) | (r << 16) | (g << 8) | b;
                        next.setRGB(x, y, newArgb);
                    } else {
                        // 주변에도 없으면 그대로
                        next.setRGB(x, y, argb);
                    }
                }
            }

            current = next;
        }

        return current;
    }

    private static BufferedImage deepCopy(BufferedImage bi) {
        BufferedImage copy = new BufferedImage(
                bi.getWidth(),
                bi.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );
        copy.setData(bi.getData());
        return copy;
    }
}
