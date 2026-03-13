package com.gaia3d.modifier.billboard;

import com.gaia3d.basic.model.GaiaFace;
import com.gaia3d.basic.model.GaiaVertex;
import com.gaia3d.modifier.billboard.render.*;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

@Slf4j
public class Renderer4TextureBake {

    public Renderer4TextureBake(BillboardCloudOptions options) {
        this.width = options.getMaximumTextureSize();
        this.height = options.getMaximumTextureSize();
    }

    private static final String VERTEX_SHADER_SOURCE = """
            #version 330 core
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec2 aTexCoord;
            
            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProj;
            
            out vec3 vNormal;
            out vec2 vTexCoord;
            
            void main() {
                vNormal = mat3(transpose(inverse(uModel))) * aNormal;
                vTexCoord = aTexCoord;
                gl_Position = uProj * uView * uModel * vec4(aPosition, 1.0);
            }
            """;
    private static final String FRAGMENT_SHADER_SOURCE = """
            #version 330 core
            in vec3 vNormal;
            in vec2 vTexCoord;
            
            uniform sampler2D uColorTex;
            uniform bool uUseTexture;
            
            out vec4 fragColor;
            
            void main() {
                vec2 uv = fract(vTexCoord);
                vec4 color = uUseTexture ? texture(uColorTex, uv) : vec4(0.8, 0.8, 0.8, 1.0);
                if (color.a < 0.01) {
                    discard;
                } else if (color.a < 0.8) {
                    color.a = 0.8;
                }
            
                fragColor = vec4(color.rgb, color.a);
                //vec3 n = normalize(vNormal);
                //fragColor = vec4(vTexCoord.x, vTexCoord.y, 0.0, 1.0);
                //fragColor = vec4(0.8, 0.8, 0.8, 1.0);
            }
            """;
    private long window;
    private final int width;
    private final int height;
    private int shaderProgram;
    private SimpleFbo fbo;

    public void init() {
        initWindow();
        initOpenGL();
    }

    private void initWindow() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        window = glfwCreateWindow(width, height, "Texture Bake Renderer", MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(0);
    }

    private void initOpenGL() {
        GL.createCapabilities();

        glViewport(0, 0, width, height);

        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        //glDisable(GL_BLEND);

        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glBlendEquation(GL_FUNC_ADD);

        shaderProgram = createShaderProgram(VERTEX_SHADER_SOURCE, FRAGMENT_SHADER_SOURCE);
        fbo = new SimpleFbo(width, height);
    }

    /**
     * BillboardPlane의 groupedFaces만 offscreen 렌더링해서
     * SimpleFbo의 color texture id를 반환
     */
    public int bakeBillboardPlane(BillboardPlane billboardPlane, List<GaiaVertex> sourceVertices, OrthographicProjection projection, BufferedImage finalDiffuseTextureImage) {
        MeshBuffer meshBuffer = null;
        try {
            MeshData meshData = convertBillboardPlaneToMeshData(billboardPlane, sourceVertices);
            if (meshData == null || meshData.vertices.length == 0 || meshData.indices.length == 0) {
                return 0;
            }

            meshBuffer = new MeshBuffer();
            meshBuffer.upload(meshData.vertices, meshData.indices);

            fbo.bind();

            glClearColor(0f, 0f, 0f, 0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            glUseProgram(shaderProgram);

            Matrix4f model = new Matrix4f().identity();
            //Matrix4f view = projection.createViewMatrix();
            Matrix4f view = projection.createPlaneViewMatrix();
            Matrix4f proj = projection.createProjectionMatrix();

            setUniformMat4(shaderProgram, "uModel", model);
            setUniformMat4(shaderProgram, "uView", view);
            setUniformMat4(shaderProgram, "uProj", proj);

            if (finalDiffuseTextureImage != null) {
                glUniform1i(glGetUniformLocation(shaderProgram, "uUseTexture"), 1);
                int textureId = TextureUtils.createTextureFromBufferedImage(finalDiffuseTextureImage);
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, textureId);
                setUniform1i(shaderProgram, "uColorTex", 0);
            } else {
                glUniform1i(glGetUniformLocation(shaderProgram, "uUseTexture"), 0);
            }

            //meshBuffer.draw();

            // TODO
            //glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            meshBuffer.draw();
            //glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);

            // clean
            glBindVertexArray(0);
            glUseProgram(0);
            fbo.unbind(width, height);
            return fbo.getColorTex();
        } finally {
            if (meshBuffer != null) {
                meshBuffer.cleanup();
            }
        }
    }

    /**
     * 필요하면 bake 결과를 CPU로 읽어오는 용도
     */
    public ByteBuffer readCurrentFboPixels() {
        fbo.bind();
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        fbo.unbind(width, height);
        return buffer;
    }

    public void saveCurrentFboToPng(String filePath) {
        ByteBuffer buffer = readCurrentFboPixels();
        saveRgbaBufferToPng(buffer, width, height, filePath, true);
    }

    private void saveRgbaBufferToPng(ByteBuffer buffer, int width, int height, String filePath, boolean flipY) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            int srcY = flipY ? (height - 1 - y) : y;

            for (int x = 0; x < width; x++) {
                int index = (srcY * width + x) * 4;

                int r = buffer.get(index) & 0xFF;
                int g = buffer.get(index + 1) & 0xFF;
                int b = buffer.get(index + 2) & 0xFF;
                int a = buffer.get(index + 3) & 0xFF;

                int argb = ((a & 0xFF) << 24)
                        | ((r & 0xFF) << 16)
                        | ((g & 0xFF) << 8)
                        | (b & 0xFF);

                image.setRGB(x, y, argb);
            }
        }

        try {
            File output = new File(filePath);
            File parent = output.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            ImageIO.write(image, "png", output);
            log.info("Saved FBO PNG: {}", output.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save PNG: " + filePath, e);
        }
    }

    private static final double POSITION_EPSILON = 1e-9;

    private MeshData convertBillboardPlaneToMeshData(BillboardPlane billboardPlane, List<GaiaVertex> sourceVertices) {
        if (billboardPlane == null || billboardPlane.getFaces() == null || billboardPlane.getFaces().isEmpty()) {
            log.warn("BillboardPlane or faces are null/empty.");
            return null;
        }

        List<Float> vertexList = new ArrayList<>();
        List<Integer> indexList = new ArrayList<>();

        int vertexOffset = 0;

        int totalFaces = 0;
        int skippedInvalidIndex = 0;
        int skippedInvalidVertex = 0;
        int skippedDegenerate = 0;
        int emittedTriangles = 0;

        for (GaiaFace face : billboardPlane.getFaces()) {
            totalFaces++;

            int[] indices = face.getIndices();
            if (indices == null || indices.length != 3) {
                log.warn("Skip face: not triangle. indices={}", indices == null ? null : java.util.Arrays.toString(indices));
                continue;
            }

            int i0 = indices[0];
            int i1 = indices[1];
            int i2 = indices[2];

            if (!isValidIndex(i0, sourceVertices.size()) ||
                    !isValidIndex(i1, sourceVertices.size()) ||
                    !isValidIndex(i2, sourceVertices.size())) {

                skippedInvalidIndex++;
                log.warn("Skip face: out of bounds indices={}, sourceVertices.size={}",
                        java.util.Arrays.toString(indices), sourceVertices.size());
                continue;
            }

            GaiaVertex gv0 = sourceVertices.get(i0);
            GaiaVertex gv1 = sourceVertices.get(i1);
            GaiaVertex gv2 = sourceVertices.get(i2);

            if (!isValidVertex(gv0) || !isValidVertex(gv1) || !isValidVertex(gv2)) {
                skippedInvalidVertex++;
                log.warn("Skip face: invalid vertex in indices={}", java.util.Arrays.toString(indices));
                continue;
            }

            if (isDegenerateTriangle(gv0, gv1, gv2, POSITION_EPSILON)) {
                skippedDegenerate++;
                log.debug("Skip face: degenerate triangle indices={}", java.util.Arrays.toString(indices));
                continue;
            }

            RenderVertex rv0 = toRenderVertexSafe(gv0);
            RenderVertex rv1 = toRenderVertexSafe(gv1);
            RenderVertex rv2 = toRenderVertexSafe(gv2);

            addRenderVertex(vertexList, rv0);
            addRenderVertex(vertexList, rv1);
            addRenderVertex(vertexList, rv2);

            indexList.add(vertexOffset);
            indexList.add(vertexOffset + 1);
            indexList.add(vertexOffset + 2);

            vertexOffset += 3;
            emittedTriangles++;
        }

        if (vertexList.isEmpty() || indexList.isEmpty()) {
            log.warn("MeshData empty. totalFaces={}, emittedTriangles={}, skippedInvalidIndex={}, skippedInvalidVertex={}, skippedDegenerate={}",
                    totalFaces, emittedTriangles, skippedInvalidIndex, skippedInvalidVertex, skippedDegenerate);
            return null;
        }

        float[] vertexArray = new float[vertexList.size()];
        for (int i = 0; i < vertexList.size(); i++) {
            vertexArray[i] = vertexList.get(i);
        }

        int[] indexArray = new int[indexList.size()];
        for (int i = 0; i < indexList.size(); i++) {
            indexArray[i] = indexList.get(i);
        }

        /*log.info("MeshData created. totalFaces={}, emittedTriangles={}, skippedInvalidIndex={}, skippedInvalidVertex={}, skippedDegenerate={}, vertexCount={}",
                totalFaces, emittedTriangles, skippedInvalidIndex, skippedInvalidVertex, skippedDegenerate, vertexArray.length / 8);*/

        return new MeshData(vertexArray, indexArray);
    }

    private boolean isValidIndex(int index, int size) {
        return index >= 0 && index < size;
    }

    private boolean isValidVertex(GaiaVertex gv) {
        if (gv == null || gv.getPosition() == null) {
            return false;
        }

        Vector3d p = gv.getPosition();
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }

    private RenderVertex toRenderVertexSafe(GaiaVertex gv) {
        RenderVertex rv = new RenderVertex();

        Vector3d pos = gv.getPosition();
        rv.px = (float) pos.x;
        rv.py = (float) pos.y;
        rv.pz = (float) pos.z;

        Vector3d normal = gv.getNormal();
        if (normal == null || !isFinite(normal) || normal.lengthSquared() < 1e-20) {
            normal = new Vector3d(0.0, 0.0, 1.0);
        } else {
            normal = new Vector3d(normal).normalize();
        }

        rv.nx = (float) normal.x;
        rv.ny = (float) normal.y;
        rv.nz = (float) normal.z;

        if (gv.getTexcoords() != null) {
            float u = (float) gv.getTexcoords().x;
            float v = (float) gv.getTexcoords().y;
            rv.u = Float.isFinite(u) ? u : 0.0f;
            rv.v = Float.isFinite(v) ? v : 0.0f;
        } else {
            rv.u = 0.0f;
            rv.v = 0.0f;
        }

        return rv;
    }

    private boolean isFinite(Vector3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private boolean isDegenerateTriangle(GaiaVertex a, GaiaVertex b, GaiaVertex c, double epsilon) {
        Vector3d p0 = a.getPosition();
        Vector3d p1 = b.getPosition();
        Vector3d p2 = c.getPosition();

        if (p0 == null || p1 == null || p2 == null) {
            return true;
        }

        if (isSamePosition(p0, p1, epsilon) ||
                isSamePosition(p1, p2, epsilon) ||
                isSamePosition(p2, p0, epsilon)) {
            return true;
        }

        Vector3d e1 = new Vector3d(p1).sub(p0);
        Vector3d e2 = new Vector3d(p2).sub(p0);
        Vector3d cross = e1.cross(e2, new Vector3d());

        return cross.lengthSquared() < epsilon * epsilon;
    }

    private boolean isSamePosition(Vector3d a, Vector3d b, double epsilon) {
        return Math.abs(a.x - b.x) <= epsilon
                && Math.abs(a.y - b.y) <= epsilon
                && Math.abs(a.z - b.z) <= epsilon;
    }

    private void addRenderVertex(List<Float> vertexList, RenderVertex rv) {
        vertexList.add(rv.px);
        vertexList.add(rv.py);
        vertexList.add(rv.pz);

        vertexList.add(rv.nx);
        vertexList.add(rv.ny);
        vertexList.add(rv.nz);

        vertexList.add(rv.u);
        vertexList.add(rv.v);
    }

    /*private MeshData convertBillboardPlaneToMeshData(BillboardPlane billboardPlane, List<GaiaVertex> sourceVertices) {
        if (billboardPlane == null || billboardPlane.getFaces() == null || billboardPlane.getFaces().isEmpty()) {
            return null;
        }

        List<Float> vertexList = new ArrayList<>();
        List<Integer> indexList = new ArrayList<>();

        int vertexOffset = 0;

        for (GaiaFace face : billboardPlane.getFaces()) {
            int[] indices = face.getIndices();
            if (indices == null || indices.length < 3) {
                continue;
            }

            List<GaiaVertex> faceVertices = getVerticesFromFace(face, sourceVertices);
            if (faceVertices.size() < 3) {
                continue;
            }

            // face 단위로 그대로 삼각형 생성
            for (GaiaVertex gv : faceVertices) {
                RenderVertex rv = toRenderVertex(gv);

                vertexList.add(rv.px);
                vertexList.add(rv.py);
                vertexList.add(rv.pz);

                vertexList.add(rv.nx);
                vertexList.add(rv.ny);
                vertexList.add(rv.nz);

                vertexList.add(rv.u);
                vertexList.add(rv.v);
            }

            // 현재는 face당 새 vertex를 복제하므로 0,1,2...
            for (int i = 1; i < faceVertices.size() - 1; i++) {
                indexList.add(vertexOffset);
                indexList.add(vertexOffset + i);
                indexList.add(vertexOffset + i + 1);
            }

            vertexOffset += faceVertices.size();
        }

        float[] vertexArray = new float[vertexList.size()];
        for (int i = 0; i < vertexList.size(); i++) {
            vertexArray[i] = vertexList.get(i);
        }

        int[] indexArray = new int[indexList.size()];
        for (int i = 0; i < indexList.size(); i++) {
            indexArray[i] = indexList.get(i);
        }

        return new MeshData(vertexArray, indexArray);
    }*/

    private RenderVertex toRenderVertex(GaiaVertex gv) {
        RenderVertex rv = new RenderVertex();

        Vector3d pos = gv.getPosition();
        rv.px = (float) pos.x;
        rv.py = (float) pos.y;
        rv.pz = (float) pos.z;

        Vector3d normal = gv.getNormal() != null ? gv.getNormal() : new Vector3d(0.0, 0.0, 1.0);
        rv.nx = (float) normal.x;
        rv.ny = (float) normal.y;
        rv.nz = (float) normal.z;

        // GaiaVertex texcoord 타입에 맞게 수정 필요할 수 있음
        if (gv.getTexcoords() != null) {
            rv.u = (float) gv.getTexcoords().x;
            rv.v = (float) gv.getTexcoords().y;
        } else {
            rv.u = 0.0f;
            rv.v = 0.0f;
        }

        return rv;
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

    private void setUniformMat4(int program, String uniformName, Matrix4f matrix) {
        int location = glGetUniformLocation(program, uniformName);
        if (location < 0) {
            return;
        }
        FloatBuffer fb = BufferUtils.createFloatBuffer(16);
        matrix.get(fb);
        glUniformMatrix4fv(location, false, fb);
    }

    private void setUniform1i(int program, String uniformName, int value) {
        int location = glGetUniformLocation(program, uniformName);
        if (location < 0) {
            return;
        }
        glUniform1i(location, value);
    }

    public void cleanup() {
        if (fbo != null) {
            fbo.cleanup();
        }

        if (shaderProgram != 0) {
            glDeleteProgram(shaderProgram);
        }

        if (window != MemoryUtil.NULL) {
            glfwDestroyWindow(window);
        }

        glfwTerminate();

        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }

    private int createShaderProgram(String vertexSrc, String fragmentSrc) {
        int vs = compileShader(GL_VERTEX_SHADER, vertexSrc);
        int fs = compileShader(GL_FRAGMENT_SHADER, fragmentSrc);

        int program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);

        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            throw new RuntimeException("Shader program link failed:\n" + log);
        }

        glDeleteShader(vs);
        glDeleteShader(fs);

        return program;
    }

    private int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            throw new RuntimeException("Shader compile failed:\n" + log);
        }

        return shader;
    }

    private record MeshData(float[] vertices, int[] indices) {
    }
}