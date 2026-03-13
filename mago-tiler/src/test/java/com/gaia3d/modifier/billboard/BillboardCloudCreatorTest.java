package com.gaia3d.modifier.billboard;

import com.gaia3d.basic.geometry.GaiaBoundingBox;
import com.gaia3d.basic.geometry.modifier.transform.GaiaBaker;
import com.gaia3d.basic.geometry.modifier.transform.GaiaScaler;
import com.gaia3d.basic.geometry.modifier.transform.GaiaScalerOptions;
import com.gaia3d.basic.model.GaiaScene;
import com.gaia3d.command.LoggingConfiguration;
import com.gaia3d.converter.assimp.AssimpConverter;
import com.gaia3d.converter.assimp.AssimpConverterOptions;
import com.gaia3d.converter.gltf.GltfWriter;
import com.gaia3d.converter.gltf.GltfWriterOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Slf4j
class BillboardCloudCreatorTest {

    static {
        LoggingConfiguration.initConsoleLogger();
        LoggingConfiguration.setLevel(Level.INFO);
    }

    @Test
    void createBillboardCloud() {
        String inputPath = "D:\\data\\mago-3d-tiler\\build-sample\\sample-tree\\dt-model.glb";
        //String inputPath = "D:\\data\\mago-3d-tiler\\build-sample\\sample-tree\\original.glb";
        File inputFile = new File(inputPath);

        String outputPath = "H:\\workspace\\billboardclouds-output";
        File outputFile = new File(outputPath, inputFile.getName().replace(".glb", "-cloud.glb"));

        GaiaScene scene = prepareScene(inputPath);

        int index = 0;
        List<BillboardCloudOptions> lods =  new ArrayList<>();

        BillboardCloudOptions billboardCloudOptions0 = BillboardCloudOptions.builder()
                .normalDotThreshold(Math.cos(Math.toRadians(40)))
                .planeDistanceEpsilon(0.03)
                .setRadius(0.2)
                .minTriangleArea(1e-8)
                .maximumTextureSize(128)
                .build();
        lods.add(billboardCloudOptions0);

        BillboardCloudOptions billboardCloudOptions1 = BillboardCloudOptions.builder()
                .normalDotThreshold(Math.cos(Math.toRadians(35)))
                .planeDistanceEpsilon(0.06)
                .setRadius(0.2)
                .minTriangleArea(1e-8)
                .maximumTextureSize(128)
                .build();
        lods.add(billboardCloudOptions1);

        BillboardCloudOptions billboardCloudOptions2 = BillboardCloudOptions.builder()
                .normalDotThreshold(Math.cos(Math.toRadians(30)))
                .planeDistanceEpsilon(0.09)
                .setRadius(0.2)
                .minTriangleArea(1e-9)
                .maximumTextureSize(64)
                .build();
        lods.add(billboardCloudOptions2);

        BillboardCloudOptions billboardCloudOptions3 = BillboardCloudOptions.builder()
                .normalDotThreshold(Math.cos(Math.toRadians(25)))
                .planeDistanceEpsilon(0.12)
                .setRadius(0.2)
                .minTriangleArea(1e-10)
                .maximumTextureSize(64)
                .build();
        lods.add(billboardCloudOptions3);


        for (BillboardCloudOptions lod : lods) {
            File name = new File(outputFile.getAbsolutePath().replace(".glb", "-lod" + index + ".glb"));
            BillboardCloudCreator creator = new BillboardCloudCreator(lod);
            GaiaScene billboardCloud = creator.createBillboardCloud(scene);
            writeGlb(billboardCloud, name.getAbsolutePath());
            index++;
        }
    }

    private GaiaScene prepareScene(String inputPath) {
        // 1rst, load the tree model from the given path
        log.info("Loading tree model from path: {}", inputPath);
        AssimpConverterOptions options = AssimpConverterOptions.builder()
                .isSplitByNode(false)
                .build();
        AssimpConverter assimpConverter = new AssimpConverter(options);
        List<GaiaScene> gaiaScenes = assimpConverter.load(inputPath);

        // Flip Y tex-coordinates
        //FlipYTexCoordinate flipYTexCoordinate = new FlipYTexCoordinate();
        //gaiaScenes.forEach(flipYTexCoordinate::flip);

        //List<GaiaScene> resultGaiaScenes = new ArrayList<>();
        //TilerExtensionModule tilerExtensionModule = new TilerExtensionModule();
        //int verticalPlanesCount = treeBillBoardParameters.getVerticalRectanglesCount();
        //int horizontalPlanesCount = treeBillBoardParameters.getHorizontalRectanglesCount();
        //tilerExtensionModule.makeBillBoard(gaiaScenes, resultGaiaScenes, verticalPlanesCount, horizontalPlanesCount);

        // rotate 90 degree to make the tree upright
        for (GaiaScene gaiaScene : gaiaScenes) {
            //GaiaNode rootNode = gaiaScene.getNodes().get(0);
            //rootNode.getTransformMatrix().rotateX(Math.toRadians(-90));

            GaiaBaker baker = new GaiaBaker();
            baker.apply(gaiaScene);

            GaiaBoundingBox bbox = gaiaScene.updateBoundingBox();
            Vector3d volume = bbox.getVolume();
            double maxDimension = Math.max(volume.x, Math.max(volume.y, volume.z));
            GaiaScalerOptions scalerOptions = GaiaScalerOptions.builder()
                    .scaleX(1.0 / maxDimension)
                    .scaleY(1.0 / maxDimension)
                    .scaleZ(1.0 / maxDimension)
                    .build();
            GaiaScaler scaler = new GaiaScaler(scalerOptions);
            scaler.apply(gaiaScene);
        }
        return gaiaScenes.getFirst();
    }

    private void writeGlb(GaiaScene scene, String outputPath) {
        GltfWriterOptions options = GltfWriterOptions.builder()
                .isDoubleSided(true)
                //.isUseQuantization(true)
                //.isUseShortTexCoord(true)
                .build();

        GltfWriter gltfWriter = new GltfWriter(options);
        gltfWriter.writeGlb(scene, outputPath);
    }
}