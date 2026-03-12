package com.gaia3d.modifier;

import com.gaia3d.command.LoggingConfiguration;
import com.gaia3d.modifier.billboard.plane.TreeBillBoardOptions;
import com.gaia3d.modifier.billboard.plane.TreeBillboardCreator;
import org.junit.jupiter.api.Test;

import java.io.File;

class TreeCreatorTest {

    static {
        LoggingConfiguration.initConsoleLogger();
    }

    @Test
    void createTreeBillBoard() {
        TreeBillBoardOptions treeBillBoardParameters = new TreeBillBoardOptions();
        treeBillBoardParameters.setVerticalRectanglesCount(2);
        treeBillBoardParameters.setHorizontalRectanglesCount(1);

        treeBillBoardParameters.setVerticalRectanglesCount(4);
        treeBillBoardParameters.setHorizontalRectanglesCount(3);

        String inputPath = "D:\\data\\korea-forest-service\\original.glb";
        String outputPath = "E:\\data\\mago-server\\output\\BillboardCreation\\";
        File outputDir = new File(outputPath);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new RuntimeException("Output directory creation failed");
        }

        TreeBillboardCreator treeCreator = new TreeBillboardCreator();
        treeCreator.createTreeBillBoard(treeBillBoardParameters, inputPath, outputPath);
    }
}