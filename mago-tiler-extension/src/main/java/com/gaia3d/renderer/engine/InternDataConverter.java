package com.gaia3d.renderer.engine;

import com.gaia3d.basic.exchangable.GaiaBuffer;
import com.gaia3d.basic.exchangable.GaiaBufferDataSet;
import com.gaia3d.basic.geometry.GaiaBoundingBox;
import com.gaia3d.basic.model.*;
import com.gaia3d.basic.types.AttributeType;
import com.gaia3d.renderer.renderable.*;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class InternDataConverter {
    public static RenderableGaiaScene getRenderableGaiaScene(GaiaScene gaiaScene) {
        RenderableGaiaScene renderableGaiaScene = new RenderableGaiaScene();

        renderableGaiaScene.setOriginalGaiaScene(gaiaScene);

        // original path
        Path originalPath = gaiaScene.getOriginalPath();
        renderableGaiaScene.setOriginalPath(originalPath);

        // materials
        List<GaiaMaterial> materials = gaiaScene.getMaterials();
        List<GaiaMaterial> newMaterials = new ArrayList<>();
        for (GaiaMaterial material : materials) {
            GaiaMaterial newMaterial = material.clone();
            newMaterials.add(newMaterial);
        }
        renderableGaiaScene.setMaterials(newMaterials);

        // nodes
        List<GaiaNode> nodes = gaiaScene.getNodes();
        for (GaiaNode node : nodes) {
            RenderableNode renderableNode = getRenderableNode(node, null, gaiaScene, renderableGaiaScene);
            renderableNode.calculatePreMultipliedTransformMatrix();
            renderableGaiaScene.addRenderableNode(renderableNode);
        }

        return renderableGaiaScene;
    }

    public static RenderableNode getRenderableNode(GaiaNode gaiaNode, Matrix4d parentTransformMatrix, GaiaScene scene, RenderableGaiaScene parentRenderableGaiaScene) {
        RenderableNode renderableNode = new RenderableNode();

        String name = gaiaNode.getName();
        GaiaBoundingBox gaiaBoundingBox = gaiaNode.getBoundingBox(parentTransformMatrix);

        Matrix4d transformMatrix = gaiaNode.getTransformMatrix();
        renderableNode.setOriginalGaiaNode(gaiaNode);
        renderableNode.setName(name);
        renderableNode.setTransformMatrix(new Matrix4d(transformMatrix));
        renderableNode.setPreMultipliedTransformMatrix(new Matrix4d(transformMatrix));
        renderableNode.setGaiaBoundingBox(gaiaBoundingBox);

        List<GaiaMesh> meshes = gaiaNode.getMeshes();
        int meshesCount = meshes.size();
        for (GaiaMesh gaiaMesh : meshes) {
            RenderableMesh renderableMesh = getRenderableMesh(gaiaMesh, transformMatrix, scene, parentRenderableGaiaScene);
            renderableNode.addRenderableMesh(renderableMesh);
        }

        // check for children.
        List<GaiaNode> children = gaiaNode.getChildren();
        int childrenCount = children.size();
        for (GaiaNode child : children) {
            RenderableNode renderableChildNode = getRenderableNode(child, transformMatrix, scene, parentRenderableGaiaScene);
            renderableChildNode.setParent(renderableNode);
            renderableNode.addChild(renderableChildNode);
        }

        return renderableNode;
    }

    public static RenderableMesh getRenderableMesh(GaiaMesh gaiaMesh, Matrix4d transformMatrix, GaiaScene scene, RenderableGaiaScene parentRenderableGaiaScene) {
        RenderableMesh renderableMesh = new RenderableMesh();
        renderableMesh.setOriginalGaiaMesh(gaiaMesh);

        List<GaiaPrimitive> primitives = gaiaMesh.getPrimitives();
        for (GaiaPrimitive gaiaPrimitive : primitives) {
            GaiaBufferDataSet bufferDataSet = gaiaPrimitive.toGaiaBufferSet(transformMatrix);
            bufferDataSet.setMaterialId(gaiaPrimitive.getMaterialIndex());
            RenderablePrimitive renderablePrimitive = getRenderablePrimitive(gaiaPrimitive, bufferDataSet, scene, parentRenderableGaiaScene);
            renderableMesh.addRenderablePrimitive(renderablePrimitive);
        }

        return renderableMesh;
    }

    public static RenderablePrimitive getRenderablePrimitive(GaiaPrimitive gaiaPrimitive, GaiaBufferDataSet bufferDataSet, GaiaScene scene, RenderableGaiaScene parentRenderableGaiaScene) {
        RenderablePrimitive renderablePrimitive = new RenderablePrimitive();
        renderablePrimitive.setOriginalBufferDataSet(bufferDataSet);
        renderablePrimitive.setOriginalGaiaPrimitive(gaiaPrimitive);

        Map<AttributeType, GaiaBuffer> buffers = bufferDataSet.getBuffers();
        for (Map.Entry<AttributeType, GaiaBuffer> entry : buffers.entrySet()) {
            AttributeType attributeType = entry.getKey();
            GaiaBuffer buffer = entry.getValue();
            buffer.setAttributeType(attributeType); // set the attribute type to the buffer
            RenderableBuffer renderableBuffer = getRenderableBuffer(buffer);
            renderablePrimitive.setAttribTypeRenderableBuffer(attributeType, renderableBuffer);
            int matId = bufferDataSet.getMaterialId();
            GaiaMaterial material = null;

            if (matId >= 0) {
                material = parentRenderableGaiaScene.getMaterials().stream().filter((materialToFind) -> materialToFind.getId() == matId).findFirst().orElseThrow();
            }

            renderablePrimitive.setMaterial(material);
        }

        return renderablePrimitive;
    }

    public static RenderableBuffer getRenderableBuffer(GaiaBuffer buffer) {
        RenderableBuffer renderableBuffer;

        AttributeType attributeType = buffer.getAttributeType();
        int glType = buffer.getGlType();
        byte glDimension = buffer.getGlDimension();
        int elemsCount = buffer.getElementsCount();
        int glTarget = buffer.getGlTarget();

        renderableBuffer = new RenderableBuffer(attributeType, elemsCount, glDimension, glType, glTarget);

        if (attributeType == AttributeType.POSITION) {
            float[] positions = buffer.getFloats();
            // Positions VBO
            int[] vboId = new int[1];
            GL20.glGenBuffers(vboId);

            GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, vboId[0]);
            GL20.glBufferData(GL20.GL_ARRAY_BUFFER, positions, GL20.GL_STATIC_DRAW);
            renderableBuffer.setVboId(vboId[0]);

        } else if (attributeType == AttributeType.NORMAL) {

            // Normal VBO
            int[] vboId = new int[1];
            GL20.glGenBuffers(vboId);

            if (glType == GL20.GL_FLOAT) {
                float[] normals = buffer.getFloats();
                GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, vboId[0]);
                GL20.glBufferData(GL20.GL_ARRAY_BUFFER, normals, GL20.GL_STATIC_DRAW);
            } else if (glType == GL20.GL_SHORT || glType == GL20.GL_UNSIGNED_SHORT) {
                throw new UnsupportedOperationException("Short type normal buffer is not supported yet.");
            } else if (glType == GL20.GL_BYTE || glType == GL20.GL_UNSIGNED_BYTE) {
                byte[] normals = buffer.getBytes();
                GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, vboId[0]);
                GL20.glBufferData(GL20.GL_ARRAY_BUFFER, ByteBuffer.wrap(normals), GL20.GL_STATIC_DRAW);
            }

            renderableBuffer.setVboId(vboId[0]);

        } else if (attributeType == AttributeType.TEXCOORD) {
            // Texture coordinates VBO
            int[] vboId = new int[1];
            GL20.glGenBuffers(vboId);

            if (glType == GL20.GL_FLOAT) {
                float[] texcoords = buffer.getFloats();
                GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, vboId[0]);
                GL20.glBufferData(GL20.GL_ARRAY_BUFFER, texcoords, GL20.GL_STATIC_DRAW);
            } else if (glType == GL20.GL_SHORT || glType == GL20.GL_UNSIGNED_SHORT) {
                throw new UnsupportedOperationException("Short type texcoord buffer is not supported yet.");
            } else if (glType == GL20.GL_BYTE || glType == GL20.GL_UNSIGNED_BYTE) {
                byte[] texcoords = buffer.getBytes();
                GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, vboId[0]);
                GL20.glBufferData(GL20.GL_ARRAY_BUFFER, ByteBuffer.wrap(texcoords), GL20.GL_STATIC_DRAW);
            }

            renderableBuffer.setVboId(vboId[0]);

        } else if (attributeType == AttributeType.COLOR) {
            // Color VBO
            int[] vboId = new int[1];
            GL20.glGenBuffers(vboId);

            if (glType == GL20.GL_FLOAT) {
                float[] colors = buffer.getFloats();
                GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, vboId[0]);
                GL20.glBufferData(GL20.GL_ARRAY_BUFFER, colors, GL20.GL_STATIC_DRAW);
            } else if (glType == GL20.GL_SHORT || glType == GL20.GL_UNSIGNED_SHORT) {
                throw new UnsupportedOperationException("Short type color buffer is not supported yet.");
            } else if (glType == GL20.GL_BYTE || glType == GL20.GL_UNSIGNED_BYTE) {
                byte[] colors = buffer.getBytes();
                ByteBuffer byteBuffer = BufferUtils.createByteBuffer(colors.length);
                byteBuffer.put(colors);
                byteBuffer.flip();

                GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, vboId[0]);
                //GL20.glBufferData(GL20.GL_ARRAY_BUFFER, ByteBuffer.wrap(colors), GL20.GL_STATIC_DRAW);
                GL20.glBufferData(GL20.GL_ARRAY_BUFFER, byteBuffer, GL20.GL_STATIC_DRAW);
            }

            renderableBuffer.setVboId(vboId[0]);

        } else if (attributeType == AttributeType.INDICE) {
            // Index VBO
            int[] vboId = new int[1];
            GL20.glGenBuffers(vboId);

            if (glType == GL20.GL_INT || glType == GL20.GL_UNSIGNED_INT) {
                int[] indices = buffer.getInts();
                GL20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, vboId[0]);
                GL20.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, indices, GL20.GL_STATIC_DRAW);

            } else if (glType == GL20.GL_SHORT || glType == GL20.GL_UNSIGNED_SHORT) {
                short[] indices = buffer.getShorts();
                GL20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, vboId[0]);
                GL20.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, indices, GL20.GL_STATIC_DRAW);

            } else if (glType == GL20.GL_BYTE || glType == GL20.GL_UNSIGNED_BYTE) {
                byte[] indices = buffer.getBytes();
                GL20.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, vboId[0]);
                GL20.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, ByteBuffer.wrap(indices), GL20.GL_STATIC_DRAW);
            }

            renderableBuffer.setVboId(vboId[0]);
        }

        return renderableBuffer;

    }
}
