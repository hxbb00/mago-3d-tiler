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
        String inputPath = "D:\\data\\mago-3d-tiler\\build-sample\\sample-tree\\coconut_palm.glb";
        File inputFile = new File(inputPath);

        String outputPath = "H:\\workspace\\billboardclouds-output";
        File outputFile = new File(outputPath, inputFile.getName().replace(".glb", "-cloud.glb"));

        GaiaScene scene = prepareScene(inputPath);

        int index = 0;
        List<BillboardCloudOptions> lods =  new ArrayList<>();

        /*BillboardCloudOptions billboardCloudOptions0 = BillboardCloudOptions.builder()
                .normalDotThreshold(Math.cos(Math.toRadians(20)))
                .planeDistanceEpsilon(0.1)
                .setRadius(0.5)
                .minTriangleArea(1e-15)
                .maximumTextureSize(128)
                .build();
        lods.add(billboardCloudOptions0);

        BillboardCloudOptions billboardCloudOptions1 = BillboardCloudOptions.builder()
                .normalDotThreshold(Math.cos(Math.toRadians(25)))
                .planeDistanceEpsilon(0.1)
                .setRadius(0.5)
                .minTriangleArea(1e-15)
                .maximumTextureSize(128)
                .build();
        lods.add(billboardCloudOptions1);*/

        BillboardCloudOptions billboardCloudOptions2 = BillboardCloudOptions.builder()
                .normalDotThreshold(Math.cos(Math.toRadians(45)))
                .planeDistanceEpsilon(0.1)
                .setRadius(0.5)
                .minTriangleArea(1e-15)
                .maximumTextureSize(512)
                .build();
        lods.add(billboardCloudOptions2);

        for (BillboardCloudOptions lod : lods) {
            File name = new File(outputFile.getAbsolutePath().replace(".glb", "-lod" + index + ".glb"));
            BillboardCloudCreator creator = new BillboardCloudCreator(lod);
            log.info("Creating billboard cloud for scene with LOD {}", index);
            GaiaScene billboardCloud = creator.createBillboardCloud(scene);
            log.info("Writing billboard cloud to output path for LOD {}", index);
            writeGlb(billboardCloud, name.getAbsolutePath());
            index++;
        }

        /*BillboardCloudCreator creator = new BillboardCloudCreator(billboardCloudOptions);
        assertDoesNotThrow(() -> {
            log.info("Creating billboard cloud for scene");
            GaiaScene billboardCloud = creator.createBillboardCloud(scene);
            log.info("Writing billboard cloud to output path");
            writeGlb(billboardCloud, outputFile.getAbsolutePath());
        }, "Expected no exception to be thrown when creating billboard cloud with null scene.");*/
    }

    private GaiaScene prepareScene(String inputPath) {
        // 1rst, load the tree model from the given path
        log.info("Loading tree model from path: {}", inputPath);
        AssimpConverterOptions options = AssimpConverterOptions.builder().isSplitByNode(false).build();
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