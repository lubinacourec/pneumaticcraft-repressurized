package me.desht.pneumaticcraft.common.progwidgets;

import me.desht.pneumaticcraft.client.gui.GuiProgrammer;
import me.desht.pneumaticcraft.client.gui.programmer.GuiProgWidgetAreaShow;
import me.desht.pneumaticcraft.common.ai.DroneAIManager;
import me.desht.pneumaticcraft.common.config.ConfigHandler;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.*;
import java.util.function.Predicate;

public abstract class ProgWidgetAreaItemBase extends ProgWidget implements IAreaProvider, IEntityProvider,
        IItemFiltering, IVariableWidget {
    private List<BlockPos> areaListCache;
    private Set<BlockPos> areaSetCache;
    private Map<String, BlockPos> areaVariableStates;
    protected DroneAIManager aiManager;
    private boolean canCache = true;
    private EntityFilterPair entityFilters;

    @Override
    public boolean hasStepInput() {
        return true;
    }

    @Override
    public Class<? extends IProgWidget> returnType() {
        return null;
    }

    @Override
    public Class<? extends IProgWidget>[] getParameters() {
        return new Class[]{ProgWidgetArea.class, ProgWidgetItemFilter.class};
    }

    @Override
    public void addErrors(List<String> curInfo, List<IProgWidget> widgets) {
        super.addErrors(curInfo, widgets);
        if (getConnectedParameters()[0] == null) {
            curInfo.add("gui.progWidget.area.error.noArea");
        }
        Set<BlockPos> areaSet = getCachedAreaSet();
        if (areaSet.size() > ConfigHandler.general.maxProgrammingArea) {
            curInfo.add(I18n.format("gui.progWidget.area.error.areaTooBig", ConfigHandler.general.maxProgrammingArea));
        }
        EntityFilterPair.addErrors(this, curInfo);
    }

    public static IBlockAccess getCache(Collection<BlockPos> area, World world) {
        if (area.isEmpty()) return world;
        AxisAlignedBB aabb = getExtents(area);
        return new ChunkCache(world, new BlockPos(aabb.minX, aabb.minY, aabb.minZ), new BlockPos(aabb.maxX, aabb.maxY, aabb.maxZ), 0);
    }

    public static AxisAlignedBB getExtents(Collection<BlockPos> areaSet) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : areaSet) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        return new AxisAlignedBB(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    public synchronized List<BlockPos> getCachedAreaList() {
        if (areaListCache != null) {
            if (!canCache || updateVariables()) {
                areaSetCache = new HashSet<>(areaListCache.size());
                getArea(areaSetCache);
                areaListCache = new ArrayList<>(areaSetCache.size());
                areaListCache.addAll(areaSetCache);
            }
        } else {
            areaSetCache = new HashSet<>();
            getArea(areaSetCache);
            areaListCache = new ArrayList<>(areaSetCache.size());
            areaListCache.addAll(areaSetCache);
            initializeVariableCache();
        }
        return areaListCache;
    }

    public synchronized Set<BlockPos> getCachedAreaSet() {
        getCachedAreaList();
        return areaSetCache;
    }

    protected synchronized void invalidateAreaCache() {
        areaListCache = null;
        areaSetCache = null;
    }

    private void initializeVariableCache() {
        areaVariableStates = new HashMap<>();
        ProgWidgetArea whitelistWidget = (ProgWidgetArea) getConnectedParameters()[0];
        ProgWidgetArea blacklistWidget = (ProgWidgetArea) getConnectedParameters()[getParameters().length];
        if (whitelistWidget == null) return;
        ProgWidgetArea widget = whitelistWidget;
        while (widget != null) {
            if (!widget.type.isDeterministic()) canCache = false;
            if (aiManager != null) {
                if (!widget.getCoord1Variable().equals(""))
                    areaVariableStates.put(widget.getCoord1Variable(), aiManager.getCoordinate(widget.getCoord1Variable()));
                if (!widget.getCoord2Variable().equals(""))
                    areaVariableStates.put(widget.getCoord2Variable(), aiManager.getCoordinate(widget.getCoord2Variable()));
            }
            widget = (ProgWidgetArea) widget.getConnectedParameters()[0];
        }
        widget = blacklistWidget;
        while (widget != null) {
            if (!widget.type.isDeterministic()) canCache = false;
            if (aiManager != null) {
                if (!widget.getCoord1Variable().equals(""))
                    areaVariableStates.put(widget.getCoord1Variable(), aiManager.getCoordinate(widget.getCoord1Variable()));
                if (!widget.getCoord2Variable().equals(""))
                    areaVariableStates.put(widget.getCoord2Variable(), aiManager.getCoordinate(widget.getCoord2Variable()));
            }
            widget = (ProgWidgetArea) widget.getConnectedParameters()[0];
        }
    }

    private boolean updateVariables() {
        boolean varChanged = false;
        for (Map.Entry<String, BlockPos> entry : areaVariableStates.entrySet()) {
            BlockPos newValue = aiManager.getCoordinate(entry.getKey());
            if (!newValue.equals(entry.getValue())) {
                varChanged = true;
                entry.setValue(newValue);
            }
        }
        return varChanged;
    }

    @Override
    public void getArea(Set<BlockPos> area) {
        getArea(area, (ProgWidgetArea) getConnectedParameters()[0], (ProgWidgetArea) getConnectedParameters()[getParameters().length]);
    }

    public static void getArea(Set<BlockPos> area, ProgWidgetArea whitelistWidget, ProgWidgetArea blacklistWidget) {
        if (whitelistWidget == null) return;
        ProgWidgetArea widget = whitelistWidget;
        while (widget != null) {
            widget.getArea(area);
            widget = (ProgWidgetArea) widget.getConnectedParameters()[0];
        }
        widget = blacklistWidget;
        Set<BlockPos> blacklistedArea = new HashSet<>();
        while (widget != null) {
            widget.getArea(blacklistedArea);
            widget = (ProgWidgetArea) widget.getConnectedParameters()[0];
        }
        area.removeAll(blacklistedArea);
    }

    @Override
    public boolean isItemValidForFilters(ItemStack item) {
        return isItemValidForFilters(item, null);
    }

    public boolean isItemValidForFilters(ItemStack item, IBlockState blockState) {
        return ProgWidgetItemFilter.isItemValidForFilters(item,
                ProgWidget.getConnectedWidgetList(this, 1),
                ProgWidget.getConnectedWidgetList(this, getParameters().length + 1),
                blockState
        );
    }

    public boolean isItemFilterEmpty() {
        return getConnectedParameters()[1] == null && getConnectedParameters()[3] == null;
    }

    public List<Entity> getEntitiesInArea(World world, Predicate<? super Entity> filter) {
        return getEntitiesInArea(
                (ProgWidgetArea) getConnectedParameters()[0],
                (ProgWidgetArea) getConnectedParameters()[getParameters().length],
                world, filter, null
        );
    }

    @Override
    public List<Entity> getValidEntities(World world) {
        if (entityFilters == null) {
            entityFilters = new EntityFilterPair(this);
        }
        return entityFilters.getValidEntities(world);
    }

    @Override
    public boolean isEntityValid(Entity entity) {
        if (entityFilters == null) {
            entityFilters = new EntityFilterPair(this);
        }
        return entityFilters.isEntityValid(entity);
    }

    public static List<Entity> getEntitiesInArea(ProgWidgetArea whitelistWidget, ProgWidgetArea blacklistWidget, World world,
                                                 Predicate<? super Entity> whitelistPredicate, Predicate<? super Entity> blacklistPredicate) {
        if (whitelistWidget == null) return new ArrayList<>();
        Set<Entity> entities = new HashSet<>();
        ProgWidgetArea widget = whitelistWidget;
        if (whitelistPredicate == null) whitelistPredicate = e -> true;
        while (widget != null) {
            entities.addAll(widget.getEntitiesWithinArea(world, whitelistPredicate));
            widget = (ProgWidgetArea) widget.getConnectedParameters()[0];
        }
        widget = blacklistWidget;
        while (widget != null) {
            entities.removeAll(widget.getEntitiesWithinArea(world, whitelistPredicate));
            widget = (ProgWidgetArea) widget.getConnectedParameters()[0];
        }
        if (blacklistPredicate != null) {
            entities.removeIf(blacklistPredicate);
        }
        return new ArrayList<>(entities);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getOptionWindow(GuiProgrammer guiProgrammer) {
        return new GuiProgWidgetAreaShow(this, guiProgrammer);
    }

    @Override
    public WidgetDifficulty getDifficulty() {
        return WidgetDifficulty.EASY;
    }

    @Override
    public void setAIManager(DroneAIManager aiManager) {
        this.aiManager = aiManager;
    }

    @Override
    public void addVariables(Set<String> variables) {
    }
}
