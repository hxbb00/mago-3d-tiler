package com.gaia3d.modifier.billboard.atlas;

import com.gaia3d.basic.model.GaiaMaterial;
import com.gaia3d.basic.model.GaiaPrimitive;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TextureAtlasSource {
    private GaiaMaterial material;
    private List<GaiaPrimitive> primitives = new ArrayList<>();
    private BufferedImage diffuseImage;
}
