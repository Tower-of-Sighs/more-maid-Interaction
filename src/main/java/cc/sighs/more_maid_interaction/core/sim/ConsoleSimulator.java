package cc.sighs.more_maid_interaction.core.sim;

import cc.sighs.more_maid_interaction.core.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class ConsoleSimulator {
    private final InteractionEngine engine;
    private SocialContext ctx;
    private final Random rng = new Random();

    private ConsoleSimulator() {
        Personality p = defaultPersonality();
        Stats init = new Stats(0.2, 0.2, 0.5, 0.3);
        this.engine = new InteractionEngine(p, init);
        this.ctx = SocialContext.empty();
    }

    private ConsoleSimulator(long seed) {
        Personality p = defaultPersonality();
        Stats init = new Stats(0.2, 0.2, 0.5, 0.3);
        this.engine = new InteractionEngine(p, init);
        this.ctx = SocialContext.empty();
        this.rng.setSeed(seed);
    }

    private Personality defaultPersonality() {
        Map<EventTag, Double> w = new EnumMap<>(EventTag.class);
        w.put(EventTag.HUG, 0.3);
        w.put(EventTag.GIFT, 0.15);
        w.put(EventTag.FEED, 0.2);
        w.put(EventTag.PILLOW, 0.25);
        w.put(EventTag.TEASE, -0.5);
        w.put(EventTag.PRAISE, 0.1);
        w.put(EventTag.WORK_HELP, 0.15);
        w.put(EventTag.TALK, 0.05);
        return new Personality(w);
    }

    private static InteractionEvent gift() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.GIFT_VALUE, 0.9)
                .put(StimulusAxis.SOCIAL_EXPOSURE, 0.3)
                .build();
        return InteractionEvent.builder("gift")
                .valence(0.6)
                .intensity(0.7)
                .tag(EventTag.GIFT)
                .stimulus(s)
                .cooldown(20)
                .build();
    }

    private static InteractionEvent hug() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.AFFECTION, 0.85)
                .put(StimulusAxis.INTIMACY, 0.6)
                .put(StimulusAxis.SOCIAL_EXPOSURE, 0.5)
                .build();
        return InteractionEvent.builder("hug")
                .valence(0.7)
                .intensity(0.8)
                .tag(EventTag.HUG)
                .stimulus(s)
                .build();
    }

    private static InteractionEvent feed() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.CARE, 0.7)
                .put(StimulusAxis.WORK_HELPFULNESS, 0.4)
                .build();
        return InteractionEvent.builder("feed")
                .valence(0.5)
                .intensity(0.6)
                .tag(EventTag.FEED)
                .stimulus(s)
                .build();
    }

    private static InteractionEvent talk() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.SOCIAL_EXPOSURE, 0.4)
                .build();
        return InteractionEvent.builder("talk")
                .valence(0.3)
                .intensity(0.5)
                .tag(EventTag.TALK)
                .stimulus(s)
                .build();
    }

    private static InteractionEvent praise() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.SOCIAL_EXPOSURE, 0.3)
                .put(StimulusAxis.PLAYFUL, 0.2)
                .build();
        return InteractionEvent.builder("praise")
                .valence(0.4)
                .intensity(0.6)
                .tag(EventTag.PRAISE)
                .stimulus(s)
                .build();
    }

    private static InteractionEvent workHelp() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.WORK_HELPFULNESS, 0.9)
                .build();
        return InteractionEvent.builder("work_help")
                .valence(0.5)
                .intensity(0.6)
                .tag(EventTag.WORK_HELP)
                .stimulus(s)
                .build();
    }

    private static InteractionEvent tease() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.TEASE_INTENSITY, 0.9)
                .put(StimulusAxis.PLAYFUL, 0.4)
                .build();
        return InteractionEvent.builder("tease")
                .valence(-0.2)
                .intensity(0.7)
                .tag(EventTag.TEASE)
                .stimulus(s)
                .cooldown(10)
                .build();
    }

    private static InteractionEvent teaseStrong() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.TEASE_INTENSITY, 1.0)
                .put(StimulusAxis.SOCIAL_EXPOSURE, 1.0)
                .build();
        return InteractionEvent.builder("tease_strong")
                .valence(-0.8)
                .intensity(1.0)
                .tag(EventTag.TEASE)
                .stimulus(s)
                .build();
    }

    private static InteractionEvent pillow() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.INTIMACY, 0.85)
                .put(StimulusAxis.AFFECTION, 0.75)
                .build();
        return InteractionEvent.builder("pillow")
                .valence(0.65)
                .intensity(0.75)
                .tag(EventTag.PILLOW)
                .stimulus(s)
                .build();
    }

    private static InteractionEvent observeOtherAffection() {
        Stimulus s = Stimulus.builder()
                .put(StimulusAxis.SOCIAL_EXPOSURE, 1.0)
                .put(StimulusAxis.AFFECTION, 0.6)
                .build();
        return InteractionEvent.builder("observe_other_affection")
                .valence(-0.2)
                .intensity(0.5)
                .stimulus(s)
                .build();
    }

    private void run() throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("More Maid Interaction - Console Simulator");
        System.out.println("输入对应编号执行事件，输入 'r <n>' 设置对手数量, 'm <0..1>' 设置其他平均好感, 'q' 退出。");
        while (true) {
            printState();
            printMenu();
            System.out.print("> ");
            String line = br.readLine();
            if (line == null) break;
            line = line.trim().toLowerCase(Locale.ROOT);
            if (line.equals("q") || line.equals("quit") || line.equals("exit")) {
                break;
            }
            if (line.startsWith("r ")) {
                try {
                    int r = Integer.parseInt(line.substring(2).trim());
                    ctx = new SocialContext(r, ctx.lastOtherAffection(), ctx.meanOtherFavor());
                } catch (Exception ignored) {}
                continue;
            }
            if (line.startsWith("m ")) {
                try {
                    double v = Double.parseDouble(line.substring(2).trim());
                    ctx = new SocialContext(ctx.rivals(), ctx.lastOtherAffection(), Stats.clamp(v));
                } catch (Exception ignored) {}
                continue;
            }
            int choice = -1;
            try {
                choice = Integer.parseInt(line);
            } catch (Exception ignored) {}
            InteractionEvent e = null;
            SocialContext sc = ctx;
            switch (choice) {
                case 1 -> e = gift();
                case 2 -> e = hug();
                case 3 -> e = feed();
                case 4 -> e = pillow();
                case 5 -> e = tease();
                case 6 -> e = talk();
                case 7 -> e = praise();
                case 8 -> e = workHelp();
                case 9 -> { // observe other hug
                    e = observeOtherAffection();
                    sc = new SocialContext(ctx.rivals(), 0.9, ctx.meanOtherFavor());
                }
                case 10 -> { // observe other gift
                    e = observeOtherAffection();
                    sc = new SocialContext(ctx.rivals(), 0.7, ctx.meanOtherFavor());
                }
                default -> System.out.println("无效输入");
            }
            if (e != null) {
                InteractionEngine.Result r = engine.apply(e, sc);
                printDelta(r);
            }
        }
        System.out.println("已退出。");
    }

    private void runAuto(int steps) {
        int[] phases = new int[]{1000, 1000, 1000, 1000, steps - 4000};
        double[][] weights = new double[][]{
                new double[]{0.12, 0.18, 0.15, 0.10, 0.05, 0.15, 0.12, 0.13},
                new double[]{0.20, 0.35, 0.10, 0.08, 0.03, 0.12, 0.07, 0.05},
                new double[]{0.10, 0.12, 0.08, 0.06, 0.25, 0.10, 0.08, 0.06},
                new double[]{0.08, 0.12, 0.12, 0.08, 0.02, 0.20, 0.20, 0.18},
                new double[]{0.12, 0.18, 0.15, 0.10, 0.05, 0.15, 0.12, 0.13}
        };
        double[] observeProb = new double[]{0.05, 0.02, 0.30, 0.03, 0.05};
        int moodSwitch = 0;
        MoodModel.Mood lastMood = engine.mood().topMood();
        int annoyedPeak = 0;
        int jealousPeak = 0;
        int coldPeriods = 0;
        int repairs = 0;
        boolean inCold = false;
        int coldLen = 0;
        int phaseIndex = 0;
        int phaseRemain = phases[0];
        while (phaseIndex < phases.length && phaseRemain > 0) {
            if (phaseRemain == 0) {
                phaseIndex++;
                if (phaseIndex >= phases.length) break;
                phaseRemain = phases[phaseIndex];
            }
            int i = phases[phaseIndex] - phaseRemain;
            if (i % 50 == 0) {
                int rivals = rng.nextInt(4);
                double mean = Math.min(1.0, Math.max(0.0, 0.3 + rng.nextGaussian() * 0.1));
                ctx = new SocialContext(rivals, ctx.lastOtherAffection(), mean);
            }
            boolean observe = rng.nextDouble() < observeProb[phaseIndex];
            InteractionEvent e;
            SocialContext sc = ctx;
            if (observe) {
                e = observeOtherAffection();
                double aff = 0.6 + rng.nextDouble() * 0.4;
                sc = new SocialContext(ctx.rivals(), aff, ctx.meanOtherFavor());
            } else {
                e = pickWithWeights(weights[phaseIndex]);
            }
            InteractionEngine.Result r = engine.apply(e, sc);
            MoodModel.Mood m = engine.mood().topMood();
            if (m != lastMood) {
                moodSwitch++;
                lastMood = m;
            }
            double annoyedP = engine.mood().distribution().get(MoodModel.Mood.ANNOYED);
            double jealousP = engine.mood().distribution().get(MoodModel.Mood.JEALOUS);
            if (annoyedP > 0.5) annoyedPeak++;
            if (jealousP > 0.5) jealousPeak++;
            EmotionState es = r.emotion();
            if (!inCold && es.pleasure() < 0.35) {
                inCold = true;
                coldLen = 1;
            } else if (inCold) {
                coldLen++;
                if (es.pleasure() > 0.48) {
                    if (coldLen >= 15) {
                        coldPeriods++;
                        repairs++;
                    }
                    inCold = false;
                    coldLen = 0;
                }
            }
            phaseRemain--;
        }
        Stats s = engine.stats();
        System.out.printf(Locale.ROOT, "自动运行完成: 步数=%d%n", steps);
        System.out.printf(Locale.ROOT, "最终: favor=%.3f bond=%.3f sincerity=%.3f novelty=%.3f%n",
                s.favor(), s.bond(), s.sincerity(), s.novelty());
        System.out.printf(Locale.ROOT, "情绪转折=%d 嫉妒峰值=%d 烦躁峰值=%d 冷淡期=%d 修复=%d%n",
                moodSwitch, jealousPeak, annoyedPeak, coldPeriods, repairs);
        System.out.printf(Locale.ROOT, "最终心境Top=%s 分布=%s%n",
                engine.mood().topMood(), engine.mood().distribution().toString());
    }

    private void runScenario() {
        System.out.println("开始场景测试：初期建立信任 → 中期升温 → 冲突 → 修复");
        Stats s0 = engine.stats();
        System.out.printf(Locale.ROOT, "初始状态: favor=%.3f bond=%.3f sincerity=%.3f novelty=%.3f%n",
                s0.favor(), s0.bond(), s0.sincerity(), s0.novelty());

        for (int i = 0; i < 100; i++) {
            InteractionEvent e = switch (i % 3) {
                case 0 -> feed();
                case 1 -> talk();
                default -> praise();
            };
            engine.apply(e, ctx);
        }
        Stats sAfterTrust = engine.stats();
        EmotionState eAfterTrust = engine.emotion(sAfterTrust);
        System.out.printf(Locale.ROOT, "[建立信任完] favor=%.3f bond=%.3f sincerity=%.3f novelty=%.3f | P=%.3f A=%.3f D=%.3f Top=%s%n",
                sAfterTrust.favor(), sAfterTrust.bond(), sAfterTrust.sincerity(), sAfterTrust.novelty(),
                eAfterTrust.pleasure(), eAfterTrust.arousal(), eAfterTrust.dominance(), engine.mood().topMood());

        for (int i = 0; i < 200; i++) {
            if (i % 25 == 0) {
                engine.apply(gift(), ctx);
            } else if (i % 2 == 0) {
                engine.apply(hug(), ctx);
            } else {
                engine.apply(pillow(), ctx);
            }
        }
        Stats sAfterWarm = engine.stats();
        EmotionState eAfterWarm = engine.emotion(sAfterWarm);
        System.out.printf(Locale.ROOT, "[升温完] favor=%.3f bond=%.3f sincerity=%.3f novelty=%.3f | P=%.3f A=%.3f D=%.3f Top=%s%n",
                sAfterWarm.favor(), sAfterWarm.bond(), sAfterWarm.sincerity(), sAfterWarm.novelty(),
                eAfterWarm.pleasure(), eAfterWarm.arousal(), eAfterWarm.dominance(), engine.mood().topMood());

        double sincerityBeforeSpam = engine.stats().sincerity();
        for (int i = 0; i < 5; i++) engine.apply(tease(), ctx);
        double sincerityAfterSpam = engine.stats().sincerity();
        boolean spamDrop = sincerityAfterSpam < sincerityBeforeSpam - 0.05;

        SocialContext jealousCtx = new SocialContext(4, 1.0, 1.0);
        for (int i = 0; i < 15; i++) {
            engine.apply(teaseStrong(), ctx);
        }
        for (int i = 0; i < 5; i++) {
            engine.apply(observeOtherAffection(), jealousCtx);
        }
        double jealousProb = engine.mood().distribution().get(MoodModel.Mood.JEALOUS);
        boolean jealousTriggered = jealousProb > 0.5;
        Stats sAfterConflict = engine.stats();
        EmotionState eAfterConflict = engine.emotion(sAfterConflict);
        double annoyedAfterConflict = engine.mood().distribution().get(MoodModel.Mood.ANNOYED);
        System.out.printf(Locale.ROOT, "[冲突后] favor=%.3f bond=%.3f sincerity=%.3f novelty=%.3f | JEALOUS=%.3f ANNOYED=%.3f%n",
                sAfterConflict.favor(), sAfterConflict.bond(), sAfterConflict.sincerity(), sAfterConflict.novelty(),
                jealousProb, annoyedAfterConflict);

        double pleasureBeforeRepair = eAfterConflict.pleasure();
        for (int i = 0; i < 150; i++) {
            InteractionEvent e = switch (i % 3) {
                case 0 -> feed();
                case 1 -> workHelp();
                default -> hug();
            };
            engine.apply(e, ctx);
        }
        Stats sFinal = engine.stats();
        EmotionState eFinal = engine.emotion(sFinal);
        double annoyedAfterRepair = engine.mood().distribution().get(MoodModel.Mood.ANNOYED);
        boolean pleasureRecovered = eFinal.pleasure() > pleasureBeforeRepair + 0.05;
        boolean annoyedFaded = (annoyedAfterRepair < annoyedAfterConflict - 0.01) || (annoyedAfterRepair < 0.05);
        boolean finalStable = engine.mood().topMood() == MoodModel.Mood.CALM
                || engine.mood().topMood() == MoodModel.Mood.AFFECTIONATE;

        System.out.printf(Locale.ROOT, "[修复后] favor=%.3f bond=%.3f sincerity=%.3f novelty=%.3f | P=%.3f A=%.3f D=%.3f Top=%s%n",
                sFinal.favor(), sFinal.bond(), sFinal.sincerity(), sFinal.novelty(),
                eFinal.pleasure(), eFinal.arousal(), eFinal.dominance(), engine.mood().topMood());
        System.out.printf(Locale.ROOT, "观测: 刷屏后真诚下降=%s  嫉妒触发=%s  Pleasure回升=%s  ANNOYED消退=%s  最终稳定=%s%n",
                spamDrop ? "是" : "否", jealousTriggered ? "是" : "否", pleasureRecovered ? "是" : "否",
                annoyedFaded ? "是" : "否", finalStable ? "是" : "否");
    }

    private InteractionEvent pickWithWeights(double[] w) {
        double p = rng.nextDouble();
        double s = 0;
        for (double x : w) s += x;
        double r = p * s;
        double c = 0;
        for (int i = 0; i < w.length; i++) {
            c += w[i];
            if (r <= c) {
                if (i == 0) return gift();
                if (i == 1) return hug();
                if (i == 2) return feed();
                if (i == 3) return pillow();
                if (i == 4) return tease();
                if (i == 5) return talk();
                if (i == 6) return praise();
                return workHelp();
            }
        }
        return hug();
    }

    private void printMenu() {
        System.out.println("[1] 送礼  [2] 拥抱  [3] 投喂  [4] 膝枕  [5] 调戏  [6] 交谈  [7] 夸赞  [8] 帮忙");
        System.out.println("[9] 他人-拥抱(观察)  [10] 他人-送礼(观察)");
    }

    private void printState() {
        Stats s = engine.stats();
        EmotionState e = engine.emotion(s);
        MoodModel m = engine.mood();
        System.out.printf(Locale.ROOT, "步数=%d | favor=%.3f bond=%.3f sincerity=%.3f novelty=%.3f%n",
                engine.ticks(), s.favor(), s.bond(), s.sincerity(), s.novelty());
        System.out.printf(Locale.ROOT, "情绪(PAD): P=%.3f A=%.3f D=%.3f | Top=%s%n",
                e.pleasure(), e.arousal(), e.dominance(), m.topMood());
        System.out.printf(Locale.ROOT, "社交: 对手=%d 其他平均好感=%.2f 最近他人亲密=%.2f%n",
                ctx.rivals(), ctx.meanOtherFavor(), ctx.lastOtherAffection());
    }

    private void printDelta(InteractionEngine.Result r) {
        Stats b = r.before();
        Stats a = r.after();
        System.out.printf(Locale.ROOT, "事件: score=%.4f  | favor: %.3f -> %.3f  bond: %.3f -> %.3f  sincerity: %.3f -> %.3f  novelty: %.3f -> %.3f%n",
                r.score(), b.favor(), a.favor(), b.bond(), a.bond(), b.sincerity(), a.sincerity(), b.novelty(), a.novelty());
    }

    public static void main(String[] args) throws Exception {
        if (args != null && args.length >= 1) {
            if ("--auto".equals(args[0])) {
                int steps = 5000;
                var rng = new Random();
                long seed = rng.nextLong(10000, 999999);
                if (args.length >= 2) {
                    try { steps = Integer.parseInt(args[1]); } catch (Exception ignored) {}
                }
                if (args.length >= 3) {
                    try { seed = Long.parseLong(args[2]); } catch (Exception ignored) {}
                }
                ConsoleSimulator sim = new ConsoleSimulator(seed);
                sim.runAuto(steps);
                return;
            }
            if ("--scenario".equals(args[0])) {
                ConsoleSimulator sim = new ConsoleSimulator(22222);
                sim.runScenario();
                return;
            }
        }
        new ConsoleSimulator().run();
    }
}
