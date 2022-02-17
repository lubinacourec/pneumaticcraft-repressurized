/*
 * This file is part of pnc-repressurized.
 *
 *     pnc-repressurized is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     pnc-repressurized is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with pnc-repressurized.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.desht.pneumaticcraft.client.gui.widget;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.datafixers.util.Either;
import com.mojang.math.Matrix3f;
import me.desht.pneumaticcraft.api.client.IGuiAnimatedStat;
import me.desht.pneumaticcraft.client.gui.AbstractPneumaticCraftContainerScreen;
import me.desht.pneumaticcraft.client.gui.AbstractPneumaticCraftScreen;
import me.desht.pneumaticcraft.client.render.ModRenderTypes;
import me.desht.pneumaticcraft.client.util.ClientUtils;
import me.desht.pneumaticcraft.client.util.GuiUtils;
import me.desht.pneumaticcraft.client.util.RenderUtils;
import me.desht.pneumaticcraft.client.util.TintColor;
import me.desht.pneumaticcraft.common.config.ConfigHelper;
import me.desht.pneumaticcraft.common.config.subconfig.ArmorHUDLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class WidgetAnimatedStat extends AbstractWidget implements IGuiAnimatedStat, ITooltipProvider {
    private static final int MIN_WIDTH_HEIGHT = 17;
    private static final int MAX_VISIBLE_LINES = 12;
    private static final int SCROLLBAR_MARGIN_WIDTH = 20;
    private static final int TOP_MARGIN_HEIGHT = 20;

    private IGuiAnimatedStat statAbove;
    private StatIcon statIcon;
    private final Screen gui;
    // the text we want to draw
    private final List<Component> textComponents = new ArrayList<>();
    // the text which is actually renderered, after having been wrapped to fit the stat's width
    private final List<FormattedCharSequence> reorderingProcessors = new ArrayList<>();
    // for each rendered line, should it be drawn with a drop shadow?
    private final List<Boolean> dropShadows = new ArrayList<>();
    private final List<AbstractWidget> subWidgets = new ArrayList<>();
    private int effectiveY;  // where the widget is actually rendered (if it has a "parent" stat, it will always render below that)
    private int reservedLines = 0; // space at the top where text isn't rendered
    private boolean autoLineWrap = true;

    // for interpolation purposes, to smoothly animate the widget expanding/contracting
    private int prevX;
    private int prevEffectiveY;
    private int prevWidth;
    private int prevHeight;

    private boolean isClicked = false;  // is the stat currently open?

    private int minWidth = MIN_WIDTH_HEIGHT;
    private int minHeight = MIN_WIDTH_HEIGHT;
    private int minExpandedHeight;
    private int minExpandedWidth;
    private int expandedWidth; // width of the stat when expanded
    private int expandedHeight; // height of the stat when expanded

    private int backGroundColor;
    private TintColor bgColorHi, bgColorLo;
    private boolean leftSided; // determines if the stat expands to the left or right
    private boolean doneExpanding;  // when true, the stat is fully open and text/subwidgets can be rendered
    private int curScroll;  // current scroll position
    private int lineSpacing = 10;
    private int widgetOffsetLeft = 0;
    private int widgetOffsetRight = 0;
    private boolean bevel = false;
    private WidgetVerticalScrollbar scrollBar = null;
    private boolean needTextRecalc = true;
    private int foregroundColor = 0xFFFFFFFF;
    private int titleColor = 0xFFFFFF00;
    private List<Component> extraTooltipText = new ArrayList<>();

    public WidgetAnimatedStat(Screen gui, Component title, int xPos, int yPos, int backGroundColor,
                              IGuiAnimatedStat statAbove, boolean leftSided) {
        super(xPos, yPos, MIN_WIDTH_HEIGHT, MIN_WIDTH_HEIGHT, title);

        this.gui = gui;
        this.statAbove = statAbove;
        this.leftSided = leftSided;
        this.statIcon = StatIcon.NONE;
        this.backGroundColor = backGroundColor;
        calculateColorHighlights(this.backGroundColor);

        this.effectiveY = y;
        if (statAbove != null) {
            this.effectiveY += statAbove.getEffectiveY() + statAbove.getStatHeight();
        }
    }

    public WidgetAnimatedStat(Screen gui, int backgroundColor) {
        this(gui, TextComponent.EMPTY, 0, 0, backgroundColor, null, false);
    }

    public WidgetAnimatedStat(Screen gui, int backgroundColor, ItemStack icon) {
        this(gui, backgroundColor);
        statIcon = StatIcon.of(icon);
    }

    public WidgetAnimatedStat(Screen gui, int backgroundColor, ResourceLocation texture) {
        this(gui, backgroundColor);
        statIcon = StatIcon.of(texture);
    }

    public WidgetAnimatedStat(Screen gui, Component title, StatIcon icon, int xPos, int yPos, int backGroundColor,
                              IGuiAnimatedStat statAbove, boolean leftSided) {
        this(gui, title, xPos, yPos, backGroundColor, statAbove, leftSided);
        statIcon = icon;
    }

    public WidgetAnimatedStat(Screen gui, Component title, StatIcon icon, int backGroundColor,
                              IGuiAnimatedStat statAbove, ArmorHUDLayout.LayoutItem layout) {
        this(gui, title, 0, 0, backGroundColor, statAbove, layout.isLeftSided());
        Window mw = Minecraft.getInstance().getWindow();
        int x = layout.getX() == -1 ? mw.getGuiScaledWidth() - 2 : (int) (mw.getGuiScaledWidth() * layout.getX());
        setBaseX(x);
        setBaseY((int) (mw.getGuiScaledHeight() * layout.getY()));
        statIcon = icon;
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        needTextRecalc = true;
    }

    @Override
    public void setParentStat(IGuiAnimatedStat stat) {
        statAbove = stat;
    }

    public void addSubWidget(AbstractWidget widget) {
        subWidgets.add(widget);
    }

    public void removeSubWidget(AbstractWidget widget) {
        subWidgets.remove(widget);
    }

    public void setSubwidgetRenderOffsets(int left, int right) {
        widgetOffsetLeft = left;
        widgetOffsetRight = right;
    }

    @Override
    public boolean isLeftSided() {
        return leftSided;
    }

    @Override
    public void setLeftSided(boolean leftSided) {
        this.leftSided = leftSided;
    }

    @Override
    public void setAutoLineWrap(boolean wrap) {
        autoLineWrap = wrap;
    }

    @Override
    public Component getTitle() {
        return getMessage();
    }

    @Override
    public void setTitle(Component title) {
        setMessage(title);
    }

    @Override
    public IGuiAnimatedStat setText(List<Component> text) {
        textComponents.clear();
        textComponents.addAll(text);
        needTextRecalc = true;
        return this;
    }

    @Override
    public IGuiAnimatedStat setText(Component text) {
        textComponents.clear();
        textComponents.add(text);
        needTextRecalc = true;
        return this;
    }

    @Override
    public void appendText(List<Component> text) {
        textComponents.addAll(text);
        needTextRecalc = true;
    }

    @Override
    public void setBackgroundColor(int backgroundColor) {
        if (backgroundColor != this.backGroundColor) {
            this.backGroundColor = backgroundColor;
            calculateColorHighlights(backgroundColor);
        }
    }

    @Override
    public void setForegroundColor(int foregroundColor) {
        this.foregroundColor = foregroundColor;
    }

    @Override
    public void setTitleColor(int titleColor) {
        this.titleColor = titleColor;
    }

    @Override
    public int getBackgroundColor() {
        return backGroundColor;
    }

    private void calculateColorHighlights(int color) {
        if (ConfigHelper.client().general.guiBevel.get()) {
            float fgR = (float) (color >> 16 & 255) / 255.0F;
            float fgG = (float) (color >> 8 & 255) / 255.0F;
            float fgB = (float) (color & 255) / 255.0F;
            float fgA = (float) (color >> 24 & 255) / 255.0F;
            TintColor c = new TintColor(fgR, fgG, fgB, fgA);
            if (bevel) {
                bgColorHi = c.brighter();
                bgColorLo = c.darker();
            } else {
                bgColorHi = c.darker().darker();
                bgColorLo = bgColorHi;
            }
        } else {
            bgColorLo = bgColorHi = TintColor.BLACK;
        }
    }

    @Override
    public void setBeveled(boolean bevel) {
        this.bevel = bevel;
        calculateColorHighlights(backGroundColor);
    }

    private int getVisibleLines() {
        return MAX_VISIBLE_LINES - reservedLines;
    }

    @Override
    public void setReservedLines(int reservedLines) {
        this.reservedLines = Mth.clamp(reservedLines, 0, MAX_VISIBLE_LINES - 1);
        needTextRecalc = true;
    }

    private void recalcText() {
        reorderingProcessors.clear();
        dropShadows.clear();

        Font font = Minecraft.getInstance().font;
        int titleWidth = font.width(getMessage());
        if (autoLineWrap) {
            int availableWidth = calculateAvailableWidth();
            reorderingProcessors.addAll(GuiUtils.wrapTextComponentList(textComponents, availableWidth, font));
            expandedWidth = Math.min(availableWidth, Math.max(titleWidth, minExpandedWidth));
        } else {
            expandedWidth = titleWidth;
            textComponents.forEach(c -> reorderingProcessors.add(c.getVisualOrderText()));
        }
        reorderingProcessors.forEach(processedLine -> {
            expandedWidth = Math.max(expandedWidth, font.width(processedLine));
            dropShadows.add(needsDropShadow(processedLine));
        });
        expandedWidth += SCROLLBAR_MARGIN_WIDTH;
        int topMargin = reorderingProcessors.isEmpty() ? font.lineHeight : TOP_MARGIN_HEIGHT;
        expandedHeight = Math.max(minExpandedHeight, topMargin + Math.min(MAX_VISIBLE_LINES, reorderingProcessors.size() + reservedLines) * font.lineHeight) + 3;

        addOrRemoveScrollbar();

        needTextRecalc = false;
    }

    /**
     * Calculate the maximum available width that a line of wrapped text can be.  Note that the actual widget might
     * not necessarily end up this wide, depending on the text contents.
     *
     * @return the maximum available width for the animated stat, not including the gutter width for the scrollbar/icon
     */
    private int calculateAvailableWidth() {
        int availableWidth;
        if (gui instanceof AbstractContainerScreen<?> screen) {
            availableWidth = Math.min(Math.max(minExpandedWidth, screen.getXSize()), leftSided ? screen.getGuiLeft() : screen.width - (screen.getGuiLeft() + screen.getXSize()));
        } else if (gui instanceof AbstractPneumaticCraftScreen screen) {
            availableWidth = Math.min(Math.max(minExpandedWidth, screen.xSize), leftSided ? screen.guiLeft : screen.xSize - (screen.guiLeft + screen.xSize));
        } else {
            availableWidth = leftSided ? x : Minecraft.getInstance().getWindow().getGuiScaledWidth() - x;
        }
        return availableWidth - 5 - SCROLLBAR_MARGIN_WIDTH;  // leave at least 5 pixel margin from edge of screen
    }

    private boolean needsDropShadow(FormattedCharSequence line) {
        StyleChecker styleChecker = new StyleChecker(foregroundColor);
        line.accept(styleChecker);
        return styleChecker.isLightColor();
    }

    private void addOrRemoveScrollbar() {
        if (reorderingProcessors.size() > getVisibleLines()) {
            if (subWidgets.contains(scrollBar)) return;
            // need to add a scrollbar
            curScroll = 0;
            int scrollbarHeight = getVisibleLines() * lineSpacing - TOP_MARGIN_HEIGHT;
            int yOffset = reservedLines > 0 ? reservedLines * Minecraft.getInstance().font.lineHeight : 0;
            addSubWidget(scrollBar = new WidgetVerticalScrollbar(leftSided ? -16 : 2, TOP_MARGIN_HEIGHT + yOffset, scrollbarHeight)
                    .setStates(reorderingProcessors.size() - getVisibleLines())
                    .setListening(true));
        } else if (subWidgets.removeIf(w -> w == scrollBar)) {
            // removing existing scrollbar
            curScroll = 0;
            scrollBar = null;
        }
    }

    @Override
    public void setMinimumContractedDimensions(int minWidth, int minHeight) {
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        width = minWidth;
        height = minHeight;
    }

    @Override
    public void setMinimumExpandedDimensions(int minWidth, int minHeight) {
        if (minExpandedWidth != minWidth) needTextRecalc = true;
        minExpandedWidth = minWidth;
        minExpandedHeight = minHeight;
    }

    @Override
    public void tickWidget() {
        if (needTextRecalc) recalcText();

        prevX = x;
        prevEffectiveY = effectiveY;
        prevWidth = width;
        prevHeight = height;

        doneExpanding = true;
        // 4 ticks to fully expand/contract
        int expandX = expandedWidth / 4;
        int expandY = expandedHeight / 4;

        if (isClicked) {
            // expand the box
            width = Math.min(expandedWidth, width + expandX);
            height = Math.min(expandedHeight, height + expandY);
            doneExpanding = width == expandedWidth && height == expandedHeight;

            int scaledWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int scaledHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            if (isLeftSided()) {
                if (x >= scaledWidth) x = scaledWidth;
            } else {
                if (x < 0) x = 1;
            }
            if (y + height >= scaledHeight) {
                y = scaledHeight - height - 1;
            }

            if (doneExpanding && scrollBar != null) curScroll = scrollBar.getState();
        } else {
            // contract the box
            width = Math.max(minWidth, width - expandX);
            height = Math.max(minHeight, height - expandY);
            doneExpanding = false;
        }

        effectiveY = y;
        if (statAbove != null) {
            effectiveY += statAbove.getEffectiveY() + statAbove.getStatHeight();
        }
    }

    @Override
    public void renderStat(PoseStack matrixStack, int x, int y, float partialTicks) {
        // just delegate to the renderButton() method
        // a separately-named interface method is used to avoid AbstractMethodError problems arising
        // from having a renderButton() method in IGuiAnimatedStat
        renderButton(matrixStack, x, y, partialTicks);
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        if (leftSided) {
            return this.active && this.visible
                    && mouseX >= (double)this.x - this.width
                    && mouseX < (double)this.x
                    && mouseY >= (double)this.y
                    && mouseY < (double)(this.y + this.height);
        } else {
            return super.clicked(mouseX, mouseY);
        }
    }

    @Override
    public void renderButton(PoseStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        int baseX = leftSided ? this.x - this.width : this.x;
        this.isHovered = mouseX >= baseX && mouseY >= this.effectiveY && mouseX < baseX + this.width && mouseY < this.effectiveY + this.height;

        float zLevel = 0;
        Font fontRenderer = Minecraft.getInstance().font;
        int renderBaseX = (int) Mth.lerp(partialTicks, prevX, x);
        int renderAffectedY = (int) Mth.lerp(partialTicks, prevEffectiveY, effectiveY);
        int renderWidth = (int) Mth.lerp(partialTicks, prevWidth, width);
        int renderHeight = (int) Mth.lerp(partialTicks, prevHeight, height);

        if (leftSided) renderWidth *= -1;
        GuiComponent.fill(matrixStack, renderBaseX, renderAffectedY, renderBaseX + renderWidth, renderAffectedY + renderHeight, backGroundColor);
        RenderSystem.disableTexture();
        RenderSystem.lineWidth(3.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder wr = Tesselator.getInstance().getBuilder();
        wr.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        float[] c1 = leftSided ? bgColorLo.getComponents(null) : bgColorHi.getComponents(null);
        float[] c2 = bgColorHi.getComponents(null);
        float[] c3 = leftSided ? bgColorHi.getComponents(null) : bgColorLo.getComponents(null);
        float[] c4 = bgColorLo.getComponents(null);
        wr.vertex(renderBaseX, renderAffectedY, zLevel).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
        wr.vertex(renderBaseX + renderWidth, renderAffectedY, zLevel).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
        wr.vertex(renderBaseX + renderWidth, renderAffectedY + renderHeight, zLevel).color(c3[0], c3[1], c3[2],c3[3]).endVertex();
        wr.vertex(renderBaseX, renderAffectedY + renderHeight, zLevel).color(c4[0], c4[1], c4[2], c4[3]).endVertex();
        wr.vertex(renderBaseX, renderAffectedY, zLevel).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
        Tesselator.getInstance().end();
        RenderSystem.enableTexture();
        if (leftSided) renderWidth *= -1;

        // if done expanding, draw the information
        if (doneExpanding) {
            if (needTextRecalc) recalcText();

            String title = getMessage().getString();
            int titleYoffset = title.isEmpty() ? 3 : 12;
            if (!title.isEmpty()) {
                fontRenderer.drawShadow(matrixStack, title, renderBaseX + (leftSided ? -renderWidth + 2 : 18), renderAffectedY + 2, titleColor);
            }
            for (int i = curScroll; i < reorderingProcessors.size() && i < curScroll + getVisibleLines(); i++) {
                FormattedCharSequence line = reorderingProcessors.get(i);
                int renderX = renderBaseX + (leftSided ? -renderWidth + 2 : 18);
                int renderY = renderAffectedY + (i - curScroll) * lineSpacing + titleYoffset + reservedLines * fontRenderer.lineHeight;
                if (dropShadows.get(i)) {
                    fontRenderer.drawShadow(matrixStack, line,renderX, renderY, foregroundColor);
                } else {
                    fontRenderer.draw(matrixStack, line,renderX, renderY, foregroundColor);
                }
            }

            // Set up necessary translations so subwidgets render in the right place
            // While our own matrix stack is passed to widget.render, we also need to modify the model view stack so
            //  widgets which render itemstacks do it in the right place
            PoseStack poseStack = RenderSystem.getModelViewStack();
            poseStack.pushPose();
            poseStack.translate(renderBaseX + (leftSided ? widgetOffsetLeft : widgetOffsetRight), renderAffectedY + (titleYoffset - 10), 0);
            RenderSystem.enableTexture();
            RenderSystem.applyModelViewMatrix();
            subWidgets.forEach(widget -> widget.render(matrixStack, mouseX - renderBaseX, mouseY - renderAffectedY, partialTicks));
            poseStack.popPose();
            RenderSystem.applyModelViewMatrix();
        }
        if (renderHeight > 16 && renderWidth > 16 && statIcon != null) {
            statIcon.render(matrixStack, renderBaseX, renderAffectedY, leftSided);
        }
    }

    @Override
    public void renderStat(PoseStack matrixStack, MultiBufferSource buffer, float partialTicks) {
        // used by the Block Tracker & Entity Tracker armor upgrades
        if (needTextRecalc) recalcText();

        int renderBaseX = (int) Mth.lerp(partialTicks, prevX, x);
        int renderEffectiveY = (int) Mth.lerp(partialTicks, prevEffectiveY, effectiveY);
        int renderWidth = (int) Mth.lerp(partialTicks, prevWidth, width);
        int renderHeight = (int) Mth.lerp(partialTicks, prevHeight, height);

        // quad bg
        int[] cols = RenderUtils.decomposeColor(backGroundColor);
        RenderUtils.renderWithTypeAndFinish(matrixStack, buffer, ModRenderTypes.UNTEXTURED_QUAD_NO_DEPTH, (posMat, builder) -> {
            int rw = leftSided ? -renderWidth : renderWidth;
            builder.vertex(posMat, (float)renderBaseX, (float)renderEffectiveY + renderHeight, 0.0F)
                    .color(cols[1], cols[2], cols[3], cols[0])
                    .uv2(RenderUtils.FULL_BRIGHT)
                    .endVertex();
            builder.vertex(posMat, (float)renderBaseX + rw, (float)renderEffectiveY + renderHeight, 0.0F)
                    .color(cols[1], cols[2], cols[3], cols[0])
                    .uv2(RenderUtils.FULL_BRIGHT)
                    .endVertex();
            builder.vertex(posMat, (float)renderBaseX + rw, (float)renderEffectiveY, 0.0F)
                    .color(cols[1], cols[2], cols[3], cols[0])
                    .uv2(RenderUtils.FULL_BRIGHT)
                    .endVertex();
            builder.vertex(posMat, (float)renderBaseX, (float)renderEffectiveY, 0.0F)
                    .color(cols[1], cols[2], cols[3], cols[0])
                    .uv2(RenderUtils.FULL_BRIGHT)
                    .endVertex();
        });

        // line loops border
        final Matrix3f normal = matrixStack.last().normal();
        RenderUtils.renderWithTypeAndFinish(matrixStack, buffer, ModRenderTypes.getLineLoops(5.0f), (posMat, builder) -> {
            int rw = leftSided ? -renderWidth : renderWidth;
            float[] c1 = leftSided ? bgColorLo.getComponents(null) : bgColorHi.getComponents(null);
            float[] c2 = bgColorHi.getComponents(null);
            float[] c3 = leftSided ? bgColorHi.getComponents(null) : bgColorLo.getComponents(null);
            float[] c4 = bgColorLo.getComponents(null);
            RenderUtils.normalLine(builder, posMat, normal, renderBaseX, renderEffectiveY, 0, renderBaseX + rw, renderEffectiveY,
                    0, c1[0], c1[1], c1[2], c1[3], true);
            RenderUtils.normalLine(builder, posMat, normal, renderBaseX + rw, renderEffectiveY, 0, renderBaseX + rw, renderEffectiveY + height,
                    0, c2[0], c2[1], c2[2], c2[3], true);
            RenderUtils.normalLine(builder, posMat, normal, renderBaseX + rw, renderEffectiveY + height, 0, renderBaseX, renderEffectiveY + height,
                    0, c3[0], c3[1], c3[2], c3[3], true);
            RenderUtils.normalLine(builder, posMat, normal, renderBaseX, renderEffectiveY + height, 0, renderBaseX, renderEffectiveY,
                    0, c4[0], c4[1], c4[2], c4[3], true);
            RenderUtils.normalLine(builder, posMat, normal, renderBaseX, renderEffectiveY, 0, renderBaseX + rw, renderEffectiveY,
                    0, c1[0], c1[1], c1[2], c1[3], true);
        });

        if (doneExpanding) {
            matrixStack.pushPose();
            // text title
            String title = getMessage().getString();
            if (!title.isEmpty()) {
                RenderUtils.renderString3d(new TextComponent(title).withStyle(ChatFormatting.UNDERLINE),
                        renderBaseX + (leftSided ? -renderWidth + 2 : 18), renderEffectiveY + 2,
                        titleColor, matrixStack, buffer, false, true);
            }
            // text lines
            int titleOffsetY = title.isEmpty() ? 3 : 12;
            Font font = Minecraft.getInstance().font;
            for (int i = curScroll; i < textComponents.size() && i < curScroll + getVisibleLines(); i++) {
                int renderX = renderBaseX + (leftSided ? -renderWidth + 2 : 18);
                int renderY = renderEffectiveY + (i - curScroll) * lineSpacing + titleOffsetY + reservedLines * font.lineHeight;
                font.drawInBatch(reorderingProcessors.get(i), renderX, renderY, foregroundColor, dropShadows.get(i),
                        matrixStack.last().pose(), buffer, true, 0, RenderUtils.FULL_BRIGHT);
            }

            matrixStack.pushPose();
            matrixStack.translate(renderBaseX + (leftSided ? widgetOffsetLeft : widgetOffsetRight), renderEffectiveY + (titleOffsetY - 10), 0);
            subWidgets.stream()
                    .filter(widget -> widget instanceof ICanRender3d)
                    .forEach(widget -> ((ICanRender3d) widget).render3d(matrixStack, buffer, partialTicks));
            matrixStack.popPose();

            matrixStack.popPose();
        }

        // no subwidget drawing in 3d rendering

        if (renderHeight > 16 && renderWidth > 16 && statIcon != null) {
            statIcon.render3d(matrixStack, buffer, renderBaseX, renderEffectiveY);
        }
    }

    private void toggle() {
        isClicked = !isClicked;
        if (isClicked && gui instanceof AbstractPneumaticCraftContainerScreen) {
            // close any other open stat on the same side of the gui
            List<IGuiAnimatedStat> otherStats = ((AbstractPneumaticCraftContainerScreen<?,?>) gui).getStatWidgets();
            otherStats.stream()
                    .filter(stat -> this != stat && stat.isLeftSided() == isLeftSided())
                    .forEach(IGuiAnimatedStat::closeStat);
            // focus on the first textfield child of this widget, if any
            subWidgets.stream()
                    .filter(w -> w instanceof EditBox)
                    .findFirst()
                    .ifPresent(w -> ((EditBox) w).setFocus(true));
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.active && this.visible && getBounds().contains((int)mouseX, (int)mouseY);
    }

    /*
     * button: 0 = left 1 = right 2 = middle
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHoveredOrFocused()) {
            for (AbstractWidget widget : subWidgets) {
                if (widget.mouseClicked(mouseX - this.x, mouseY - this.effectiveY, button)) {
                    return true;
                }
            }
            // no sub-widgets took the click; toggle this animated stat open/closed
            toggle();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isHoveredOrFocused()) {
            for (AbstractWidget widget : subWidgets) {
                if (widget.mouseReleased(mouseX - this.x, mouseY - this.effectiveY, button)) {
                    return true;
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isHoveredOrFocused()) {
            Rect2i bounds = getBounds();
            for (AbstractWidget widget : subWidgets) {
                if (widget.mouseDragged(mouseX - bounds.getX(), mouseY - bounds.getY(), button, dragX, dragY)) {
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dir) {
        Rect2i bounds = getBounds();
        for (AbstractWidget widget : subWidgets) {
            if (widget.isFocused() && widget.mouseScrolled(x - bounds.getX(), y - bounds.getY(), dir)) {
                return true;
            }
        }
        return scrollBar != null && scrollBar.mouseScrolled(x, y, dir);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (AbstractWidget widget : subWidgets) {
            if (widget.keyPressed(keyCode, scanCode, modifiers) || (widget instanceof EditBox && widget.isFocused()) && keyCode != GLFW.GLFW_KEY_ESCAPE) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (AbstractWidget widget : subWidgets) {
            if (widget.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void closeStat() {
        isClicked = false;
    }

    @Override
    public void openStat() {
        isClicked = true;
    }

    @Override
    public boolean isStatOpen() {
        return isClicked;
    }

    @Override
    public int getEffectiveY() {
        return effectiveY;
    }

    @Override
    public int getBaseX() {
        return x;
    }

    @Override
    public int getBaseY() {
        return y;
    }

    @Override
    public int getStatHeight() {
        return getHeight();  // nice bit of mapping there...
    }

    @Override
    public int getStatWidth() {
        return getWidth();
    }

    @Override
    public void setBaseY(int y) {
        this.y = y;
    }

    @Override
    public void setBaseX(int x) {
        this.x = x;
    }

    @Override
    public boolean isDoneExpanding() {
        return doneExpanding;
    }

    @Override
    public Rect2i getBounds() {
        return new Rect2i(x - (leftSided ? width : 0), effectiveY, width, height);
    }

    @Override
    public void addTooltip(double mouseX, double mouseY, List<Component> curTooltip, boolean shiftPressed) {
        if (mouseIsHoveringOverIcon(mouseX, mouseY)) {
            curTooltip.add(getMessage());
            curTooltip.addAll(getExtraTooltipText());
        }

        for (AbstractWidget widget : subWidgets)
            if (widget.isHoveredOrFocused() && widget instanceof ITooltipProvider) {
                ((ITooltipProvider) widget).addTooltip(mouseX, mouseY, curTooltip, shiftPressed);
            }
    }

    public void setExtraTooltipText(List<Component> extraTooltipText) {
        this.extraTooltipText = extraTooltipText;
    }

    private List<Component> getExtraTooltipText() {
        return extraTooltipText;
    }

    private boolean mouseIsHoveringOverIcon(double x, double y) {
        if (leftSided) {
            return x <= this.x && x >= this.x - 16 && y >= effectiveY && y <= effectiveY + 16;
        } else {
            return x >= this.x && x <= this.x + 16 && y >= effectiveY && y <= effectiveY + 16;
        }
    }

    public void setLineSpacing(int lineSpacing) {
        this.lineSpacing = lineSpacing;
    }

    @Override
    public void setTexture(ResourceLocation texture) {
        this.statIcon = StatIcon.of(texture);
    }

    @Override
    public void setTexture(ItemStack itemStack) {
        this.statIcon = StatIcon.of(itemStack);
    }

    @Override
    public void updateNarration(NarrationElementOutput pNarrationElementOutput) {
    }

    public static class StatIcon {
        public static final StatIcon NONE = StatIcon.of(ItemStack.EMPTY);

        private final Either<ItemStack,ResourceLocation> texture;

        private StatIcon(Either<ItemStack,ResourceLocation> texture) {
            this.texture = texture;
        }

        public static StatIcon of(ItemStack stack) {
            return new StatIcon(Either.left(stack));
        }

        public static StatIcon of(ItemLike item) {
            return new StatIcon(Either.left(new ItemStack(item, 1)));
        }

        public static StatIcon of(ResourceLocation texture) {
            return new StatIcon(Either.right(texture));
        }

        void render(PoseStack matrixStack, int x, int y, boolean leftSided) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            texture.ifLeft(stack -> Minecraft.getInstance().getItemRenderer().renderGuiItem(stack, x - (leftSided ? 16 : 0), y))
                    .ifRight(resLoc -> GuiUtils.drawTexture(matrixStack, resLoc, x - (leftSided ? 16 : 0), y));
            RenderSystem.disableBlend();
        }

        public void render3d(PoseStack matrixStack, MultiBufferSource buffer, int x, int y) {
            texture.ifLeft(stack -> {
                ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
                BakedModel bakedModel = itemRenderer.getModel(stack, ClientUtils.getClientLevel(), null, 0);
                itemRenderer.render(stack, ItemTransforms.TransformType.FIXED, true, matrixStack, buffer, RenderUtils.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, bakedModel);
            }).ifRight(resLoc ->
                    RenderUtils.renderWithTypeAndFinish(matrixStack, buffer, ModRenderTypes.getTextureRenderColored(resLoc),
                            (posMat, builder) -> RenderUtils.drawTexture(matrixStack, builder, x, y, RenderUtils.FULL_BRIGHT)));
        }
    }

    private static class StyleChecker implements FormattedCharSink {
        private static final int THRESHOLD = 129;

        Style style = Style.EMPTY;
        private final int defColor;

        private StyleChecker(int defColor) {
            this.defColor = defColor;
        }

        @Override
        public boolean accept(int p_accept_1_, Style p_accept_2_, int p_accept_3_) {
            if (style == Style.EMPTY) style = p_accept_2_;
            return true;
        }

        public boolean isLightColor() {
            int c = style == null || style.isEmpty() || style.getColor() == null ? defColor : style.getColor().getValue();
            return isLightColor(new TintColor(c));
        }

        private boolean isLightColor(TintColor bg) {
            // calculate a foreground color which suitably contrasts with the given background color
            int luminance = (int) Math.sqrt(
                    bg.getRed() * bg.getRed() * 0.241 +
                            bg.getGreen() * bg.getGreen() * 0.691 +
                            bg.getBlue() * bg.getBlue() * 0.068
            );
            return luminance > THRESHOLD;
        }
    }
}
