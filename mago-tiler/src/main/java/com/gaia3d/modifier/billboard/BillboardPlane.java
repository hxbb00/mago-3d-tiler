package com.gaia3d.modifier.billboard;

import com.gaia3d.basic.geometry.entities.GaiaPlane;
import com.gaia3d.basic.model.GaiaFace;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class BillboardPlane {
    private GaiaPlane plane = null;
    private List<GaiaFace> faces = new ArrayList<>();
    private Vector3d center = null;
    private Vector3d normal = null;

    // OBB / plane local basis
    private Vector3d tangent;
    private Vector3d bitangent;

    // bounds in plane local coordinates
    private double minU;
    private double minV;
    private double maxU;
    private double maxV;

    private double depthMin;
    private double depthMax;
}
