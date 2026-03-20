package com.gaia3d.modifier.billboard.merge;

import org.joml.Vector3d;

public class MergeProjection {
    Vector3d center;
    Vector3d normal;
    Vector3d tangent;
    Vector3d bitangent;

    double minU;
    double minV;
    double maxU;
    double maxV;
    double depthMin;
    double depthMax;

    double getRectArea() {
        return Math.max(0.0, maxU - minU) * Math.max(0.0, maxV - minV);
    }
}
