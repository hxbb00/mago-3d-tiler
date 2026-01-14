package com.gaia3d.renderer.engine.dataStructure;

import com.gaia3d.basic.halfedge.CameraDirectionType;
import org.joml.Vector3d;

import java.util.Map;

public class FaceVisibilityDataManager {
    private final Map<CameraDirectionType, FaceVisibilityData> faceVisibilityDataMap;

    public FaceVisibilityDataManager() {
        faceVisibilityDataMap = new java.util.HashMap<>();
    }

    public FaceVisibilityData getFaceVisibilityData(CameraDirectionType cameraDirectionType) {
        // if no existing data, create new one
        if (!faceVisibilityDataMap.containsKey(cameraDirectionType)) {
            faceVisibilityDataMap.put(cameraDirectionType, new FaceVisibilityData(cameraDirectionType));
        }
        return faceVisibilityDataMap.get(cameraDirectionType);
    }

    public CameraDirectionType getBestCameraDirectionTypeOfFace(int faceId) {
        CameraDirectionType bestCameraDirectionType = null;
        int maxPixelCount = 0;
        for (Map.Entry<CameraDirectionType, FaceVisibilityData> entry : faceVisibilityDataMap.entrySet()) {
            int pixelCount = entry.getValue().getPixelFaceVisibility(faceId);
            if (pixelCount > maxPixelCount) {
                maxPixelCount = pixelCount;
                bestCameraDirectionType = entry.getKey();
            }
        }
        return bestCameraDirectionType;
    }

    public CameraDirectionType getBestCameraDirectionTypeOfFace(int faceId, Vector3d faceNormal) {
        CameraDirectionType bestCameraDirectionType = null;
        //double maxPixelCount = 0;
        double maxDotProduct = 0.0;
        for (Map.Entry<CameraDirectionType, FaceVisibilityData> entry : faceVisibilityDataMap.entrySet()) {
            int pixelCount = entry.getValue().getPixelFaceVisibility(faceId);
            if (pixelCount > 0) {
                Vector3d invertedFaceNormal = new Vector3d(faceNormal).negate();
                Vector3d cameraDirection = CameraDirectionType.getCameraDirection(entry.getKey());
                double dotProduct = invertedFaceNormal.dot(cameraDirection);
                if (dotProduct > maxDotProduct) {
                    maxDotProduct = dotProduct;

                    //maxPixelCount = pixelCount * dotProduct;
                    bestCameraDirectionType = entry.getKey();
                }

            }
        }
        return bestCameraDirectionType;
    }

    public void deleteObjects() {
        for (FaceVisibilityData faceVisibilityData : faceVisibilityDataMap.values()) {
            faceVisibilityData.deleteObjects();
        }
        faceVisibilityDataMap.clear();
    }
}
