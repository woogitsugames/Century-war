package cwr8;

import com.centurywar.game.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Próba zachowania sim po R8 (tryb JVM): wzorce, zrzuty v16..BattleSnapshot.VERSION, powtórki. */
public final class R8Smoke {
    static int fails = 0;

    static String sha(byte[] b) throws Exception {
        StringBuilder s = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) s.append(String.format("%02x", x & 255));
        return s.toString();
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "OK   " : "FAIL ") + what);
        if (!ok) fails++;
    }

    static Battle battle(BattleConfig cfg, int steps) {
        Battle b = new Battle(cfg, false, false);
        b.setPlayerAi(new Ai(b, 0, null));
        for (int i = 0; i < steps && !b.getOver(); i++) b.step();
        return b;
    }

    public static void main(String[] args) throws Exception {
        Path golden = Paths.get(args[0]);
        Path dir = Paths.get(args[1]);
        String mode = args[2]; // write | read
        Files.createDirectories(dir);
        List<GoldenFixtures.Scenario> scenarios = GoldenFixtures.INSTANCE.getScenarios();
        // 1. Złote powtórki: replay encode/verify/playback i SHA zrzutu jak w GoldenReplayTest.
        for (GoldenFixtures.Scenario s : scenarios) {
            String expected = new String(Files.readAllBytes(golden.resolve(s.getId() + ".json")), "UTF-8");
            check(expected.equals(GoldenFixtures.INSTANCE.render(s)), "wzorzec " + s.getId());
        }
        // 2. Zrzuty v16..VERSION (dziś v20): bajty z wersji niezminifikowanej czytane po R8;
        //    bieżący format musi się dodatkowo zapisać identycznie i dalej symulować tak samo.
        final int cur = BattleSnapshot.VERSION;
        int idx = 0;
        for (GoldenFixtures.Scenario s : scenarios) {
            if (idx++ % 3 != 0) continue;
            BattleConfig cfg = s.getConfig().invoke();
            Battle a = battle(cfg, 60 * 45);
            for (int v = 16; v <= cur; v++) {
                long id = 1000L * v + idx;
                byte[] bytes = BattleSnapshot.INSTANCE.encode$sim(a, true, 12.5f, id, v);
                Path f = dir.resolve(s.getId() + "-v" + v + ".snap");
                if (mode.equals("write")) Files.write(f, bytes);
                byte[] stored = Files.readAllBytes(f);
                check(Arrays.equals(stored, bytes), "zrzut v" + v + " " + s.getId() + " identyczne bajty (" + sha(bytes).substring(0, 12) + ")");
                RestoredBattle r = BattleSnapshot.INSTANCE.decode(stored);
                check(r != null, "zrzut v" + v + " " + s.getId() + " wczytany");
                if (r == null) continue;
                check(r.getBattleId() == (v >= 17 ? id : 0L) && r.getRankedDaily() && r.getCamX() == 12.5f,
                        "zrzut v" + v + " " + s.getId() + " battleId=" + r.getBattleId() + ", ranked, camX");
                if (v == cur) {
                    check(Arrays.equals(BattleSnapshot.INSTANCE.encode(r.getBattle(), true, 12.5f, id), stored),
                            "zrzut v" + v + " " + s.getId() + " ponowny zapis identyczny");
                    Battle copy = BattleSnapshot.INSTANCE.decode(stored).getBattle();
                    for (int i = 0; i < 600; i++) { a.step(); copy.step(); }
                    check(Arrays.equals(BattleSnapshot.INSTANCE.encode(a, false, 0f, 0L),
                            BattleSnapshot.INSTANCE.encode(copy, false, 0f, 0L)),
                            "zrzut v" + v + " " + s.getId() + " dalsza symulacja zgodna");
                }
            }
        }
        // 3. Rekord powtórki (ReplayRecord) i kodek komend Replay.
        GoldenFixtures.Scenario s0 = scenarios.get(0);
        BattleConfig cfg = s0.getConfig().invoke();
        // Dziennik komend z zapisanego wzorca (prawdziwe komendy gracza).
        String json = new String(Files.readAllBytes(golden.resolve(s0.getId() + ".json")), "UTF-8");
        int at = json.indexOf("\"replay\": \"") + 11;
        Replay.Log log = Replay.INSTANCE.decode(json.substring(at, json.indexOf('"', at)));
        ReplayPlayer player = new ReplayPlayer(cfg, log);
        player.advance(log.getSteps() + 1, false);
        check(player.getFinished() && !player.getBroken(), "odtworzenie powtórki wzorca do końca");
        Battle b = player.getBattle();
        ReplayRecord.Meta meta = new ReplayRecord.Meta(cfg.getMode(), cfg.getStageIndex(), b.getWinner(), b.score(),
                b.getTime(), b.getWave(), 1_700_000_000_000L, "Tester");
        byte[] rec = ReplayRecord.INSTANCE.encode(cfg, log, meta);
        Path rf = dir.resolve("record.cwr");
        if (mode.equals("write")) Files.write(rf, rec);
        byte[] storedRec = Files.readAllBytes(rf);
        check(Arrays.equals(storedRec, rec), "rekord powtórki identyczne bajty (" + sha(rec).substring(0, 12) + ")");
        ReplayRecord.Record r = ReplayRecord.INSTANCE.decode(storedRec);
        check(r.getPlayable() && log.equals(r.getLog()) && r.getMeta().equals(meta)
                && Arrays.equals(ReplayRecord.INSTANCE.encode(r.getCfg(), r.getLog(), r.getMeta()), storedRec),
                "rekord powtórki odczyt: log/meta równe, playable, ponowny zapis identyczny");
        check(Replay.INSTANCE.decode(Replay.INSTANCE.encode(log)).equals(log),
                "Replay.encode/decode (komend: " + log.getCommands().size() + ")");
        System.out.println(fails == 0 ? "WYNIK: OK" : "WYNIK: " + fails + " BLEDOW");
        if (fails != 0) System.exit(1);
    }
}
