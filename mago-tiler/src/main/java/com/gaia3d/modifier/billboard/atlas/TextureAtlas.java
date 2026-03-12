package com.gaia3d.modifier.billboard.atlas;

import com.gaia3d.basic.model.GaiaFace;
import com.gaia3d.basic.model.GaiaPrimitive;
import com.gaia3d.basic.model.GaiaSurface;
import com.gaia3d.basic.model.GaiaVertex;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2d;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Getter
@Setter
@NoArgsConstructor
public class TextureAtlas {
    private final List<TextureAtlasEntry> entries = new ArrayList<>();

    private BufferedImage atlasImage;
    private int atlasWidth;
    private int atlasHeight;

    private int tileWidth;
    private int tileHeight;

    private int columns;
    private int rows;

    public BufferedImage build(List<TextureAtlasSource> sources) {
        validateSources(sources);

        int count = sources.size();
        BufferedImage firstImage = sources.getFirst().getDiffuseImage();
        this.tileWidth = firstImage.getWidth();
        this.tileHeight = firstImage.getHeight();

        this.columns = (int) Math.ceil(Math.sqrt(count));
        this.rows = (int) Math.ceil((double) count / columns);

        this.atlasWidth = columns * tileWidth;
        this.atlasHeight = rows * tileHeight;

        this.atlasImage = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = atlasImage.createGraphics();

        try {
            entries.clear();
            for (int i = 0; i < count; i++) {
                TextureAtlasSource source = sources.get(i);
                int row = i / columns;
                int col = i % columns;
                int x = col * tileWidth;
                int y = row * tileHeight;

                g2d.drawImage(source.getDiffuseImage(), x, y, null);

                TextureAtlasEntry entry = new TextureAtlasEntry();
                entry.setIndex(i);
                entry.setMaterial(source.getMaterial());
                entry.setPrimitives(source.getPrimitives());
                entry.setDiffuseImage(source.getDiffuseImage());
                entry.setRow(row);
                entry.setCol(col);
                entry.setX(x);
                entry.setY(y);
                entry.setWidth(tileWidth);
                entry.setHeight(tileHeight);

                entry.setUOffset((double) x / atlasWidth);
                entry.setVOffset((double) y / atlasHeight);
                entry.setUScale((double) tileWidth / atlasWidth);
                entry.setVScale((double) tileHeight / atlasHeight);

                entries.add(entry);
            }
        } finally {
            g2d.dispose();
        }

        return atlasImage;
    }

    private void validateSources(List<TextureAtlasSource> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("TextureAtlas sources must not be null or empty.");
        }

        BufferedImage first = sources.getFirst().getDiffuseImage();
        if (first == null) {
            throw new IllegalArgumentException("First source diffuse image is null.");
        }

        int expectedWidth = first.getWidth();
        int expectedHeight = first.getHeight();

        for (int i = 0; i < sources.size(); i++) {
            TextureAtlasSource source = sources.get(i);
            if (source == null) {
                throw new IllegalArgumentException("TextureAtlas source[" + i + "] is null.");
            }
            if (source.getMaterial() == null) {
                throw new IllegalArgumentException("TextureAtlas source[" + i + "] material is null.");
            }
            if (source.getDiffuseImage() == null) {
                throw new IllegalArgumentException("TextureAtlas source[" + i + "] diffuse image is null.");
            }
            if (source.getDiffuseImage().getWidth() != expectedWidth || source.getDiffuseImage().getHeight() != expectedHeight) {
                throw new IllegalArgumentException("All diffuse images must have the same size. expected=" + expectedWidth + "x" + expectedHeight + ", actual=" + source.getDiffuseImage().getWidth() + "x" + source.getDiffuseImage().getHeight());
            }
        }
    }

    public void remapUv() {
        for (TextureAtlasEntry entry : entries) {
            remapUv(entry);
        }
    }

    private void remapUv(TextureAtlasEntry entry) {
        for (GaiaPrimitive primitive : entry.getPrimitives()) {
            remapPrimitiveUv(primitive, entry);
        }
    }

    private void remapPrimitiveUv(GaiaPrimitive primitive, TextureAtlasEntry entry) {
        if (primitive == null) {
            return;
        }

        List<GaiaVertex> vertices = primitive.getVertices();
        for (GaiaVertex vertex : vertices) {
            if (vertex == null || vertex.getTexcoords() == null) {
                continue;
            }
            Vector2d uv = vertex.getTexcoords();
            uv.x = entry.getUOffset() + uv.x * entry.getUScale();
            uv.y = entry.getVOffset() + uv.y * entry.getVScale();
        }
    }
}