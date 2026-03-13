package com.gaia3d.modifier.billboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class BillboardCloudOptions {
    @Builder.Default
    private final double normalDotThreshold = Math.cos(Math.toRadians(30)); // 법선 유사도 기준
    @Builder.Default
    private final double planeDistanceEpsilon = 0.1; // 후보 평면이 시드 평면에서 얼마나 떨어져 있어야 하는지
    @Builder.Default
    private final double setRadius = 0.3; // 시드 반경
    @Builder.Default
    private final double minTriangleArea = 1e-6; // 삼각형 최소 면적
    @Builder.Default
    private final boolean refineBillboardPlane = false;
    @Builder.Default
    private final int maximumTextureSize = 128;
    @Builder.Default
    private final String tempPath = "H:\\workspace\\billboardclouds-output\\textures";
}
