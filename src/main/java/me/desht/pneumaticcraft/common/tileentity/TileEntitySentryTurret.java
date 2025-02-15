package me.desht.pneumaticcraft.common.tileentity;

import com.mojang.authlib.GameProfile;
import me.desht.pneumaticcraft.api.item.IItemRegistry.EnumUpgrade;
import me.desht.pneumaticcraft.common.ai.StringFilterEntitySelector;
import me.desht.pneumaticcraft.common.block.Blockss;
import me.desht.pneumaticcraft.common.inventory.handler.BaseItemStackHandler;
import me.desht.pneumaticcraft.common.item.ItemGunAmmo;
import me.desht.pneumaticcraft.common.minigun.Minigun;
import me.desht.pneumaticcraft.common.network.DescSynced;
import me.desht.pneumaticcraft.common.network.GuiSynced;
import me.desht.pneumaticcraft.common.network.NetworkHandler;
import me.desht.pneumaticcraft.common.network.PacketPlaySound;
import me.desht.pneumaticcraft.common.util.PneumaticCraftUtils;
import me.desht.pneumaticcraft.common.util.fakeplayer.FakeNetHandlerPlayerServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class TileEntitySentryTurret extends TileEntityTickableBase implements IRedstoneControlled, IGUITextFieldSensitive {
    private static final int INVENTORY_SIZE = 4;

    private final ItemStackHandler inventory = new TurretItemStackHandler(this);
    @GuiSynced
    private String entityFilter = "";
    @GuiSynced
    private int redstoneMode;
    @DescSynced
    private double range;
    @DescSynced
    private boolean activated;
    @DescSynced
    private ItemStack minigunColorStack = ItemStack.EMPTY;
    private Minigun minigun;
    @DescSynced
    private int targetEntityId = -1;
    @DescSynced
    private boolean sweeping;
    private final SentryTurretEntitySelector entitySelector = new SentryTurretEntitySelector();
    private double rangeSq;

    public TileEntitySentryTurret() {
        super(4);
        addApplicableUpgrade(EnumUpgrade.RANGE);
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote) {
            if (getMinigun().getAttackTarget() == null && redstoneAllows()) {
                getMinigun().setSweeping(true);
                if ((getWorld().getTotalWorldTime() & 0xF) == 0) {
                    List<EntityLivingBase> entities = getWorld().getEntitiesWithinAABB(EntityLivingBase.class, getTargetingBoundingBox(), entitySelector);
                    if (entities.size() > 0) {
                        entities.sort(new TargetSorter());
                        getMinigun().setAttackTarget(entities.get(0));
                        targetEntityId = entities.get(0).getEntityId();
                    }
                }
            } else {
                getMinigun().setSweeping(false);
            }
            EntityLivingBase target = getMinigun().getAttackTarget();
            if (target != null) {
                if (!redstoneAllows() || !entitySelector.apply(target)) {
                    getMinigun().setAttackTarget(null);
                    targetEntityId = -1;
                } else {
                    if ((getWorld().getTotalWorldTime() & 0x7) == 0) {
                        getFakePlayer().setPosition(getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5); //Make sure the knockback has the right direction.
                        boolean usedAmmo = getMinigun().tryFireMinigun(target);
                        if (usedAmmo) {
                            for (int i = 0; i < inventory.getSlots(); i++) {
                                if (!inventory.getStackInSlot(i).isEmpty()) {
                                    inventory.setStackInSlot(i, ItemStack.EMPTY);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        getMinigun().update(getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5);
    }

    private boolean canSeeEntity(Entity entity) {
        Vec3d entityVec = new Vec3d(entity.posX + entity.width / 2, entity.posY + entity.height / 2, entity.posZ + entity.width / 2);
        Vec3d tileVec = new Vec3d(getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5);
        RayTraceResult trace = getWorld().rayTraceBlocks(entityVec, tileVec);
        return trace != null && trace.getBlockPos().equals(getPos());
    }

    private AxisAlignedBB getTargetingBoundingBox() {
        return new AxisAlignedBB(getPos().getX() - range, getPos().getY() - range, getPos().getZ() - range, getPos().getX() + range + 1, getPos().getY() + range + 1, getPos().getZ() + range + 1);
    }

    @Override
    protected void onFirstServerUpdate() {
        super.onFirstServerUpdate();
        updateAmmo();
        setText(0, entityFilter);
    }

    @Override
    public void onDescUpdate() {
        super.onDescUpdate();
        Entity entity = getWorld().getEntityByID(targetEntityId);
        if (entity instanceof EntityLivingBase) {
            getMinigun().setAttackTarget((EntityLivingBase) entity);
        } else {
            getMinigun().setAttackTarget(null);
        }
    }

    public Minigun getMinigun() {
        if (minigun == null) {
            minigun = new MinigunSentryTurret();
            minigun.setWorld(getWorld());
            if (!getWorld().isRemote) {
                minigun.setPlayer(getFakePlayer());
            }
        }
        return minigun;
    }

    private EntityPlayer getFakePlayer() {
        FakePlayer fakePlayer = FakePlayerFactory.get((WorldServer) getWorld(), new GameProfile(null, "Sentry Turret"));
        if (fakePlayer.connection == null) {
            fakePlayer.connection = new FakeNetHandlerPlayerServer(FMLCommonHandler.instance().getMinecraftServerInstance(), fakePlayer);
        }
        return fakePlayer;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("Items", inventory.serializeNBT());
        tag.setByte("redstoneMode", (byte) redstoneMode);
        tag.setString("entityFilter", entityFilter);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        inventory.deserializeNBT(tag.getCompoundTag("Items"));
        redstoneMode = tag.getByte("redstoneMode");
        entityFilter = tag.getString("entityFilter");
    }

    @Override
    public boolean redstoneAllows() {
        return redstoneMode == 3 || super.redstoneAllows();
    }

    @Override
    public int getRedstoneMode() {
        return redstoneMode;
    }

    @Override
    public void handleGUIButtonPress(int buttonID, EntityPlayer player) {
        if (buttonID == 0) {
            redstoneMode++;
            if (redstoneMode > 2) redstoneMode = 0;
        }
    }

    /**
     * Returns the name of the inventory.
     */
    @Override
    public String getName() {
        return Blockss.SENTRY_TURRET.getTranslationKey();
    }

    private class TurretItemStackHandler extends BaseItemStackHandler {
        TurretItemStackHandler(TileEntity te) {
            super(te, INVENTORY_SIZE);
        }

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            updateAmmo();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack itemStack) {
            return itemStack.isEmpty() || itemStack.getItem() instanceof ItemGunAmmo;
        }
    }

    private void updateAmmo() {
        ItemStack ammo = ItemStack.EMPTY;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ammo = inventory.getStackInSlot(i);
            if (!ammo.isEmpty()) {
                break;
            }
        }
        getMinigun().setAmmoStack(ammo);
        recalculateRange();
    }

    @Override
    public IItemHandlerModifiable getPrimaryInventory() {
        return inventory;
    }

    @Override
    protected void onUpgradesChanged() {
        super.onUpgradesChanged();
        if (getWorld() != null) {
            // this can get called when reading nbt on load when world = null
            // in that case, range is recalculated in onFirstServerUpdate()
            recalculateRange();
        }
    }

    private void recalculateRange() {
        range = 16.0 + Math.min(16, getUpgrades(EnumUpgrade.RANGE));
        ItemStack ammoStack = getMinigun().getAmmoStack();
        if (ammoStack.getItem() instanceof ItemGunAmmo) {
            range *= ((ItemGunAmmo) ammoStack.getItem()).getRangeMultiplier(ammoStack);
        }
        rangeSq = range * range;
    }

    private class MinigunSentryTurret extends Minigun {

        MinigunSentryTurret() {
            super(true);
        }

        @Override
        public boolean isMinigunActivated() {
            return activated;
        }

        @Override
        public void setMinigunActivated(boolean activated) {
            TileEntitySentryTurret.this.activated = activated;
        }

        @Override
        public void setAmmoColorStack(@Nonnull ItemStack ammo) {
            minigunColorStack = ammo;
        }

        @Override
        public int getAmmoColor() {
            return getAmmoColor(minigunColorStack);
        }

        @Override
        public void playSound(SoundEvent soundName, float volume, float pitch) {
            NetworkHandler.sendToAllAround(new PacketPlaySound(soundName, SoundCategory.BLOCKS,
                    getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5,
                    volume, pitch, false), world);
        }

        @Override
        public void setSweeping(boolean sweeping) {
            TileEntitySentryTurret.this.sweeping = sweeping;
        }

        @Override
        public boolean isSweeping() {
            return sweeping;
        }

        @Override
        public Object getSoundSource() {
            return TileEntitySentryTurret.this.getPos();
        }
    }

    private class TargetSorter implements Comparator<Entity> {
        private final BlockPos pos;

        TargetSorter() {
            pos = new BlockPos(getPos().getX(), getPos().getY(), getPos().getZ());
        }

        @Override
        public int compare(Entity arg0, Entity arg1) {
            double dist1 = PneumaticCraftUtils.distBetweenSq(pos, arg0.getPosition());
            double dist2 = PneumaticCraftUtils.distBetweenSq(pos, arg1.getPosition());
            return Double.compare(dist1, dist2);
        }
    }

    private class SentryTurretEntitySelector extends StringFilterEntitySelector {
        @Override
        public boolean apply(Entity entity) {
            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                if (player.capabilities.isCreativeMode || isExcludedBySecurityStations(player)) return false;
            }
            return super.apply(entity) && inRange(entity) && canSeeEntity(entity);
        }

        private boolean inRange(Entity entity) {
            return PneumaticCraftUtils.distBetweenSq(new BlockPos(getPos().getX(), getPos().getY(), getPos().getZ()), entity.posX, entity.posY, entity.posZ) <= rangeSq;
        }

        private boolean isExcludedBySecurityStations(EntityPlayer player) {
            Iterator<TileEntitySecurityStation> iterator = TileEntitySecurityStation.getSecurityStations(getWorld(), getPos(), false).iterator();
            if (iterator.hasNext()) { //When there are Security Stations, all stations need to be allowing the player.
                while (iterator.hasNext()) {
                    if (!iterator.next().doesAllowPlayer(player)) return false;
                }
                return true;
            } else {
                return false; //When there are no Security Stations at all, the player isn't automatically 'allowed to live'.
            }
        }
    }

    @Override
    public void setText(int textFieldID, String text) {
        entityFilter = text;
        if (world != null && !world.isRemote) {
            entitySelector.setFilter(text);
            if (minigun != null) minigun.setAttackTarget(null);
            markDirty();
        }
    }

    @Override
    public String getText(int textFieldID) {
        return entityFilter;
    }
}
