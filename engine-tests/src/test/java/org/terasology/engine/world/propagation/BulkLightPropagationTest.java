// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.engine.world.propagation;

import org.joml.Vector3ic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.terasology.TerasologyTestingEnvironment;
import org.terasology.engine.math.Diamond3iIterator;
import org.terasology.engine.math.JomlUtil;
import org.terasology.engine.math.Region3i;
import org.terasology.engine.registry.CoreRegistry;
import org.terasology.engine.world.block.Block;
import org.terasology.engine.world.block.BlockManager;
import org.terasology.engine.world.block.BlockUri;
import org.terasology.engine.world.block.family.SymmetricFamily;
import org.terasology.engine.world.block.internal.BlockManagerImpl;
import org.terasology.engine.world.block.loader.BlockFamilyDefinition;
import org.terasology.engine.world.block.loader.BlockFamilyDefinitionData;
import org.terasology.engine.world.block.shapes.BlockShape;
import org.terasology.engine.world.block.tiles.NullWorldAtlas;
import org.terasology.engine.world.chunks.ChunkConstants;
import org.terasology.engine.world.propagation.light.LightPropagationRules;
import org.terasology.gestalt.assets.ResourceUrn;
import org.terasology.gestalt.assets.management.AssetManager;
import org.terasology.math.geom.Vector3i;
import org.terasology.unittest.world.propagation.StubPropagatorWorldView;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BulkLightPropagationTest extends TerasologyTestingEnvironment {

    private static final Vector3ic ZERO_VECTOR = new org.joml.Vector3i();

    private BlockManagerImpl blockManager;
    private Block air;
    private Block fullLight;
    private Block weakLight;
    private Block mediumLight;
    private Block solid;
    private Block solidMediumLight;
    private LightPropagationRules lightRules;

    private final Region3i testingRegion = Region3i.createFromMinMax(new Vector3i(-ChunkConstants.SIZE_X,
                    -ChunkConstants.SIZE_Y, -ChunkConstants.SIZE_Z),
            new Vector3i(2 * ChunkConstants.SIZE_X, 2 * ChunkConstants.SIZE_Y, 2 * ChunkConstants.SIZE_Z));

    @BeforeEach
    public void setup() throws Exception {
        super.setup();
        lightRules = new LightPropagationRules();
        AssetManager assetManager = CoreRegistry.get(AssetManager.class);
        blockManager = new BlockManagerImpl(new NullWorldAtlas(), assetManager, true);
        CoreRegistry.put(BlockManager.class, blockManager);
        BlockFamilyDefinitionData fullLightData = new BlockFamilyDefinitionData();
        fullLightData.getBaseSection().setDisplayName("Torch");
        fullLightData.getBaseSection().setShape(assetManager.getAsset("engine:cube", BlockShape.class).get());
        fullLightData.getBaseSection().setLuminance(ChunkConstants.MAX_LIGHT);
        fullLightData.getBaseSection().setTranslucent(true);
        fullLightData.setBlockFamily(SymmetricFamily.class);
        assetManager.loadAsset(new ResourceUrn("engine:torch"), fullLightData, BlockFamilyDefinition.class);
        fullLight = blockManager.getBlock(new BlockUri(new ResourceUrn("engine:torch")));

        BlockFamilyDefinitionData weakLightData = new BlockFamilyDefinitionData();
        weakLightData.getBaseSection().setDisplayName("PartLight");
        weakLightData.getBaseSection().setShape(assetManager.getAsset("engine:cube", BlockShape.class).get());
        weakLightData.getBaseSection().setLuminance((byte) 2);
        weakLightData.getBaseSection().setTranslucent(true);
        weakLightData.setBlockFamily(SymmetricFamily.class);
        assetManager.loadAsset(new ResourceUrn("engine:weakLight"), weakLightData, BlockFamilyDefinition.class);
        weakLight = blockManager.getBlock(new BlockUri(new ResourceUrn("engine:weakLight")));

        BlockFamilyDefinitionData mediumLightData = new BlockFamilyDefinitionData();
        mediumLightData.getBaseSection().setDisplayName("MediumLight");
        mediumLightData.getBaseSection().setShape(assetManager.getAsset("engine:cube", BlockShape.class).get());
        mediumLightData.getBaseSection().setLuminance((byte) 5);
        mediumLightData.getBaseSection().setTranslucent(true);
        mediumLightData.setBlockFamily(SymmetricFamily.class);
        assetManager.loadAsset(new ResourceUrn("engine:mediumLight"), mediumLightData, BlockFamilyDefinition.class);
        mediumLight = blockManager.getBlock(new BlockUri(new ResourceUrn("engine:mediumLight")));

        BlockFamilyDefinitionData solidData = new BlockFamilyDefinitionData();
        solidData.getBaseSection().setDisplayName("Stone");
        solidData.getBaseSection().setShape(assetManager.getAsset("engine:cube", BlockShape.class).get());
        solidData.getBaseSection().setTranslucent(false);
        solidData.setBlockFamily(SymmetricFamily.class);
        assetManager.loadAsset(new ResourceUrn("engine:stone"), solidData, BlockFamilyDefinition.class);
        solid = blockManager.getBlock(new BlockUri(new ResourceUrn("engine:stone")));

        BlockFamilyDefinitionData solidMediumLightData = new BlockFamilyDefinitionData();
        solidMediumLightData.getBaseSection().setDisplayName("SolidMediumLight");
        solidMediumLightData.getBaseSection().setShape(assetManager.getAsset("engine:cube", BlockShape.class).get());
        solidMediumLightData.getBaseSection().setTranslucent(false);
        solidMediumLightData.getBaseSection().setLuminance((byte) 5);
        solidMediumLightData.setBlockFamily(SymmetricFamily.class);
        assetManager.loadAsset(new ResourceUrn("engine:solidMediumLight"), solidMediumLightData,
                BlockFamilyDefinition.class);
        solidMediumLight = blockManager.getBlock(new BlockUri(new ResourceUrn("engine:solidMediumLight")));

        air = blockManager.getBlock(BlockManager.AIR_ID);
    }

    @Test
    public void testAddLightInVacuum() {
        StubPropagatorWorldView worldView = new StubPropagatorWorldView(testingRegion, air);
        worldView.setBlockAt(Vector3i.zero(), fullLight);

        BatchPropagator propagator = new StandardBatchPropagator(lightRules, worldView);
        propagator.process(new BlockChange(ZERO_VECTOR, air, fullLight));

        assertEquals(fullLight.getLuminance(), worldView.getValueAt(Vector3i.zero()));
        assertEquals(fullLight.getLuminance() - 1, worldView.getValueAt(new Vector3i(0, 1, 0)));
        assertEquals(fullLight.getLuminance() - 14, worldView.getValueAt(new Vector3i(0, 14, 0)));
        for (int i = 1; i < fullLight.getLuminance(); ++i) {
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(fullLight.getLuminance() - i, worldView.getValueAt(pos));
            }
        }
    }

    @Test
    public void testRemoveLightInVacuum() {
        StubPropagatorWorldView worldView = new StubPropagatorWorldView(testingRegion, air);
        worldView.setBlockAt(Vector3i.zero(), fullLight);
        BatchPropagator propagator = new StandardBatchPropagator(lightRules, worldView);
        propagator.process(new BlockChange(ZERO_VECTOR, air, fullLight));

        worldView.setBlockAt(Vector3i.zero(), air);
        propagator.process(new BlockChange(ZERO_VECTOR, fullLight, air));

        assertEquals(0, worldView.getValueAt(Vector3i.zero()));
        for (int i = 1; i < fullLight.getLuminance(); ++i) {
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(0, worldView.getValueAt(pos));
            }
        }
    }

    @Test
    public void testReduceLight() {
        StubPropagatorWorldView worldView = new StubPropagatorWorldView(testingRegion, air);
        worldView.setBlockAt(Vector3i.zero(), fullLight);
        BatchPropagator propagator = new StandardBatchPropagator(lightRules, worldView);
        propagator.process(new BlockChange(ZERO_VECTOR, air, fullLight));

        worldView.setBlockAt(Vector3i.zero(), weakLight);
        propagator.process(new BlockChange(ZERO_VECTOR, fullLight, weakLight));

        assertEquals(weakLight.getLuminance(), worldView.getValueAt(Vector3i.zero()));
        for (int i = 1; i < 15; ++i) {
            byte expectedLuminance = (byte) Math.max(0, weakLight.getLuminance() - i);
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(expectedLuminance, worldView.getValueAt(pos));
            }
        }
    }

    @Test
    public void testAddOverlappingLights() {
        Vector3i lightPos = new Vector3i(5, 0, 0);

        StubPropagatorWorldView worldView = new StubPropagatorWorldView(ChunkConstants.CHUNK_REGION, air);
        worldView.setBlockAt(Vector3i.zero(), fullLight);
        worldView.setBlockAt(lightPos, fullLight);
        BatchPropagator propagator = new StandardBatchPropagator(lightRules, worldView);
        propagator.process(new BlockChange(ZERO_VECTOR, air, fullLight), new BlockChange(JomlUtil.from(lightPos), air
                , fullLight));

        assertEquals(fullLight.getLuminance(), worldView.getValueAt(Vector3i.zero()));
        assertEquals(fullLight.getLuminance() - 1, worldView.getValueAt(new Vector3i(1, 0, 0)));
        assertEquals(fullLight.getLuminance() - 2, worldView.getValueAt(new Vector3i(2, 0, 0)));
        assertEquals(fullLight.getLuminance() - 2, worldView.getValueAt(new Vector3i(3, 0, 0)));
        assertEquals(fullLight.getLuminance() - 1, worldView.getValueAt(new Vector3i(4, 0, 0)));
        assertEquals(fullLight.getLuminance(), worldView.getValueAt(new Vector3i(5, 0, 0)));
    }

    @Test
    public void testRemoveOverlappingLight() {
        Vector3i lightPos = new Vector3i(5, 0, 0);

        StubPropagatorWorldView worldView = new StubPropagatorWorldView(testingRegion, air);
        worldView.setBlockAt(Vector3i.zero(), fullLight);
        worldView.setBlockAt(lightPos, fullLight);
        BatchPropagator propagator = new StandardBatchPropagator(lightRules, worldView);
        propagator.process(new BlockChange(ZERO_VECTOR, air, fullLight), new BlockChange(JomlUtil.from(lightPos), air
                , fullLight));

        worldView.setBlockAt(lightPos, air);
        propagator.process(new BlockChange(JomlUtil.from(lightPos), fullLight, air));

        for (int i = 0; i < 16; ++i) {
            byte expectedLuminance = (byte) Math.max(0, fullLight.getLuminance() - i);
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(expectedLuminance, worldView.getValueAt(pos));
            }
        }
    }

    @Test
    public void testRemoveLightOverlappingAtEdge() {
        Vector3i lightPos = new Vector3i(2, 0, 0);

        StubPropagatorWorldView worldView = new StubPropagatorWorldView(testingRegion, air);
        worldView.setBlockAt(Vector3i.zero(), weakLight);
        worldView.setBlockAt(lightPos, weakLight);
        BatchPropagator propagator = new StandardBatchPropagator(lightRules, worldView);
        propagator.process(new BlockChange(ZERO_VECTOR, air, weakLight), new BlockChange(JomlUtil.from(lightPos), air
                , weakLight));

        worldView.setBlockAt(lightPos, air);
        propagator.process(new BlockChange(JomlUtil.from(lightPos), weakLight, air));

        for (int i = 0; i < weakLight.getLuminance() + 1; ++i) {
            byte expectedLuminance = (byte) Math.max(0, weakLight.getLuminance() - i);
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(expectedLuminance, worldView.getValueAt(pos));
            }
        }
    }

    @Test
    public void testAddLightInLight() {
        StubPropagatorWorldView worldView = new StubPropagatorWorldView(testingRegion, air);
        worldView.setBlockAt(new Vector3i(2, 0, 0), mediumLight);
        BatchPropagator propagator = new StandardBatchPropagator(lightRules, worldView);
        propagator.process(new BlockChange(JomlUtil.from(new Vector3i(2, 0, 0)), air, mediumLight));

        worldView.setBlockAt(Vector3i.zero(), fullLight);
        propagator.process(new BlockChange(ZERO_VECTOR, air, fullLight));

        for (int i = 0; i < fullLight.getLuminance() + 1; ++i) {
            byte expectedLuminance = (byte) Math.max(0, fullLight.getLuminance() - i);
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(expectedLuminance, worldView.getValueAt(pos));
            }
        }
    }

    @Test
    public void testAddAdjacentLights() {
        StubPropagatorWorldView worldView = new StubPropagatorWorldView(testingRegion, air);
        worldView.setBlockAt(new Vector3i(1, 0, 0), mediumLight);
        worldView.setBlockAt(new Vector3i(0, 0, 0), mediumLight);
        BatchPropagator propagator = new StandardBatchPropagator(lightRules, worldView);
        propagator.process(new BlockChange(JomlUtil.from(new Vector3i(1, 0, 0)), air, mediumLight),
                new BlockChange(ZERO_VECTOR, air, mediumLight));

        for (int i = 0; i < fullLight.getLuminance() + 1; ++i) {
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                int dist = Math.min(Vector3i.zero().gridDistance(pos), new Vector3i(1, 0, 0).gridDistance(pos));
                byte expectedLuminance = (byte) Math.max(mediumLight.getLuminance() - dist, 0);
                assertEquals(expectedLuminance, worldView.getValueAt(pos));
            }
        }
    }

    @Test
    public void testAddWeakLightNextToStrongLight() {
        StubPropagatorWorldView worldView = new StubPropagatorWorldView(testingRegion, air);
        worldView.setBlockAt(new Vector3i(0, 0, 0), fullLight);
        BatchPropagator propagator = new StandardBatchPropagator(lightRules, worldView);
        propagator.process(new BlockChange(ZERO_VECTOR, air, fullLight));

        worldView.setBlockAt(new Vector3i(1, 0, 0), weakLight);
        propagator.process(new BlockChange(JomlUtil.from(new Vector3i(1, 0, 0)), air, weakLight));
        assertEquals(14, worldView.getValueAt(new Vector3i(1, 0, 0)));
    }

    @Test
    public void testRemoveAdjacentLights() {
        StubPropagatorWorldView worldView = new StubPropagatorWorldView(testingRegion, air);
        worldView.setBlockAt(new Vector3i(1, 0, 0), mediumLight);
        worldView.setBlockAt(new Vector3i(0, 0, 0), mediumLight);
        BatchPropagator propagator = new StandardBatchPropagator(lightRules, worldView);
        propagator.process(new BlockChange(JomlUtil.from(new Vector3i(1, 0, 0)), air, mediumLight),
                new BlockChange(ZERO_VECTOR, air, mediumLight));

        worldView.setBlockAt(new Vector3i(1, 0, 0), air);
        worldView.setBlockAt(new Vector3i(0, 0, 0), air);
        propagator.process(new BlockChange(JomlUtil.from(new Vector3i(1, 0, 0)), mediumLight, air),
                new BlockChange(ZERO_VECTOR, mediumLight, air));

        for (int i = 0; i < fullLight.getLuminance() + 1; ++i) {
            byte expectedLuminance = (byte) 0;
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(expectedLuminance, worldView.getValueAt(pos));
            }
        }
    }

    @Test
    public void testAddSolidBlocksLight() {
        StubPropagatorWorldView worldView = new StubPropagatorWorldView(ChunkConstants.CHUNK_REGION, air);
        worldView.setBlockAt(new Vector3i(0, 0, 0), mediumLight);
        BatchPropagator propagator = new StandardBatchPropagator(lightRules, worldView);
        propagator.process(new BlockChange(ZERO_VECTOR, air, mediumLight));

        worldView.setBlockAt(new Vector3i(1, 0, 0), solid);
        propagator.process(new BlockChange(JomlUtil.from(new Vector3i(1, 0, 0)), air, solid));

        assertEquals(0, worldView.getValueAt(new Vector3i(1, 0, 0)));
        assertEquals(1, worldView.getValueAt(new Vector3i(2, 0, 0)));
    }

    @Test
    public void testRemoveSolidAllowsLight() {
        StubPropagatorWorldView worldView = new StubPropagatorWorldView(testingRegion, air);
        for (Vector3i pos : Region3i.createFromCenterExtents(new Vector3i(1, 0, 0), new Vector3i(0, 30, 30))) {
            worldView.setBlockAt(pos, solid);
        }
        worldView.setBlockAt(new Vector3i(0, 0, 0), fullLight);
        BatchPropagator propagator = new StandardBatchPropagator(lightRules, worldView);
        propagator.process(new BlockChange(ZERO_VECTOR, air, fullLight));

        assertEquals(0, worldView.getValueAt(new Vector3i(1, 0, 0)));

        worldView.setBlockAt(new Vector3i(1, 0, 0), air);
        propagator.process(new BlockChange(JomlUtil.from(new Vector3i(1, 0, 0)), solid, air));

        assertEquals(14, worldView.getValueAt(new Vector3i(1, 0, 0)));
        assertEquals(13, worldView.getValueAt(new Vector3i(2, 0, 0)));
    }

    @Test
    public void testRemoveSolidAndLight() {
        StubPropagatorWorldView worldView = new StubPropagatorWorldView(testingRegion, air);
        for (Vector3i pos : Region3i.createFromCenterExtents(new Vector3i(1, 0, 0), new Vector3i(0, 30, 30))) {
            worldView.setBlockAt(pos, solid);
        }
        worldView.setBlockAt(new Vector3i(0, 0, 0), fullLight);
        BatchPropagator propagator = new StandardBatchPropagator(lightRules, worldView);
        propagator.process(new BlockChange(ZERO_VECTOR, air, fullLight));

        assertEquals(0, worldView.getValueAt(new Vector3i(1, 0, 0)));

        worldView.setBlockAt(new Vector3i(1, 0, 0), air);
        worldView.setBlockAt(new Vector3i(0, 0, 0), air);
        propagator.process(new BlockChange(JomlUtil.from(new Vector3i(1, 0, 0)), solid, air),
                new BlockChange(ZERO_VECTOR, fullLight, air));

        for (int i = 0; i < fullLight.getLuminance() + 1; ++i) {
            byte expectedLuminance = (byte) 0;
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(expectedLuminance, worldView.getValueAt(pos));
            }
        }
    }
}
