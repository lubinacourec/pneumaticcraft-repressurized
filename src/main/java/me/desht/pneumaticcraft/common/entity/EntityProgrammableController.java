package me.desht.pneumaticcraft.common.entity;

import me.desht.pneumaticcraft.common.entity.living.EntityDroneBase;
import me.desht.pneumaticcraft.common.tileentity.TileEntityProgrammableController;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class EntityProgrammableController extends EntityDroneBase {
    private final TileEntityProgrammableController controller;

    public EntityProgrammableController(World world) {
        this(world, null);
    }

    public EntityProgrammableController(World world, TileEntityProgrammableController controller) {
        super(world);

        this.preventEntitySpawning = false;
        this.controller = controller;
    }

    /**
     * Returns true if other Entities should be prevented from moving through this Entity.
     */
    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /**
     * Returns true if this entity should push and be pushed by other entities when colliding.
     */
    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public void onUpdate() {
        if (controller != null) {
            if (controller.isInvalid()) setDead();
            if (digLaser != null) digLaser.update();
            oldPropRotation = propRotation;
            propRotation += 1;
        }
    }

    @Override
    protected double getLaserOffsetY() {
        return 0.45;
    }

    @Override
    public boolean attackEntityFrom(DamageSource p_70097_1_, float p_70097_2_) {
        return false;
    }

    @Override
    protected BlockPos getDugBlock() {
        return controller == null ? null : controller.getDugPosition();
    }

    @Override
    public ItemStack getDroneHeldItem() {
        return controller == null ? ItemStack.EMPTY : controller.getFakePlayer().getHeldItemMainhand();
    }
}
