package com.gaia3d.modifier.billboard.merge;

import com.gaia3d.basic.geometry.entities.GaiaPlane;
import com.gaia3d.basic.model.GaiaFace;
import com.gaia3d.modifier.billboard.BillboardPlane;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class MergeNode {
    int id;
    boolean active = true;

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

    GaiaPlane plane;
    List<GaiaFace> faces = new ArrayList<>();

    List<Vector3d> samplePoints = new ArrayList<>();
    List<BillboardPlane> sourcePlanes = new ArrayList<>();

    static MergeNode from(int id, BillboardPlane plane) {
        MergeNode node = new MergeNode();
        node.id = id;
        node.sourcePlanes.add(plane);

        node.center = new Vector3d(plane.getCenter());
        node.normal = new Vector3d(plane.getNormal()).normalize();

        if (plane.getTangent() != null) {
            node.tangent = new Vector3d(plane.getTangent()).normalize();
        } else {
            node.tangent = node.buildFallbackTangent(node.normal);
        }

        if (plane.getBitangent() != null) {
            node.bitangent = new Vector3d(plane.getBitangent()).normalize();
        } else {
            node.bitangent = new Vector3d(node.normal).cross(node.tangent).normalize();
        }

        node.minU = plane.getMinU();
        node.minV = plane.getMinV();
        node.maxU = plane.getMaxU();
        node.maxV = plane.getMaxV();

        node.depthMin = plane.getDepthMin();
        node.depthMax = plane.getDepthMax();

        node.plane = plane.getPlane();

        if (plane.getFaces() != null) {
            node.faces.addAll(plane.getFaces());
        }

        node.samplePoints = buildSamplePoints(plane, node.center, node.tangent, node.bitangent);

        return node;
    }

    private static List<Vector3d> buildSamplePoints(BillboardPlane plane, Vector3d center, Vector3d tangent, Vector3d bitangent) {
        List<Vector3d> points = new ArrayList<>(4);

        double minU = plane.getMinU();
        double minV = plane.getMinV();
        double maxU = plane.getMaxU();
        double maxV = plane.getMaxV();

        points.add(buildPoint(center, tangent, bitangent, minU, minV));
        points.add(buildPoint(center, tangent, bitangent, minU, maxV));
        points.add(buildPoint(center, tangent, bitangent, maxU, minV));
        points.add(buildPoint(center, tangent, bitangent, maxU, maxV));

        return points;
    }

    private static Vector3d buildPoint(Vector3d center, Vector3d tangent, Vector3d bitangent, double u, double v) {
        return new Vector3d(center).fma(u, tangent).fma(v, bitangent);
    }

    BillboardPlane toBillboardPlane() {
        BillboardPlane plane = new BillboardPlane();

        plane.setFaces(new ArrayList<>(new LinkedHashSet<>(faces)));
        plane.setPlane(new GaiaPlane(center, normal));
        plane.setCenter(new Vector3d(center));
        plane.setNormal(new Vector3d(normal));
        plane.setTangent(new Vector3d(tangent));
        plane.setBitangent(new Vector3d(bitangent));

        plane.setMinU(minU);
        plane.setMinV(minV);
        plane.setMaxU(maxU);
        plane.setMaxV(maxV);

        plane.setDepthMin(depthMin);
        plane.setDepthMax(depthMax);

        return plane;
    }

    List<Vector3d> getSamplePoints() {
        return samplePoints;
    }

    double getRectArea() {
        return Math.max(0.0, maxU - minU) * Math.max(0.0, maxV - minV);
    }

    private Vector3d buildFallbackTangent(Vector3d normal) {
        Vector3d up = Math.abs(normal.z) < 0.9
                ? new Vector3d(0.0, 0.0, 1.0)
                : new Vector3d(0.0, 1.0, 0.0);

        Vector3d tangent = up.cross(normal, new Vector3d());
        if (tangent.lengthSquared() < 1e-12) {
            tangent = new Vector3d(1.0, 0.0, 0.0).cross(normal, new Vector3d());
        }
        tangent.normalize();
        return tangent;
    }
}
