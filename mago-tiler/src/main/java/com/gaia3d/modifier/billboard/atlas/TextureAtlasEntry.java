package com.gaia3d.modifier.billboard.atlas;

import com.gaia3d.basic.model.GaiaMaterial;
import com.gaia3d.basic.model.GaiaPrimitive;
import lombok.Getter;
import lombok.Setter;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TextureAtlasEntry {
    private GaiaMaterial material;
    private List<GaiaPrimitive> primitives = new ArrayList<>();

    private BufferedImage diffuseImage;
    private int width;
    private int height;

    private int index;
    private int row;
    private int col;
    private int x;
    private int y;

    private double uOffset;
    private double vOffset;
    private double uScale;
    private double vScale;
}