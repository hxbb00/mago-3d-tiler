package com.gaia3d.modifier.billboard;

import com.gaia3d.basic.geometry.entities.GaiaPlane;
import com.gaia3d.basic.model.GaiaFace;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
public class BillboardPlanePostMergerBackup {

    public List<BillboardPlane> merge(List<BillboardPlane> inputPlanes, MergeConfig config) {
        if (inputPlanes == null || inputPlanes.isEmpty()) {
            return Collections.emptyList();
        }

        List<MergeNode> nodes = new ArrayList<>();
        for (int i = 0; i < inputPlanes.size(); i++) {
            BillboardPlane plane = inputPlanes.get(i);
            if (plane == null) {
                continue;
            }
            nodes.add(MergeNode.from(i, plane));
        }

        boolean merged;
        do {
            merged = false;

            double bestScore = -1.0;
            int bestI = -1;
            int bestJ = -1;
            MergeProjection bestProjection = null;

            int nodeSize = nodes.size();
            for (int i = 0; i < nodes.size(); i++) {
                log.info("[{}/{}] Evaluating merges... Current active nodes", i, nodeSize);
                MergeNode a = nodes.get(i);
                if (!a.active) {
                    continue;
                }

                for (int j = i + 1; j < nodes.size(); j++) {
                    MergeNode b = nodes.get(j);
                    if (!b.active) {
                        continue;
                    }

                    MergeEvaluation evaluation = evaluateMerge(a, b, config);
                    if (!evaluation.mergeable) {
                        continue;
                    }

                    if (evaluation.score > bestScore) {
                        bestScore = evaluation.score;
                        bestI = i;
                        bestJ = j;
                        bestProjection = evaluation.projection;
                    }
                }
            }

            if (bestI >= 0 && bestJ >= 0 && bestProjection != null) {
                MergeNode a = nodes.get(bestI);
                MergeNode b = nodes.get(bestJ);

                MergeNode mergedNode = mergeNodes(a, b, bestProjection);

                a.active = false;
                b.active = false;
                nodes.add(mergedNode);
                merged = true;
            }

        } while (merged);

        List<BillboardPlane> result = new ArrayList<>();
        for (MergeNode node : nodes) {
            if (node.active) {
                result.add(node.toBillboardPlane());
            }
        }
        return result;
    }

    private MergeEvaluation evaluateMerge(MergeNode a, MergeNode b, MergeConfig config) {
        /*double centerDistance = a.center.distance(b.center);
        if (centerDistance > config.maxCenterDistance) {
            return MergeEvaluation.fail();
        }*/

        double centerDistance = a.center.distanceSquared(b.center);
        if (centerDistance > config.maxCenterDistanceSquared) {
            return MergeEvaluation.fail();
        }

        double distAB = distancePointToPlane(a.center, b.center, b.normal);
        double distBA = distancePointToPlane(b.center, a.center, a.normal);
        double planeDistance = Math.max(distAB, distBA);
        if (planeDistance > config.maxPlaneDistance) {
            return MergeEvaluation.fail();
        }

        double normalDot = Math.abs(a.normal.dot(b.normal));
        if (normalDot < config.minNormalDot) {
            return MergeEvaluation.fail();
        }

        MergeProjection projection = computeMergedProjection(a, b);

        double mergedRectArea = projection.getRectArea();
        if (mergedRectArea <= 1e-12) {
            return MergeEvaluation.fail();
        }

        double occupiedArea = a.getRectArea() + b.getRectArea();
        double efficiency = occupiedArea / mergedRectArea;
        if (efficiency < config.minEfficiency) {
            return MergeEvaluation.fail();
        }

        double thickness = projection.depthMax - projection.depthMin;
        if (thickness > config.maxThickness) {
            return MergeEvaluation.fail();
        }

        double normalScore = remap(normalDot, config.minNormalDot, 1.0);
        double planeScore = 1.0 - clamp01(planeDistance / config.maxPlaneDistance);
        double efficiencyScore = remap(efficiency, config.minEfficiency, 1.0);
        double thicknessScore = 1.0 - clamp01(thickness / config.maxThickness);

        double score = normalScore * 0.35 + planeScore * 0.20 + efficiencyScore * 0.30 + thicknessScore * 0.15;

        MergeEvaluation evaluation = new MergeEvaluation();
        evaluation.mergeable = true;
        evaluation.score = score;
        evaluation.normalDot = normalDot;
        evaluation.planeDistance = planeDistance;
        evaluation.efficiency = efficiency;
        evaluation.thickness = thickness;
        evaluation.projection = projection;
        return evaluation;
    }

    private MergeProjection computeMergedProjection(MergeNode a, MergeNode b) {
        MergeProjection projection = new MergeProjection();

        Vector3d mergedNormal = new Vector3d(a.normal).add(b.normal);
        if (mergedNormal.lengthSquared() < 1e-12) {
            mergedNormal.set(a.normal);
        }
        mergedNormal.normalize();

        Vector3d mergedCenter = new Vector3d(a.center).add(b.center).mul(0.5);

        Vector3d tangent = buildStableTangent(mergedNormal);
        Vector3d bitangent = new Vector3d(mergedNormal).cross(tangent).normalize();

        projection.center = mergedCenter;
        projection.normal = mergedNormal;
        projection.tangent = tangent;
        projection.bitangent = bitangent;

        projection.minU = Double.POSITIVE_INFINITY;
        projection.minV = Double.POSITIVE_INFINITY;
        projection.maxU = Double.NEGATIVE_INFINITY;
        projection.maxV = Double.NEGATIVE_INFINITY;
        projection.depthMin = Double.POSITIVE_INFINITY;
        projection.depthMax = Double.NEGATIVE_INFINITY;

        updateProjectionBounds(projection, a);
        updateProjectionBounds(projection, b);

        return projection;
    }

    private void updateProjectionBounds(MergeProjection projection, MergeNode node) {
        List<Vector3d> points = node.getSamplePoints();
        for (Vector3d point : points) {
            Vector3d diff = new Vector3d(point).sub(projection.center);

            double u = diff.dot(projection.tangent);
            double v = diff.dot(projection.bitangent);
            double d = diff.dot(projection.normal);

            if (u < projection.minU) {projection.minU = u;}
            if (u > projection.maxU) {projection.maxU = u;}
            if (v < projection.minV) {projection.minV = v;}
            if (v > projection.maxV) {projection.maxV = v;}
            if (d < projection.depthMin) {projection.depthMin = d;}
            if (d > projection.depthMax) {projection.depthMax = d;}
        }
    }

    private MergeNode mergeNodes(MergeNode a, MergeNode b, MergeProjection projection) {
        MergeNode merged = new MergeNode();
        merged.id = Math.min(a.id, b.id);
        merged.active = true;

        merged.sourcePlanes = new ArrayList<>();
        merged.sourcePlanes.addAll(a.sourcePlanes);
        merged.sourcePlanes.addAll(b.sourcePlanes);

        merged.center = new Vector3d(projection.center);
        merged.normal = new Vector3d(projection.normal);
        merged.tangent = new Vector3d(projection.tangent);
        merged.bitangent = new Vector3d(projection.bitangent);

        merged.minU = projection.minU;
        merged.minV = projection.minV;
        merged.maxU = projection.maxU;
        merged.maxV = projection.maxV;
        merged.depthMin = projection.depthMin;
        merged.depthMax = projection.depthMax;

        merged.samplePoints = new ArrayList<>();
        merged.samplePoints.addAll(a.samplePoints);
        merged.samplePoints.addAll(b.samplePoints);

        merged.faces = new ArrayList<>();
        merged.faces.addAll(a.faces);
        merged.faces.addAll(b.faces);
        //merged.faces = new ArrayList<>(new LinkedHashSet<>(merged.faces));
        merged.plane = new GaiaPlane(merged.center, merged.normal);

        return merged;
    }

    private double distancePointToPlane(Vector3d point, Vector3d planePoint, Vector3d planeNormal) {
        return Math.abs(new Vector3d(point).sub(planePoint).dot(planeNormal));
    }

    private Vector3d buildStableTangent(Vector3d normal) {
        Vector3d up = Math.abs(normal.z) < 0.9 ? new Vector3d(0.0, 0.0, 1.0) : new Vector3d(0.0, 1.0, 0.0);

        Vector3d tangent = up.cross(normal, new Vector3d());
        if (tangent.lengthSquared() < 1e-12) {
            tangent = new Vector3d(1.0, 0.0, 0.0).cross(normal, new Vector3d());
        }
        tangent.normalize();
        return tangent;
    }

    private double remap(double value, double min, double max) {
        if (max <= min) {
            return 1.0;
        }
        return clamp01((value - min) / (max - min));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static class MergeConfig {
        public double minNormalDot = Math.cos(Math.toRadians(30)); // 법선 유사도 기준
        public double maxPlaneDistance = 0.1;
        public double minEfficiency = 0.1;
        public double maxThickness = 0.1;
        public double maxCenterDistance = 0.1;
        public double maxCenterDistanceSquared = maxCenterDistance * maxCenterDistance;

        public static MergeConfig defaultConfig() {
            return new MergeConfig();
        }
    }

    private static class MergeEvaluation {
        boolean mergeable;
        double score;
        double normalDot;
        double planeDistance;
        double efficiency;
        double thickness;
        MergeProjection projection;

        static MergeEvaluation fail() {
            MergeEvaluation evaluation = new MergeEvaluation();
            evaluation.mergeable = false;
            evaluation.score = -1.0;
            return evaluation;
        }
    }

    private static class MergeProjection {
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

    private static class MergeNode {
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

            //plane.setFaces(new ArrayList<>(faces));
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

            // 필요하면 sourcePlanes 기반 추가 메타데이터도 합칠 수 있음.
            return plane;
        }

        List<Vector3d> getSamplePoints() {
            return samplePoints;
        }

        double getRectArea() {
            return Math.max(0.0, maxU - minU) * Math.max(0.0, maxV - minV);
        }

        private Vector3d buildFallbackTangent(Vector3d normal) {
            Vector3d up = Math.abs(normal.z) < 0.9 ? new Vector3d(0.0, 0.0, 1.0) : new Vector3d(0.0, 1.0, 0.0);

            Vector3d tangent = up.cross(normal, new Vector3d());
            if (tangent.lengthSquared() < 1e-12) {
                tangent = new Vector3d(1.0, 0.0, 0.0).cross(normal, new Vector3d());
            }
            tangent.normalize();
            return tangent;
        }
    }

    public class GridKey {
        public final int x;
        public final int y;
        public final int z;

        public GridKey(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GridKey key)) return false;
            return x == key.x && y == key.y && z == key.z;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * x + y) + z;
        }
    }
}
