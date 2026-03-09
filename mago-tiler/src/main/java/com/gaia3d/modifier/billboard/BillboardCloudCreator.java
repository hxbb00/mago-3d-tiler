package com.gaia3d.modifier.billboard;

import com.gaia3d.basic.geometry.entities.GaiaPlane;
import com.gaia3d.basic.geometry.entities.GaiaTriangle;
import com.gaia3d.basic.geometry.modifier.topology.GaiaExtractor;
import com.gaia3d.basic.model.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class BillboardCloudCreator {
    private GaiaExtractor extractor = new GaiaExtractor();

    public GaiaScene createBillboardCloud(GaiaScene scene) {

        List<GaiaNode> leafNodes = extractor.extractAllNodes(scene, true);
        for (GaiaNode node : leafNodes) {
            log.info("Extracting faces from node: {}", node.getName());
            List<GaiaPrimitive> primitives = extractor.extractAllPrimitives(node);
            log.info("Extracted {} primitives from node: {}", primitives.size(), node.getName());

            for (GaiaPrimitive primitive : primitives) {
                List<BillboardPlane> billboardPlanes = createBillboardCloud(primitive);
                log.info("Created {} billboard planes from surface", billboardPlanes.size());
            }
        }

        return scene;
    }

    public List<BillboardPlane> createBillboardCloud(GaiaPrimitive primitive) {
        List<GaiaVertex> vertices = primitive.getVertices();
        List<GaiaFace> faces = extractor.extractAllFaces(primitive);
        return createBillboardPlanes(faces, vertices);
    }

    private List<BillboardPlane> createBillboardPlanes(List<GaiaFace> faces, List<GaiaVertex> vertices) {
        List<BillboardPlane> billboardPlanes = new ArrayList<>();
        for (GaiaFace face : faces) {
            BillboardPlane billboardPlane = createBillboardPlane(face, vertices);
            if (billboardPlane != null) {
                billboardPlanes.add(billboardPlane);
            }
        }
        log.info("Created {} billboard planes from {} faces", billboardPlanes.size(), faces.size());
        return billboardPlanes;
    }

    private BillboardPlane createBillboardPlane(GaiaFace seedFace, List<GaiaVertex> vertices) {
        List<GaiaVertex> faceVertices = getVerticesFromFace(seedFace, vertices);
        if (faceVertices == null || faceVertices.size() < 3) {
            log.warn("Face with indices {} has less than 3 vertices, skipping billboard plane creation", seedFace.getIndices());
            return null;
        }

        GaiaTriangle triangle = new GaiaTriangle(faceVertices.get(0).getPosition(), faceVertices.get(1).getPosition(), faceVertices.get(2).getPosition());
        GaiaPlane plane = triangle.getPlane();

        BillboardPlane billboardPlane = new BillboardPlane();
        billboardPlane.setPlane(plane);
        return billboardPlane;
    }

    private List<GaiaVertex> getVerticesFromFace(GaiaFace face, List<GaiaVertex> allVertices) {
        int[] vertexIndices = face.getIndices();
        List<GaiaVertex> vertices = new ArrayList<>();
        for (int index : vertexIndices) {
            if (index >= 0 && index < allVertices.size()) {
                vertices.add(allVertices.get(index));
            } else {
                log.warn("Vertex index {} is out of bounds for vertices list of size {}", index, allVertices.size());
            }
        }
        return vertices;
    }

}
