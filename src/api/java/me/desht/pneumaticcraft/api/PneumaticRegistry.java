package me.desht.pneumaticcraft.api;

import me.desht.pneumaticcraft.api.client.IClientRegistry;
import me.desht.pneumaticcraft.api.client.pneumaticHelmet.IPneumaticHelmetRegistry;
import me.desht.pneumaticcraft.api.drone.IDroneRegistry;
import me.desht.pneumaticcraft.api.harvesting.IHarvestRegistry;
import me.desht.pneumaticcraft.api.item.IItemRegistry;
import me.desht.pneumaticcraft.api.recipe.IPneumaticRecipeRegistry;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerSupplier;
import me.desht.pneumaticcraft.api.tileentity.IHeatRegistry;
import me.desht.pneumaticcraft.api.universalSensor.ISensorRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;

/**
 * This class can be used to register and access various things to and from the mod.  All access is via
 * {@link PneumaticRegistry#getInstance()}
 */
public final class PneumaticRegistry {
    private static IPneumaticCraftInterface instance;

    /**
     * Get an instance of the top-level API interface.
     *
     * @return the top-level API interface
     */
    public static IPneumaticCraftInterface getInstance() {
        return instance;
    }

    /**
     * Do not call this method yourself.  For PneumaticCraft internal usage only!
     * @param inter reference to the API interface object
     */
    public static void init(IPneumaticCraftInterface inter) {
        if (instance == null && Loader.instance().activeModContainer().getModId().equals("pneumaticcraft"))
            instance = inter;//only allow initialization once; by PneumaticCraft
        else throw new IllegalStateException("Only pneumaticcraft is allowed to call this method!");
    }

    /**
     * Retrieve an instance of this via {@link PneumaticRegistry#getInstance()}
     */
    public interface IPneumaticCraftInterface {

        IPneumaticRecipeRegistry getRecipeRegistry();

        IAirHandlerSupplier getAirHandlerSupplier();

        IPneumaticHelmetRegistry getHelmetRegistry();

        IDroneRegistry getDroneRegistry();

        IHeatRegistry getHeatRegistry();

        IClientRegistry getGuiRegistry();

        ISensorRegistry getSensorRegistry();

        IItemRegistry getItemRegistry();
        
        IHarvestRegistry getHarvestRegistry();

        /**
         * Adds a burnable liquid to the Liquid Compressor's available burnable fuels.  This also allows a bucket
         * of that liquid to be used in furnaces, the burn time being half the mLPerBucket value.  Note that this
         * can also be manipulated via CraftTweaker.
         *
         * @param fluid the fluid to register
         * @param mLPerBucket the amount of mL generated for 1000mB of the fuel. As comparison, one piece of coal
         *                    generates 16000mL in an Air Compressor.
         */
        void registerFuel(Fluid fluid, int mLPerBucket);

        /**
         * Returns the number of Security Stations that disallow interaction with the given coordinate for the given
         * player. Usually you'd disallow interaction when this returns > 0.
         *
         * @param world the player's world
         * @param pos the position to check
         * @param player the player to check
         * @param showRangeLines when true, any Security Station that prevents interaction will show the line grid
         *                       (server --> client update is handled internally).
         * @return the number of Security Stations that disallow interaction for the given player.
         * @throws IllegalArgumentException when called from the client side
         */
        int getProtectingSecurityStations(World world, BlockPos pos, EntityPlayer player, boolean showRangeLines);

        /**
         * Used to register a liquid that represents liquid XP (e.g. CoFH Essence of Knowledge or OpenBlocks liquid XP).
         * This is used in the Aerial Interface to pump XP in/out of the player.
         *
         * @param fluid registered name of the fluid (may have been registered by another mod)
         * @param liquidToPointRatio the amount of liquid (in mB) used to get one XP point; use a value of 0 or less to
         *                          unregister this fluid
         */
        void registerXPLiquid(Fluid fluid, int liquidToPointRatio);

        /**
         * Register this fluid as a raw input to the refinery.
         *
         * @param fluid the fluid to register
         * @deprecated use {@link IPneumaticRecipeRegistry#registerRefineryRecipe(net.minecraftforge.fluids.FluidStack, net.minecraftforge.fluids.FluidStack...)}
         */
        @Deprecated
        void registerRefineryInput(Fluid fluid);

        /**
         * Register a fluid as a valid input for the Plastic Mixer.
         *
         * @deprecated use {@link IPneumaticRecipeRegistry#registerPlasticMixerRecipe(FluidStack, ItemStack, int, boolean, boolean, boolean, int)}
         * @param fluid the fluid to register
         * @param ratio amount in mB which will be used to make 1 solid plastic sheet
         */
        @Deprecated
        void registerPlasticFluid(Fluid fluid, int ratio);
    }
}
