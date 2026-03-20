package com.gaia3d.modifier.billboard;

import com.gaia3d.basic.geometry.entities.GaiaPlane;
import com.gaia3d.basic.geometry.modifier.topology.GaiaExtractor;
import com.gaia3d.basic.model.*;
import com.gaia3d.basic.types.TextureType;
import com.gaia3d.modifier.billboard.atlas.ImageDilate;
import com.gaia3d.modifier.billboard.atlas.TextureAtlas;
import com.gaia3d.modifier.billboard.atlas.TextureAtlasSource;
import com.gaia3d.modifier.billboard.atlas.TextureAtlasUtils;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2d;
import org.joml.Vector3d;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
abstract public class AbstractBillboardCloudCreator {

    protected final BillboardCloudOptions options;
    protected final GaiaExtractor extractor;
    protected final Renderer4TextureBake renderer;

    protected AbstractBillboardCloudCreator() {
        this.options = BillboardCloudOptions.builder().build();
        extractor = new GaiaExtractor();
        renderer = new Renderer4TextureBake(options);
    }

    protected AbstractBillboardCloudCreator(BillboardCloudOptions options) {
        this.options = options;
        extractor = new GaiaExtractor();
        renderer = new Renderer4TextureBake(options);
    }

    abstract public GaiaScene create(GaiaScene scene);

    protected GaiaScene createDefaultScene() {
        GaiaScene scene = new GaiaScene();
        GaiaNode node = new GaiaNode();
        node.setName("DefaultNode");
        scene.getNodes().add(node);

        List<GaiaMaterial> materials = new ArrayList<>();
        scene.setMaterials(materials);
        scene.updateBoundingBox();
        return scene;
    }

    protected GaiaVertex createVertex(Vector3d position) {
        GaiaVertex vertex = new GaiaVertex();
        vertex.setPosition(new Vector3d(position));
        return vertex;
    }

    protected GaiaVertex createVertex(Vector3d position, double u, double v) {
        GaiaVertex vertex = new GaiaVertex();
        vertex.setPosition(new Vector3d(position));
        vertex.setTexcoords(new Vector2d(u, v));
        return vertex;
    }

    protected GaiaVertex createVertex(Vector3d position, Vector3d normal, double u, double v) {
        GaiaVertex vertex = new GaiaVertex();
        vertex.setPosition(new Vector3d(position));
        vertex.setNormal(new Vector3d(normal));
        vertex.setTexcoords(new Vector2d(u, v));
        return vertex;
    }

    protected Vector3d projectPointOntoPlane(Vector3d point, GaiaPlane plane) {
        Vector3d normal = plane.getNormal();
        double lenSq = normal.lengthSquared();
        if (lenSq == 0.0) {
            return new Vector3d(point);
        }

        double distance = plane.distanceToPoint(point); // signed distance * |n| if n not normalized
        return new Vector3d(point).sub(new Vector3d(normal).mul(distance / lenSq));
    }

    protected Vector3d createStableTangent(Vector3d normal) {
        Vector3d ref = Math.abs(normal.z) < 0.9 ? new Vector3d(0.0, 0.0, 1.0) : new Vector3d(0.0, 1.0, 0.0);

        Vector3d tangent = ref.cross(normal, new Vector3d());
        if (tangent.lengthSquared() == 0.0) {
            ref.set(1.0, 0.0, 0.0);
            tangent = ref.cross(normal, new Vector3d());
        }
        tangent.normalize();
        return tangent;
    }

    protected Vector3d calculateCentroid(List<GaiaVertex> vertices) {
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

    protected Vector3d safeNormalize(Vector3d v) {
        if (v == null || v.lengthSquared() == 0.0) {
            return null;
        }
        return v.normalize(new Vector3d());
    }

    protected List<Vector2d> collectProjectedPoints2D(BillboardPlane billboardPlane, List<GaiaVertex> sourceVertices, Vector3d origin, Vector3d tangent, Vector3d bitangent) {
        List<Vector2d> points2D = new ArrayList<>();

        for (GaiaFace face : billboardPlane.getFaces()) {
            List<GaiaVertex> faceVertices = getVerticesFromFace(face, sourceVertices);
            for (GaiaVertex vertex : faceVertices) {
                Vector3d projected = projectPointOntoPlane(new Vector3d(vertex.getPosition()), billboardPlane.getPlane());
                Vector3d diff = new Vector3d(projected).sub(origin);
                double u = diff.dot(tangent);
                double v = diff.dot(bitangent);

                points2D.add(new Vector2d(u, v));
            }
        }
        return points2D;
    }

    protected void computePlaneOBB(BillboardPlane billboardPlane, List<GaiaVertex> sourceVertices) {
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
        double minDepth = Double.POSITIVE_INFINITY;
        double maxDepth = Double.NEGATIVE_INFINITY;

        for (GaiaFace face : billboardPlane.getFaces()) {
            List<GaiaVertex> faceVertices = getVerticesFromFace(face, sourceVertices);
            for (GaiaVertex vertex : faceVertices) {
                Vector3d position = vertex.getPosition();
                Vector3d projected = projectPointOntoPlane(new Vector3d(position), billboardPlane.getPlane());
                Vector3d projectedDiff = new Vector3d(projected).sub(origin);

                double u = projectedDiff.dot(tangent);
                double v = projectedDiff.dot(bitangent);

                if (u < minU) {minU = u;}
                if (u > maxU) {maxU = u;}
                if (v < minV) {minV = v;}
                if (v > maxV) {maxV = v;}

                Vector3d rawDiff = new Vector3d(position).sub(origin);
                double depth = rawDiff.dot(normal);
                if (depth < minDepth) { minDepth = depth; }
                if (depth > maxDepth) { maxDepth = depth; }
            }
        }

        billboardPlane.setTangent(tangent);
        billboardPlane.setBitangent(bitangent);
        billboardPlane.setMinU(minU);
        billboardPlane.setMinV(minV);
        billboardPlane.setMaxU(maxU);
        billboardPlane.setMaxV(maxV);

        billboardPlane.setDepthMin(minDepth);
        billboardPlane.setDepthMax(maxDepth);
    }

    protected List<GaiaVertex> getVerticesFromFace(GaiaFace face, List<GaiaVertex> allVertices) {
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

    protected void mergePrimitives(GaiaScene resultScene) {
        List<GaiaNode> leafNodes = extractor.extractAllNodes(resultScene, true);
        for (GaiaNode node : leafNodes) {
            List<GaiaMesh> meshes = node.getMeshes();
            for (GaiaMesh mesh : meshes) {
                List<GaiaPrimitive> meshPrimitives = mesh.getPrimitives();
                GaiaPrimitive mergedPrimitive = TextureAtlasUtils.createMergedPrimitives(meshPrimitives);
                if (mergedPrimitive == null) {
                    continue;
                }
                meshPrimitives.clear();
                meshPrimitives.add(mergedPrimitive);
            }
        }
    }

    protected void atlasTextures(GaiaScene resultScene) {
        List<TextureAtlasSource> sources = new ArrayList<>();
        for (GaiaMaterial material : resultScene.getMaterials()) {
            List<GaiaPrimitive> primitives = TextureAtlasUtils.findPrimitivesUsingMaterial(resultScene, material);
            BufferedImage diffuseImage = TextureAtlasUtils.loadDiffuseImage(material);
            TextureAtlasSource source = new TextureAtlasSource(material, primitives, diffuseImage);
            sources.add(source);
        }


        TextureAtlas atlas = new TextureAtlas();
        BufferedImage atlasImage = atlas.build(sources);
        ImageDilate.dilateAlphaRGB(atlasImage, 3);
        atlas.remapUv();


        GaiaMaterial atlasMaterial = new GaiaMaterial();
        atlasMaterial.setId(0);
        atlasMaterial.setName("atlas");
        if (options.isBlendTexture()) {
            atlasMaterial.setBlend(true);
            atlasMaterial.setOpaque(false);
        } else {
            atlasMaterial.setBlend(false);
            atlasMaterial.setOpaque(true);
        }
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
}
