package pneumaticCraft.common.progwidgets;

import net.minecraft.util.ResourceLocation;
import pneumaticCraft.common.entity.living.EntityDrone;
import pneumaticCraft.lib.Textures;

public class ProgWidgetDroneConditionPressure extends ProgWidgetDroneEvaluation{

    @Override
    public Class<? extends IProgWidget>[] getParameters(){
        return new Class[]{ProgWidgetString.class};
    }

    @Override
    public String getWidgetString(){
        return "conditionDronePressure";
    }

    @Override
    protected int getCount(EntityDrone drone){
        return (int)drone.getPressure(null);
    }

    @Override
    protected ResourceLocation getTexture(){
        return Textures.PROG_WIDGET_CONDITION_DRONE_PRESSURE;
    }

}
