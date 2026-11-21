package com.example.donutprofit;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class DonutProfitClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Tracker.load();

        ClientTickEvents.END_CLIENT_TICK.register(Tracker::onTick);
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> { if (!overlay) Tracker.onChat(msg); });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!Tracker.d.hud || mc.player == null || mc.options.hudHidden) return;
            var d = Tracker.d;
            int total = d.wins + d.losses;
            String wr = total == 0 ? "-" : String.format("%.0f%%", 100.0 * d.wins / total);
            int gp = d.gambleProfit >= 0 ? 0xFF55FF55 : 0xFFFF5555;
            double bc = Tracker.balanceChange();
            int bcc = bc >= 0 ? 0xFF55FF55 : 0xFFFF5555;
            int x = 6, y = 6;
            var tr = mc.textRenderer;
            ctx.drawTextWithShadow(tr, "DonutProfit", x, y, 0xFFFFAA00);
            ctx.drawTextWithShadow(tr, "Balance: $" + (Tracker.balance < 0 ? "?" : Tracker.fmt(Tracker.balance)), x, y + 11, 0xFFFFFFFF);
            ctx.drawTextWithShadow(tr, "Cambio hoy: " + (bc >= 0 ? "+" : "") + Tracker.fmt(bc), x, y + 22, bcc);
            ctx.drawTextWithShadow(tr, "Gambling hoy: " + (d.gambleProfit >= 0 ? "+" : "") + Tracker.fmt(d.gambleProfit), x, y + 33, gp);
            ctx.drawTextWithShadow(tr, "W/L: " + d.wins + "/" + d.losses + " (" + wr + ")", x, y + 44, 0xFFFFFFFF);
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, reg) -> {
            var root = literal("dprofit")
                .then(literal("reset").executes(c -> { Tracker.reset(); say(c.getSource(), "Reiniciado."); return 1; }))
                .then(literal("hud").executes(c -> {
                    Tracker.d.hud = !Tracker.d.hud; Tracker.save();
                    say(c.getSource(), "HUD " + (Tracker.d.hud ? "activado" : "desactivado")); return 1; }))
                .then(literal("win").then(argument("amount", DoubleArgumentType.doubleArg(0)).executes(c -> {
                    Tracker.addWin(DoubleArgumentType.getDouble(c, "amount")); say(c.getSource(), "Win añadida."); return 1; })))
                .then(literal("loss").then(argument("amount", DoubleArgumentType.doubleArg(0)).executes(c -> {
                    Tracker.addLoss(DoubleArgumentType.getDouble(c, "amount")); say(c.getSource(), "Loss añadida."); return 1; })))
                .then(literal("history").executes(c -> {
                    if (Tracker.d.history.isEmpty()) say(c.getSource(), "Sin historial aún.");
                    for (Map.Entry<String, Double> e : Tracker.d.history.entrySet())
                        say(c.getSource(), e.getKey() + ": " + Tracker.fmt(e.getValue()));
                    return 1; }))
                .then(literal("debug").executes(c -> {
                    say(c.getSource(), "Líneas del scoreboard:");
                    for (String l : Tracker.sidebarLines(MinecraftClient.getInstance())) say(c.getSource(), " > " + l);
                    say(c.getSource(), "Balance detectado: " + Tracker.balance);
                    return 1; }))
                .executes(c -> {
                    var d = Tracker.d;
                    say(c.getSource(), "Gambling hoy: " + Tracker.fmt(d.gambleProfit) + " | W/L " + d.wins + "/" + d.losses
                        + " | Cambio balance: " + Tracker.fmt(Tracker.balanceChange()));
                    return 1; });
            var node = dispatcher.register(root);
            dispatcher.register(literal("gamble").redirect(node));
        });
    }

    private static void say(FabricClientCommandSource s, String msg) {
        s.sendFeedback(Text.literal("[DonutProfit] " + msg));
    }
}
