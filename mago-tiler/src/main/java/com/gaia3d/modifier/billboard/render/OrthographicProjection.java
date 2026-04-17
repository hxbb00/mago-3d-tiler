package com.gaia3d.modifier.billboard.render;

import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector3d;

@Getter
@Setter
public class OrthographicProjection {
    private Vector3d cameraPosition;
    private Vector3d target;
    private Vector3d up;

    private double left;
    private double right;
    private double bottom;
    private double top;

    private double near;
    private double far;

    private Vector3d tangent;
    private Vector3d bitangent;
    private Vector3d normal;

    public Matrix4f createViewMatrix() {
        return new Matrix4f().lookAt((float) cameraPosition.x, (float) cameraPosition.y, (float) cameraPosition.z, (float) target.x, (float) target.y, (float) target.z, (float) up.x, (float) up.y, (float) up.z);
    }

    public Matrix4f createPlaneViewMatrix() {
        Vector3d t = new Vector3d(tangent).normalize();
        Vector3d b = new Vector3d(bitangent).normalize();
        Vector3d n = new Vector3d(normal).normalize();
        Vector3d eye = cameraPosition;

        Matrix4f view = new Matrix4f();

        view.m00((float) t.x);
        view.m10((float) t.y);
        view.m20((float) t.z);
        view.m30((float) -t.dot(eye));

        view.m01((float) b.x);
        view.m11((float) b.y);
        view.m21((float) b.z);
        view.m31((float) -b.dot(eye));

        view.m02((float) n.x);
        view.m12((float) n.y);
        view.m22((float) n.z);
        view.m32((float) -n.dot(eye));

        view.m03(0f);
        view.m13(0f);
        view.m23(0f);
        view.m33(1f);

        return view;
    }

    public Matrix4f createProjectionMatrix() {
        return new Matrix4f().ortho((float) left, (float) right, (float) bottom, (float) top, (float) near, (float) far);
    }
}
