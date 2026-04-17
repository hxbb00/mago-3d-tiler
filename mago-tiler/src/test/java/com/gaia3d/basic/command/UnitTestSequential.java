package com.gaia3d.basic.command;

import com.gaia3d.command.mago.Mago3DTilerMain;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@SuppressWarnings("ALL")
@Deprecated
@Slf4j
class UnitTestSequential {
    private static final String INPUT_PATH = "D:\\data\\unit-test\\";
    private static final String OUTPUT_PATH = "D:\\Result_mago3dTiler\\";

    @Test
        //@Disabled
    void test_JeonJu_data() {
        String inputPath = "E:\\data\\mago3dtiler_TESTDATA\\20260205-JeonJu-realistic-mesh\\L22_9_buildings\\";
        String outputPath = "C:\\data\\mago-server\\output\\JeonJu_9_building_20260206\\";
        String[] args = new String[]{
                "-i", inputPath,
                "-inputType", "obj",
                "-o", outputPath,
                "-lon", "127.0656897",
                "-lat", "35.8369408",
                "-pg"
        };
        Mago3DTilerMain.main(args);
    }

    @Test
        //@Disabled
    void test_JoGonHee_SangAm_someBuilding() {
        String inputPath = "E:\\data\\mago3dtiler_TESTDATA\\SangAm_JoGoNi\\Production_2\\L22_16_buildings\\";
        String outputPath = "C:\\data\\mago-server\\output\\SangAm_L22_16_buildings_20260206\\";

        String[] args = new String[]{
                "-i", inputPath,
                "-inputType", "obj",
                "-o", outputPath,
                "-crs", "32652",
                "-xOffset", "314050",
                "-yOffset", "4160814",
                "-pg"
        };
        Mago3DTilerMain.main(args);
    }

}
