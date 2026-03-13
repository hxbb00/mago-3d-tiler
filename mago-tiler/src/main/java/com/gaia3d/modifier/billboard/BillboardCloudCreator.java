package com.gaia3d.modifier.billboard;

import com.gaia3d.basic.geometry.entities.GaiaPlane;
import com.gaia3d.basic.geometry.entities.GaiaTriangle;
import com.gaia3d.basic.geometry.modifier.topology.GaiaExtractor;
import com.gaia3d.basic.model.*;
import com.gaia3d.basic.types.TextureType;
import com.gaia3d.modifier.billboard.atlas.TextureAtlas;
import com.gaia3d.modifier.billboard.atlas.TextureAtlasSource;
import com.gaia3d.modifier.billboard.atlas.TextureAtlasUtils;
import com.gaia3d.modifier.billboard.render.OrthographicProjection;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2d;
import org.joml.Vector3d;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

@Slf4j
public class BillboardCloudCreator {

    private final BillboardCloudOptions options;
    private final GaiaExtractor extractor;
    private final Renderer4TextureBake renderer;

    public BillboardCloudCreator() {
        this.options = BillboardCloudOptions.builder().build();
        extractor = new GaiaExtractor();
        renderer = new Renderer4TextureBake(options);
    }

    public BillboardCloudCreator(BillboardCloudOptions options) {
        this.options = options;
        extractor = new GaiaExtractor();
        renderer = new Renderer4TextureBake(options);
    }


    public GaiaScene createBillboardCloud(GaiaScene scene) {
        List<GaiaNode> leafNodes = extractor.extractAllNodes(scene, true);

        GaiaScene resultScene = createDefaultScene();
        GaiaNode rootNode = resultScene.getRootNode();

        int nodeIndex = 0;
        for (GaiaNode node : leafNodes) {
            log.info("[{}/{}] Processing node: {}", ++nodeIndex, leafNodes.size(), node.getName());
            List<GaiaPrimitive> primitives = extractor.extractAllPrimitives(node);
            log.info("Extracted {} primitives from node: {}", primitives.size(), node.getName());

            for (GaiaPrimitive primitive : primitives) {
                List<BillboardPlane> billboardPlanes = createBillboardCloud(primitive);
                log.info("Created {} billboard planes from primitive", billboardPlanes.size());
                GaiaMaterial material = scene.getMaterials().get(primitive.getMaterialIndex());

                GaiaNode billboardNode = new GaiaNode();
                billboardNode.setName(node.getName() + "_billboard");
                rootNode.getChildren().add(billboardNode);

                GaiaMesh billboardMesh = new GaiaMesh();
                billboardNode.getMeshes().add(billboardMesh);

                List<GaiaPrimitive> newPrimitives = buildBillboardPrimitive(resultScene, billboardPlanes, primitive.getVertices(), material);
                for (GaiaPrimitive newPrimitive : newPrimitives) {
                    billboardMesh.getPrimitives().add(newPrimitive);
                }
            }
        }

        atlasTextures(resultScene);
        mergePrimitives(resultScene);

        return resultScene;
    }

    private void mergePrimitives(GaiaScene resultScene) {
        List<GaiaNode> leafNodes = extractor.extractAllNodes(resultScene, true);
        for (GaiaNode node : leafNodes) {
            List<GaiaMesh> meshes = node.getMeshes();

            for (GaiaMesh mesh : meshes) {
                List<GaiaPrimitive> meshPrimitives = mesh.getPrimitives();
                GaiaPrimitive mergedPrimitive = TextureAtlasUtils.createMergedPrimitives(meshPrimitives);
                meshPrimitives.clear();
                meshPrimitives.add(mergedPrimitive);
            }
        }
    }

    private void atlasTextures(GaiaScene resultScene) {
        List<TextureAtlasSource> sources = new ArrayList<>();
        for (GaiaMaterial material : resultScene.getMaterials()) {
            List<GaiaPrimitive> primitives = TextureAtlasUtils.findPrimitivesUsingMaterial(resultScene, material);
            BufferedImage diffuseImage = TextureAtlasUtils.loadDiffuseImage(material);
            TextureAtlasSource source = new TextureAtlasSource(material, primitives, diffuseImage);
            sources.add(source);
        }
        TextureAtlas atlas = new TextureAtlas();
        BufferedImage atlasImage = atlas.build(sources);
        atlas.remapUv();

        GaiaMaterial atlasMaterial = new GaiaMaterial();
        atlasMaterial.setId(0);
        atlasMaterial.setName("atlas");
        //atlasMaterial.setBlend(true);
        //atlasMaterial.setOpaque(false);
        File atlasFile = new File(options.getTempPath() + File.separator + "atlas.png");
        try {
            ImageIO.write(atlasImage, "PNG", atlasFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        GaiaTexture atlasTexture = new GaiaTexture();
        atlasTexture.setName("atlas");
        atlasTexture.setParentPath(options.getTempPath());
        atlasTexture.setPath("atlas.png");
        atlasTexture.loadImage();
        atlasMaterial.getTextures().computeIfAbsent(TextureType.DIFFUSE, k -> new ArrayList<>()).add(atlasTexture);

        resultScene.getMaterials().clear();
        resultScene.getMaterials().add(atlasMaterial);
        TextureAtlasUtils.changePrimitivesMaterialId(resultScene, atlasMaterial.getId());
    }

    private List<GaiaPrimitive> buildBillboardPrimitive(GaiaScene scene, List<BillboardPlane> billboardPlanes, List<GaiaVertex> sourceVertices, GaiaMaterial originalMaterial) {
        Map<TextureType, List<GaiaTexture>> materialTextures = originalMaterial.getTextures();
        List<GaiaTexture> diffuseTextures = materialTextures.getOrDefault(TextureType.DIFFUSE, Collections.emptyList());
        GaiaTexture finalDiffuseTexture = null;
        BufferedImage finalDiffuseTextureImage = null;
        if (!diffuseTextures.isEmpty()) {
            finalDiffuseTexture = diffuseTextures.getFirst();
            finalDiffuseTexture.loadImage();
            finalDiffuseTextureImage = finalDiffuseTexture.getBufferedImage();
        }

        List<GaiaPrimitive> primitives = new ArrayList<>();
        List<GaiaMaterial> materials = scene.getMaterials();
        List<OrthographicProjection> projections = new ArrayList<>();
        for (BillboardPlane billboardPlane : billboardPlanes) {
            GaiaPrimitive targetPrimitive = new GaiaPrimitive();
            primitives.add(targetPrimitive);

            List<GaiaVertex> targetVertices = targetPrimitive.getVertices();
            List<GaiaSurface> targetSurfaces = targetPrimitive.getSurfaces();

            GaiaSurface surface = new GaiaSurface();
            targetSurfaces.add(surface);

            GaiaMaterial material = new GaiaMaterial();
            int materialIndex = materials.size();
            String materialName = "billboard_material_" + materialIndex;
            material.setId(materialIndex);
            material.setName(materialName);
            //material.setOpaque(false);
            //material.setBlend(true);
            materials.add(material);

            targetPrimitive.setMaterialIndex(materialIndex);

            OrthographicProjection orthographicProjection = addBillboardQuad(billboardPlane, sourceVertices, targetVertices, surface);
            projections.add(orthographicProjection);
            log.debug("Added billboard quad for plane with center {} and normal {}, orthographic projection: {}", billboardPlane.getCenter(), billboardPlane.getNormal(), orthographicProjection);

            renderer.init();
            if (orthographicProjection != null) {
                log.debug("Baking texture for billboard plane with center {} and normal {}", billboardPlane.getCenter(), billboardPlane.getNormal());

                renderer.bakeBillboardPlane(billboardPlane, sourceVertices, orthographicProjection, finalDiffuseTextureImage);
                String parentPath = options.getTempPath();
                String textureName = materialName + ".png";
                String texturePath = parentPath + File.separator + textureName;
                renderer.saveCurrentFboToPng(texturePath);

                GaiaTexture texture = new GaiaTexture();
                texture.setName(textureName);
                texture.setParentPath(parentPath);
                texture.setPath(textureName);
                material.getTextures().computeIfAbsent(TextureType.DIFFUSE, k -> new ArrayList<>()).add(texture);
                texture.loadImage();
            }
            renderer.cleanup();
        }

        return primitives;
    }

    private OrthographicProjection addBillboardQuad(BillboardPlane billboardPlane, List<GaiaVertex> sourceVertices, List<GaiaVertex> targetVertices, GaiaSurface targetSurface) {
        if (billboardPlane == null) {
            return null;
        }

        Vector3d normal = new Vector3d(billboardPlane.getNormal()).normalize();

        Vector3d origin = projectPointOntoPlane(new Vector3d(billboardPlane.getCenter()), billboardPlane.getPlane());

        Vector3d tangent = new Vector3d(billboardPlane.getTangent());
        Vector3d bitangent = new Vector3d(billboardPlane.getBitangent());

        double minU = billboardPlane.getMinU();
        double minV = billboardPlane.getMinV();
        double maxU = billboardPlane.getMaxU();
        double maxV = billboardPlane.getMaxV();

        double width = maxU - minU;
        double height = maxV - minV;

        if (width <= 1e-9 || height <= 1e-9) {
            return null;
        }

        // quad corners
        Vector3d p0 = new Vector3d(origin).add(new Vector3d(tangent).mul(minU)).add(new Vector3d(bitangent).mul(minV));
        Vector3d p1 = new Vector3d(origin).add(new Vector3d(tangent).mul(maxU)).add(new Vector3d(bitangent).mul(minV));
        Vector3d p2 = new Vector3d(origin).add(new Vector3d(tangent).mul(maxU)).add(new Vector3d(bitangent).mul(maxV));
        Vector3d p3 = new Vector3d(origin).add(new Vector3d(tangent).mul(minU)).add(new Vector3d(bitangent).mul(maxV));

        int baseIndex = targetVertices.size();

        GaiaVertex v0 = createVertex(p0, normal, 0.0, 1.0);
        GaiaVertex v1 = createVertex(p1, normal, 1.0, 1.0);
        GaiaVertex v2 = createVertex(p2, normal, 1.0, 0.0);
        GaiaVertex v3 = createVertex(p3, normal, 0.0, 0.0);

        targetVertices.add(v0);
        targetVertices.add(v1);
        targetVertices.add(v2);
        targetVertices.add(v3);

        GaiaFace face0 = new GaiaFace();
        face0.setIndices(new int[]{baseIndex, baseIndex + 1, baseIndex + 2});

        GaiaFace face1 = new GaiaFace();
        face1.setIndices(new int[]{baseIndex, baseIndex + 2, baseIndex + 3});

        targetSurface.getFaces().add(face0);
        targetSurface.getFaces().add(face1);

        Vector3d obbCenter = new Vector3d(p0).add(p1).add(p2).add(p3).mul(0.25);

        double planeSize = Math.max(width, height);

        double cameraDistance = planeSize * 2.0;

        Vector3d cameraPos = new Vector3d(obbCenter).add(new Vector3d(normal).mul(cameraDistance));
        Vector3d forward = new Vector3d(obbCenter).sub(cameraPos).normalize();

        double minDepth = Double.POSITIVE_INFINITY;
        double maxDepth = Double.NEGATIVE_INFINITY;

        for (GaiaFace face : billboardPlane.getFaces()) {
            List<GaiaVertex> faceVertices = getVerticesFromFace(face, sourceVertices);

            for (GaiaVertex vertex : faceVertices) {
                Vector3d p = new Vector3d(vertex.getPosition());
                double depth = new Vector3d(p).sub(cameraPos).dot(forward);

                if (depth < minDepth) minDepth = depth;
                if (depth > maxDepth) maxDepth = depth;
            }
        }

        double margin = planeSize * 0.1;

        double near = Math.max(0.01, minDepth - margin);
        double far = maxDepth + margin;

        // projection 객체 생성
        OrthographicProjection projection = new OrthographicProjection();

        projection.setCameraPosition(cameraPos);
        projection.setTarget(obbCenter);
        projection.setUp(bitangent);

        double halfWidth = width * 0.5;
        double halfHeight = height * 0.5;

        projection.setLeft(-halfWidth);
        projection.setRight(halfWidth);
        projection.setBottom(-halfHeight);
        projection.setTop(halfHeight);

        projection.setNear(near);
        projection.setFar(far);

        projection.setBitangent(bitangent);
        projection.setTangent(tangent);
        projection.setNormal(normal);

        return projection;
    }

    private Vector3d createStableTangent(Vector3d normal) {
        Vector3d ref = Math.abs(normal.z) < 0.9 ? new Vector3d(0.0, 0.0, 1.0) : new Vector3d(0.0, 1.0, 0.0);

        Vector3d tangent = ref.cross(normal, new Vector3d());
        if (tangent.lengthSquared() == 0.0) {
            ref.set(1.0, 0.0, 0.0);
            tangent = ref.cross(normal, new Vector3d());
        }
        tangent.normalize();
        return tangent;
    }

    private GaiaVertex createVertex(Vector3d position, Vector3d normal, double u, double v) {
        GaiaVertex vertex = new GaiaVertex();
        vertex.setPosition(new Vector3d(position));
        vertex.setNormal(new Vector3d(normal));
        vertex.setTexcoords(new Vector2d(u, v));
        return vertex;
    }

    private Vector3d projectPointOntoPlane(Vector3d point, GaiaPlane plane) {
        Vector3d normal = plane.getNormal();
        double lenSq = normal.lengthSquared();
        if (lenSq == 0.0) {
            return new Vector3d(point);
        }

        double distance = plane.distanceToPoint(point); // signed distance * |n| if n not normalized
        return new Vector3d(point).sub(new Vector3d(normal).mul(distance / lenSq));
    }

    public List<BillboardPlane> createBillboardCloud(GaiaPrimitive primitive) {
        List<GaiaVertex> vertices = primitive.getVertices();
        List<GaiaFace> faces = extractor.extractAllFaces(primitive);

        List<GaiaFace> filteredFaces = new ArrayList<>();
        for (GaiaFace face : faces) {
            GaiaTriangle triangle = new GaiaTriangle(vertices.get(face.getIndices()[0]).getPosition(), vertices.get(face.getIndices()[1]).getPosition(), vertices.get(face.getIndices()[2]).getPosition());
            double seedArea = triangle.area();
            if (seedArea < options.getMinTriangleArea()) {
                log.info("Skipping face with area {} below threshold {}, indices: {}", seedArea, options.getMinTriangleArea(), face.getIndices());
                continue;
            }
            filteredFaces.add(face);
        }

        // arrange triangle size 순으로 정렬해서 billboard plane 생성 시 작은 triangle이 seed가 되는 경우를 줄임
        /*filteredFaces.sort(Comparator.comparingDouble(face -> {
            GaiaTriangle triangle = new GaiaTriangle(vertices.get(face.getIndices()[0]).getPosition(), vertices.get(face.getIndices()[1]).getPosition(), vertices.get(face.getIndices()[2]).getPosition());
            return triangle.area();
        }));*/

        return createBillboardPlanes(filteredFaces, vertices);
    }

    private GaiaScene createDefaultScene() {
        GaiaScene scene = new GaiaScene();
        GaiaNode node = new GaiaNode();
        node.setName("DefaultNode");
        scene.getNodes().add(node);

        List<GaiaMaterial> materials = new ArrayList<>();
        scene.setMaterials(materials);
        scene.updateBoundingBox();
        return scene;
    }

    private List<BillboardPlane> createBillboardPlanes(List<GaiaFace> faces, List<GaiaVertex> vertices) {
        List<BillboardPlane> billboardPlanes = new ArrayList<>();
        Set<Integer> usedFaceIndices = new HashSet<>();

        for (int i = 0; i < faces.size(); i++) {
            if (usedFaceIndices.contains(i)) {
                continue;
            }

            BillboardPlane billboardPlane = createBillboardPlane(faces, vertices, i, usedFaceIndices);
            if (billboardPlane != null) {
                billboardPlanes.add(billboardPlane);
            }
        }

        log.debug("Created {} billboard planes from {} faces", billboardPlanes.size(), faces.size());
        return billboardPlanes;
    }

    private BillboardPlane createBillboardPlane(List<GaiaFace> faces, List<GaiaVertex> vertices, int seedFaceIndex, Set<Integer> usedFaceIndices) {
        GaiaFace seedFace = faces.get(seedFaceIndex);
        List<GaiaVertex> seedVertices = getVerticesFromFace(seedFace, vertices);
        if (seedVertices.size() < 3) {
            return null;
        }

        GaiaTriangle seedTriangle = new GaiaTriangle(seedVertices.get(0).getPosition(), seedVertices.get(1).getPosition(), seedVertices.get(2).getPosition());

        GaiaPlane seedPlane = seedTriangle.getPlane();
        Vector3d seedNormal = safeNormalize(seedPlane.getNormal());
        Vector3d seedCentroid = calculateCentroid(seedVertices);

        if (seedNormal == null || seedCentroid == null) {
            return null;
        }

        List<GaiaFace> groupedFaces = new ArrayList<>();
        groupedFaces.add(seedFace);
        usedFaceIndices.add(seedFaceIndex);

        for (int i = 0; i < faces.size(); i++) {
            if (i == seedFaceIndex || usedFaceIndices.contains(i)) {
                continue;
            }

            GaiaFace candidateFace = faces.get(i);
            List<GaiaVertex> candidateVertices = getVerticesFromFace(candidateFace, vertices);
            if (candidateVertices.size() < 3) {
                continue;
            }

            GaiaTriangle candidateTriangle = new GaiaTriangle(candidateVertices.get(0).getPosition(), candidateVertices.get(1).getPosition(), candidateVertices.get(2).getPosition());

            GaiaPlane candidatePlane = candidateTriangle.getPlane();
            Vector3d candidateNormal = safeNormalize(candidatePlane.getNormal());
            Vector3d candidateCentroid = calculateCentroid(candidateVertices);

            if (candidateNormal == null || candidateCentroid == null) {
                continue;
            }

            // 1. normal 유사도 (양면 leaf 고려)
            double dot = Math.abs(seedNormal.dot(candidateNormal));
            if (dot < options.getNormalDotThreshold()) {
                continue;
            }

            // 2. candidate centroid가 seed plane에 가까운지
            double planeDistance = Math.abs(seedPlane.distanceToPoint(candidateCentroid));
            if (planeDistance > options.getPlaneDistanceEpsilon()) {
                continue;
            }

            // 3. candidate centroid가 seed centroid와 너무 멀지 않은지
            double centroidDistance = seedCentroid.distance(candidateCentroid);
            if (centroidDistance > options.getSetRadius()) {
                continue;
            }

            groupedFaces.add(candidateFace);
            usedFaceIndices.add(i);
        }

        BillboardPlane billboardPlane = new BillboardPlane();

        billboardPlane.setPlane(seedPlane);

        // BillboardPlane에 아래 필드가 있으면 같이 넣는 걸 추천
        billboardPlane.setFaces(groupedFaces);
        billboardPlane.setCenter(seedCentroid);
        billboardPlane.setNormal(seedNormal);

        // 1. groupedFaces 기반으로 normal / center 보정
        if (options.isRefineBillboardPlane()) {
            refineBillboardPlane(billboardPlane, vertices);
        }

        // 2. 보정된 plane 위에서 OBB 계산
        computePlaneOBB(billboardPlane, vertices);

        log.debug("Seed face {} -> grouped {} faces", seedFaceIndex, groupedFaces.size());
        return billboardPlane;
    }

    private List<GaiaVertex> getVerticesFromFace(GaiaFace face, List<GaiaVertex> allVertices) {
        int[] vertexIndices = face.getIndices();
        List<GaiaVertex> vertices = new ArrayList<>(vertexIndices.length);

        for (int index : vertexIndices) {
            if (index >= 0 && index < allVertices.size()) {
                vertices.add(allVertices.get(index));
            } else {
                log.warn("Vertex index {} is out of bounds for vertices list size {}", index, allVertices.size());
            }
        }
        return vertices;
    }

    private Vector3d calculateCentroid(List<GaiaVertex> vertices) {
        if (vertices == null || vertices.isEmpty()) {
            return null;
        }

        Vector3d centroid = new Vector3d();
        for (GaiaVertex vertex : vertices) {
            centroid.add(vertex.getPosition());
        }
        centroid.div(vertices.size());
        return centroid;
    }

    private Vector3d safeNormalize(Vector3d v) {
        if (v == null || v.lengthSquared() == 0.0) {
            return null;
        }
        return v.normalize(new Vector3d());
    }

    private void refineBillboardPlane(BillboardPlane billboardPlane, List<GaiaVertex> sourceVertices) {
        List<GaiaFace> faces = billboardPlane.getFaces();
        if (faces == null || faces.isEmpty()) {
            return;
        }

        Vector3d normalSum = new Vector3d();
        Vector3d centerSum = new Vector3d();
        int pointCount = 0;

        for (GaiaFace face : faces) {
            List<GaiaVertex> faceVertices = getVerticesFromFace(face, sourceVertices);
            if (faceVertices.size() < 3) {
                continue;
            }

            GaiaTriangle triangle = new GaiaTriangle(faceVertices.get(0).getPosition(), faceVertices.get(1).getPosition(), faceVertices.get(2).getPosition());

            Vector3d n = triangle.getPlane().getNormal();
            if (n.lengthSquared() > 0.0) {
                n.normalize();

                if (normalSum.lengthSquared() > 0.0 && normalSum.dot(n) < 0.0) {
                    n.negate();
                }
                normalSum.add(n);
            }

            for (GaiaVertex v : faceVertices) {
                centerSum.add(v.getPosition());
                pointCount++;
            }
        }

        if (normalSum.lengthSquared() == 0.0 || pointCount == 0) {
            return;
        }

        Vector3d refinedNormal = normalSum.normalize();
        Vector3d refinedCenter = centerSum.div(pointCount);

        billboardPlane.setNormal(refinedNormal);
        billboardPlane.setCenter(refinedCenter);
        billboardPlane.setPlane(new GaiaPlane(refinedCenter, refinedNormal));
    }

    private void computePlaneOBB(BillboardPlane billboardPlane, List<GaiaVertex> sourceVertices) {
        if (billboardPlane == null || billboardPlane.getFaces() == null || billboardPlane.getFaces().isEmpty()) {
            return;
        }

        Vector3d normal = new Vector3d(billboardPlane.getNormal());
        if (normal.lengthSquared() == 0.0) {
            return;
        }
        normal.normalize();

        Vector3d origin = projectPointOntoPlane(new Vector3d(billboardPlane.getCenter()), billboardPlane.getPlane());

        Vector3d tempTangent = createStableTangent(normal);
        Vector3d tempBitangent = new Vector3d(normal).cross(tempTangent).normalize();

        List<Vector2d> points2D = collectProjectedPoints2D(billboardPlane, sourceVertices, origin, tempTangent, tempBitangent);

        if (points2D.isEmpty()) {
            return;
        }

        double meanX = 0.0;
        double meanY = 0.0;
        for (Vector2d p : points2D) {
            meanX += p.x;
            meanY += p.y;
        }
        meanX /= points2D.size();
        meanY /= points2D.size();

        double xx = 0.0;
        double xy = 0.0;
        double yy = 0.0;
        for (Vector2d p : points2D) {
            double dx = p.x - meanX;
            double dy = p.y - meanY;
            xx += dx * dx;
            xy += dx * dy;
            yy += dy * dy;
        }

        double angle = 0.5 * Math.atan2(2.0 * xy, xx - yy);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        Vector3d tangent = new Vector3d(tempTangent).mul(cos).add(new Vector3d(tempBitangent).mul(sin)).normalize();
        Vector3d bitangent = new Vector3d(normal).cross(tangent).normalize();

        double minU = Double.POSITIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;

        for (GaiaFace face : billboardPlane.getFaces()) {
            List<GaiaVertex> faceVertices = getVerticesFromFace(face, sourceVertices);
            for (GaiaVertex vertex : faceVertices) {
                Vector3d projected = projectPointOntoPlane(new Vector3d(vertex.getPosition()), billboardPlane.getPlane());
                Vector3d diff = new Vector3d(projected).sub(origin);

                double u = diff.dot(tangent);
                double v = diff.dot(bitangent);

                if (u < minU) {minU = u;}
                if (u > maxU) {maxU = u;}
                if (v < minV) {minV = v;}
                if (v > maxV) {maxV = v;}
            }
        }

        billboardPlane.setTangent(tangent);
        billboardPlane.setBitangent(bitangent);
        billboardPlane.setMinU(minU);
        billboardPlane.setMinV(minV);
        billboardPlane.setMaxU(maxU);
        billboardPlane.setMaxV(maxV);
    }

    private List<Vector2d> collectProjectedPoints2D(BillboardPlane billboardPlane, List<GaiaVertex> sourceVertices, Vector3d origin, Vector3d tangent, Vector3d bitangent) {
        List<Vector2d> points2D = new ArrayList<>();

        for (GaiaFace face : billboardPlane.getFaces()) {
            List<GaiaVertex> faceVertices = getVerticesFromFace(face, sourceVertices);
            for (GaiaVertex vertex : faceVertices) {
                /*Vector3d projected = projectPointOntoPlane(new Vector3d(vertex.getPosition()), billboardPlane.getPlane());
                Vector3d diff = new Vector3d(projected).sub(origin);
                double u = diff.dot(tangent);
                double v = diff.dot(bitangent);*/

                Vector3d projected = projectPointOntoPlane(new Vector3d(vertex.getPosition()), billboardPlane.getPlane());
                Vector3d diff = new Vector3d(projected).sub(origin);
                double u = diff.dot(tangent);
                double v = diff.dot(bitangent);

                points2D.add(new Vector2d(u, v));
            }
        }

        return points2D;
    }
}