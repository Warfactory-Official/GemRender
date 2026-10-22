package com.wf.gemrender.direct;

//? if >=26.1 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GemRenderArmorModelTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nullWearerStillSelectsTheAppearanceOnce() {
        var appearance = new RecordingAppearance();
        var model = model(appearance).prepare(null, ItemStack.EMPTY, EquipmentSlot.CHEST);
        var pose = new PoseStack();

        assertThat(model.gemrender$submitArmour(pose, null, 0, 0)).isTrue();
        assertThat(appearance.calls).isEqualTo(1);
        assertThat(appearance.wearer).isNull();
        assertThat(appearance.slot).isEqualTo(EquipmentSlot.CHEST);

        assertThat(model.gemrender$submitArmour(pose, null, 0, 0)).isTrue();
        assertThat(appearance.calls).isEqualTo(1);
    }

    @Test
    void equipmentUsesTheStacksSlotInsteadOfTheSharedLayer() {
        var appearance = new RecordingAppearance();
        var stack = new ItemStack(new Holder.Direct<>(Items.DIAMOND_BOOTS, DataComponentMap.EMPTY));
        stack.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        var model = model(appearance).prepare(null, stack, EquipmentSlot.CHEST);

        assertThat(model.gemrender$submitEquipment(new PoseStack(), null, 0, 0)).isTrue();
        assertThat(appearance.calls).isEqualTo(1);
        assertThat(appearance.wearer).isNull();
        assertThat(appearance.stack).isSameAs(stack);
        assertThat(appearance.slot).isEqualTo(EquipmentSlot.FEET);
    }

    private static GemRenderArmorModel model(ArmorAppearance appearance) {
        var root = LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f), 64, 32)
                .bakeRoot();
        return new GemRenderArmorModel(root, appearance, Map.of());
    }

    private static final class RecordingAppearance implements ArmorAppearance {
        private int calls;
        private LivingEntity wearer;
        private ItemStack stack;
        private EquipmentSlot slot;

        @Override
        public GemRenderGltfModel model(LivingEntity entity, ItemStack stack, EquipmentSlot slot) {
            calls++;
            wearer = entity;
            this.stack = stack;
            this.slot = slot;
            return null;
        }
    }
}
*///?}
