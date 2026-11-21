package com.example.donutprofit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.text.Text;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

public class Tracker {
    public static class Data {
        public String day = "";
        public double startBalance = -1;
        public double gambleProfit = 0;
        public int wins = 0, losses = 0;
        public boolean hud = true;
        // Regex con grupo "amount" (ej: 1,500 o 2.5M). Ajusta con /dprofit debug + chat real.
        public String winRegex = "(?i)you won \\$?(?<amount>[\\d.,]+[kmb]?)";
        public String lossRegex = "(?i)you lost \\$?(?<amount>[\\d.,]+[kmb]?)";
        public Map<String, Double> history = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("donutprofit.json");
    public static Data d = new Data();
    public static double balance = -1;
    private static Pattern winP, lossP;
    private static int tick = 0;

    public static void load() {
        try {
            if (Files.exists(FILE)) d = GSON.fromJson(Files.readString(FILE), Data.class);
        } catch (Exception e) { d = new Data(); }
        if (d.history == null) d.history = new LinkedHashMap<>();
        compile();
        rollover();
    }

    public static void save() {
        try { Files.writeString(FILE, GSON.toJson(d)); } catch (Exception ignored) {}
    }

    private static void compile() {
        try { winP = Pattern.compile(d.winRegex); } catch (Exception e) { winP = null; }
        try { lossP = Pattern.compile(d.lossRegex); } catch (Exception e) { lossP = null; }
    }

    public static double balanceChange() {
        return (balance < 0 || d.startBalance < 0) ? 0 : balance - d.startBalance;
    }

    private static void rollover() {
        String today = LocalDate.now().toString();
        if (!today.equals(d.day)) {
            if (!d.day.isEmpty()) d.history.put(d.day, d.gambleProfit);
            d.day = today;
            d.gambleProfit = 0; d.wins = 0; d.losses = 0;
            d.startBalance = balance; // se fija al leer el balance
            save();
        }
    }

    public static void reset() {
        d.gambleProfit = 0; d.wins = 0; d.losses = 0; d.startBalance = balance;
        save();
    }

    public static void addWin(double amt) { d.gambleProfit += amt; d.wins++; save(); }
    public static void addLoss(double amt) { d.gambleProfit -= amt; d.losses++; save(); }

    public static void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        if (++tick % 20 != 0) return;
        for (String line : sidebarLines(mc)) {
            String l = line.toLowerCase();
            if (l.contains("money") || l.contains("balance") || l.contains("dinero") || l.contains("coins")) {
                double v = parseMoney(line);
                if (v >= 0) {
                    balance = v;
                    if (d.startBalance < 0) { d.startBalance = v; save(); }
                    break;
                }
            }
        }
        rollover();
    }

    public static void onChat(Text msg) {
        String s = msg.getString();
        if (winP != null) {
            Matcher m = winP.matcher(s);
            if (m.find()) { double v = parseMoney(m.group("amount")); if (v >= 0) addWin(v); return; }
        }
        if (lossP != null) {
            Matcher m = lossP.matcher(s);
            if (m.find()) { double v = parseMoney(m.group("amount")); if (v >= 0) addLoss(v); }
        }
    }

    public static List<String> sidebarLines(MinecraftClient mc) {
        List<String> out = new ArrayList<>();
        Scoreboard sb = mc.world.getScoreboard();
        ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (obj == null) return out;
        for (ScoreboardEntry e : sb.getScoreboardEntries(obj)) {
            Team t = sb.getScoreHolderTeam(e.owner());
            out.add(Team.decorateName(t, e.name()).getString());
        }
        return out;
    }

    /** Convierte "$1,250", "2.5M", "10k", "1.2B" a double. -1 si falla. */
    public static double parseMoney(String s) {
        Matcher m = Pattern.compile("([\\d][\\d.,]*)\\s*([kKmMbB])?").matcher(s);
        if (!m.find()) return -1;
        String num = m.group(1);
        String suf = m.group(2);
        // Si hay sufijo, el punto es decimal; si no, comas/puntos son separadores de miles
        if (suf != null) num = num.replace(",", "");
        else num = num.replace(",", "");
        try {
            double v = Double.parseDouble(num);
            if (suf != null) switch (suf.toLowerCase()) {
                case "k" -> v *= 1_000;
                case "m" -> v *= 1_000_000;
                case "b" -> v *= 1_000_000_000;
            }
            return v;
        } catch (Exception e) { return -1; }
    }

    public static String fmt(double v) {
        double a = Math.abs(v);
        String sign = v < 0 ? "-" : "";
        if (a >= 1e9) return sign + String.format("%.2fB", a / 1e9);
        if (a >= 1e6) return sign + String.format("%.2fM", a / 1e6);
        if (a >= 1e3) return sign + String.format("%.1fK", a / 1e3);
        return sign + String.format("%.0f", a);
    }
}
