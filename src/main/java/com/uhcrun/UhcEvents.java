package com.uhcrun;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UhcEvents {

    private record Broken(ResourceKey<Level> dim, BlockPos pos, long time) {}

    /** Bloques de mineral rotos en los últimos ticks (para cocinar sus drops). */
    private final List<Broken> recent = new ArrayList<>();

    private static boolean mechanicsActive() {
        return UhcSettings.get("solo_en_partida") == 0 || UhcGame.running;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) UhcGame.tick();
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        UhcCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) UhcGame.onLogin(sp);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) UhcGame.onDeath(event, sp);
    }

    // ---------------------------------------------------------------- minerales, manzanas, pedernal

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getPlayer().isCreative() || !mechanicsActive()) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        long now = level.getGameTime();

        if (UhcSettings.get("cutclean") == 1 && state.is(UhcRun.CUT_ORES)) {
            recent.removeIf(b -> Math.abs(now - b.time()) > 2);
            recent.add(new Broken(level.dimension(), pos.immutable(), now));
        }

        if (state.is(BlockTags.LEAVES)) {
            int chance = UhcSettings.get("prob_manzana");
            if (chance > 0 && level.random.nextInt(100) < chance) {
                Block.popResource(level, pos, new ItemStack(Items.APPLE));
            }
        }

        if (state.is(Blocks.GRAVEL) && UhcSettings.get("pedernal") == 1) {
            Block.popResource(level, pos, new ItemStack(Items.FLINT));
        }
    }

    @SubscribeEvent
    public void onItemJoin(EntityJoinLevelEvent event) {
        if (recent.isEmpty()) return;
        if (!(event.getEntity() instanceof ItemEntity item)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (UhcSettings.get("cutclean") == 0 || !mechanicsActive()) return;

        long now = level.getGameTime();
        for (Broken b : recent) {
            if (b.dim() != level.dimension() || Math.abs(now - b.time()) > 1) continue;
            if (item.position().distanceToSqr(Vec3.atCenterOf(b.pos())) > 4.0) continue;
            ItemStack cooked = smelt(level, item.getItem());
            if (!cooked.isEmpty()) item.setItem(cooked);
            return;
        }
    }

    // ---------------------------------------------------------------- comida de mobs ya cocinada

    @SubscribeEvent
    public void onMobDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (UhcSettings.get("cutclean") == 0 || !mechanicsActive()) return;

        for (ItemEntity drop : event.getDrops()) {
            ItemStack cooked = smelt(level, drop.getItem());
            if (!cooked.isEmpty() && cooked.has(DataComponents.FOOD)) {
                drop.setItem(cooked);
            }
        }
    }

    /** Devuelve el resultado de fundir el stack en un horno, o ItemStack.EMPTY si no se funde. */
    private static ItemStack smelt(ServerLevel level, ItemStack in) {
        if (in.isEmpty()) return ItemStack.EMPTY;
        Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(in), level);
        if (recipe.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = recipe.get().value().getResultItem(level.registryAccess());
        if (result.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = result.copy();
        out.setCount(Math.min(out.getMaxStackSize(), result.getCount() * in.getCount()));
        return out;
    }
}
