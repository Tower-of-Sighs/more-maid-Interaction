package cc.sighs.more_maid_interaction.core.sim;

import cc.sighs.more_maid_interaction.core.EmotionState;
import cc.sighs.more_maid_interaction.core.EventTag;
import cc.sighs.more_maid_interaction.core.InteractionEngine;
import cc.sighs.more_maid_interaction.core.InteractionEvent;
import cc.sighs.more_maid_interaction.core.MoodModel;
import cc.sighs.more_maid_interaction.core.Personality;
import cc.sighs.more_maid_interaction.core.SocialContext;
import cc.sighs.more_maid_interaction.core.Stats;
import cc.sighs.more_maid_interaction.core.Stimulus;
import cc.sighs.more_maid_interaction.core.StimulusAxis;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class FxSimulator extends Application {
    private InteractionEngine engine;
    private SocialContext ctx;
    private Font uiFont;
    private ImageView portraitView;
    private Group overlayGroup;
    private ProgressBar favorBar;
    private ProgressBar bondBar;
    private ProgressBar sinBar;
    private ProgressBar novBar;
    private Label endingLabel;
    private Stage debugStage;
    private Label dbgStep;
    private Label dbgStats;
    private Label dbgMood;
    private Spinner<Integer> dbgRivals;
    private Slider dbgOthersMean;

    @Override
    public void start(Stage stage) {
        Personality p = defaultPersonality();
        Stats init = new Stats(0.2, 0.2, 0.5, 0.3);
        engine = new InteractionEngine(p, init);
        ctx = SocialContext.empty();
        uiFont = loadFont("/assets/font/lxgw3500.ttf", 16);
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        StackPane left = buildLeftPane();
        VBox right = buildRightPane();
        root.setLeft(left);
        root.setCenter(right);
        Scene scene = new Scene(root, 1180, 720);
        stage.setTitle("More Maid Interaction - JavaFX Simulator");
        stage.setScene(scene);
        stage.show();
        refreshStateTexts();
    }

    private StackPane buildLeftPane() {
        Image portrait = loadImage("/assets/pic/Nagato Yuki.png");
        portraitView = new ImageView(portrait);
        portraitView.setPreserveRatio(true);
        portraitView.setFitHeight(640);
        Image dialog = loadImage("/assets/pic/dialog.png");
        ImageView dialogView = new ImageView(dialog);
        dialogView.setFitWidth(540);
        dialogView.setPreserveRatio(true);
        StackPane.setAlignment(dialogView, Pos.BOTTOM_CENTER);
        overlayGroup = new Group();
        StackPane.setAlignment(overlayGroup, Pos.CENTER_LEFT);
        StackPane pane = new StackPane(portraitView, overlayGroup, dialogView);
        pane.setMinWidth(580);
        pane.setPadding(new Insets(10));
        return pane;
    }

    private VBox buildRightPane() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(10, 10, 10, 20));
        box.setPrefWidth(560);
        Label title = new Label("事件");
        title.setFont(loadFont("/assets/font/lxgw3500.ttf", 22));
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        ImageView tabGraphic = new ImageView(loadImage("/assets/pic/event.png"));
        tabGraphic.setFitHeight(22);
        tabGraphic.setPreserveRatio(true);
        Tab tInteract = new Tab();
        tInteract.setGraphic(tabGraphic);
        tInteract.setContent(buildInteractGrid());
        Tab tObserve = new Tab("观察");
        tObserve.setContent(buildObserveGrid());
        tabs.getTabs().addAll(tInteract, tObserve);
        VBox statBars = buildStatBars();
        HBox btns = new HBox(10);
        Button consoleBtn = new Button("控制台");
        consoleBtn.setFont(uiFont);
        consoleBtn.setOnAction(e -> showConsole());
        btns.getChildren().add(consoleBtn);
        btns.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(title, tabs, new Separator(), statBars, btns);
        return box;
    }

    private GridPane buildInteractGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        Button bGift = makeEventButton("送礼", FxSimulator::gift);
        Button bHug = makeEventButton("拥抱", FxSimulator::hug);
        Button bFeed = makeEventButton("投喂", FxSimulator::feed);
        Button bPillow = makeEventButton("膝枕", FxSimulator::pillow);
        Button bTease = makeEventButton("调戏", FxSimulator::tease);
        Button bTalk = makeEventButton("交谈", FxSimulator::talk);
        Button bPraise = makeEventButton("夸赞", FxSimulator::praise);
        Button bWork = makeEventButton("帮忙", FxSimulator::workHelp);
        Button[] arr = new Button[]{bGift, bHug, bFeed, bPillow, bTease, bTalk, bPraise, bWork};
        int i = 0;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 2; c++) {
                if (i >= arr.length) break;
                grid.add(arr[i++], c, r);
            }
        }
        return grid;
    }

    private GridPane buildObserveGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        Button b1 = new Button("他人-拥抱(观察)");
        b1.setFont(uiFont);
        b1.setOnAction(e -> {
            InteractionEvent event = observeOtherAffection();
            SocialContext sc = new SocialContext(ctx.rivals(), 0.9, ctx.meanOtherFavor());
            applyEvent(event, sc);
        });
        Button b2 = new Button("他人-送礼(观察)");
        b2.setFont(uiFont);
        b2.setOnAction(e -> {
            InteractionEvent event = observeOtherAffection();
            SocialContext sc = new SocialContext(ctx.rivals(), 0.7, ctx.meanOtherFavor());
            applyEvent(event, sc);
        });
        grid.add(b1, 0, 0);
        grid.add(b2, 1, 0);
        return grid;
    }

    private VBox buildStatBars() {
        favorBar = new ProgressBar(0);
        bondBar = new ProgressBar(0);
        sinBar = new ProgressBar(0);
        novBar = new ProgressBar(0);
        favorBar.setPrefWidth(310);
        bondBar.setPrefWidth(310);
        sinBar.setPrefWidth(310);
        novBar.setPrefWidth(310);
        favorBar.setStyle("-fx-accent: #ff8ccf;");
        bondBar.setStyle("-fx-accent: #ff9ad6;");
        sinBar.setStyle("-fx-accent: #ffabdE;");
        novBar.setStyle("-fx-accent: #ffb7e4;");
        Label l1 = new Label("好感"); l1.setFont(uiFont);
        Label l2 = new Label("羁绊"); l2.setFont(uiFont);
        Label l3 = new Label("真诚"); l3.setFont(uiFont);
        Label l4 = new Label("新鲜"); l4.setFont(uiFont);
        HBox r1 = new HBox(10, l1, favorBar);
        HBox r2 = new HBox(10, l2, bondBar);
        HBox r3 = new HBox(10, l3, sinBar);
        HBox r4 = new HBox(10, l4, novBar);
        r1.setAlignment(Pos.CENTER_LEFT);
        r2.setAlignment(Pos.CENTER_LEFT);
        r3.setAlignment(Pos.CENTER_LEFT);
        r4.setAlignment(Pos.CENTER_LEFT);
        endingLabel = new Label("结局：进行中");
        endingLabel.setFont(loadFont("/assets/font/lxgw3500.ttf", 18));
        endingLabel.setTextFill(Color.web("#ff6fb4"));
        VBox v = new VBox(8, r1, r2, r3, r4, new Separator(), endingLabel);
        return v;
    }

    private Button makeEventButton(String text, SupplierEvent supplier) {
        Button b = new Button(text);
        b.setFont(uiFont);
        b.setPrefWidth(160);
        b.setOnAction(e -> applyEvent(supplier.get(), ctx));
        return b;
    }

    private void applyEvent(InteractionEvent e, SocialContext sc) {
        bouncePortrait();
        InteractionEngine.Result r = engine.apply(e, sc);
        ctx = sc;
        showDelta(r);
        refreshStateTexts();
    }

    private void refreshStateTexts() {
        Stats s = engine.stats();
        EmotionState e = engine.emotion(s);
        MoodModel m = engine.mood();
        favorBar.setProgress(s.favor());
        bondBar.setProgress(s.bond());
        sinBar.setProgress(s.sincerity());
        novBar.setProgress(s.novelty());
        if (debugStage != null && debugStage.isShowing()) {
            if (dbgStep != null)
                dbgStep.setText(String.format(Locale.ROOT, "步数=%d", engine.ticks()));
            if (dbgStats != null)
                dbgStats.setText(String.format(Locale.ROOT, "favor=%.3f  bond=%.3f  sincerity=%.3f  novelty=%.3f",
                        s.favor(), s.bond(), s.sincerity(), s.novelty()));
            if (dbgMood != null)
                dbgMood.setText(String.format(Locale.ROOT, "情绪(PAD): P=%.3f A=%.3f D=%.3f  Top=%s",
                        e.pleasure(), e.arousal(), e.dominance(), m.topMood()));
        }
        endingLabel.setText("结局：" + endingName(s, engine.ticks()));
    }

    private void bouncePortrait() {
        ScaleTransition up = new ScaleTransition(Duration.millis(110), portraitView);
        up.setToX(1.05);
        up.setToY(1.05);
        ScaleTransition down = new ScaleTransition(Duration.millis(140), portraitView);
        down.setToX(1.0);
        down.setToY(1.0);
        SequentialTransition seq = new SequentialTransition(up, down);
        seq.play();
    }

    private void showDelta(InteractionEngine.Result r) {
        Stats b = r.before();
        Stats a = r.after();
        double df = a.favor() - b.favor();
        double db = a.bond() - b.bond();
        double ds = a.sincerity() - b.sincerity();
        double dn = a.novelty() - b.novelty();
        overlayGroup.getChildren().clear();
        double baseY = 260;
        int index = 0;
        if (Math.abs(df) > 1e-6) {
            overlayGroup.getChildren().add(makeFloatingText(formatDelta("好感", df), 40, baseY + index * 26));
            index++;
        }
        if (Math.abs(db) > 1e-6) {
            overlayGroup.getChildren().add(makeFloatingText(formatDelta("羁绊", db), 40, baseY + index * 26));
            index++;
        }
        if (Math.abs(ds) > 1e-6) {
            overlayGroup.getChildren().add(makeFloatingText(formatDelta("真诚", ds), 40, baseY + index * 26));
            index++;
        }
        if (Math.abs(dn) > 1e-6) {
            overlayGroup.getChildren().add(makeFloatingText(formatDelta("新鲜感", dn), 40, baseY + index * 26));
        }
    }

    private Text makeFloatingText(String s, double x, double y) {
        Text t = new Text(s);
        t.setFont(loadFont("/assets/font/lxgw3500.ttf", 20));
        t.setFill(Color.web("#ff6fb4"));
        t.setOpacity(0);
        t.setTranslateX(x);
        t.setTranslateY(y);
        TranslateTransition right = new TranslateTransition(Duration.millis(180), t);
        right.setByX(38);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), t);
        fadeIn.setToValue(1.0);
        TranslateTransition up = new TranslateTransition(Duration.millis(720), t);
        up.setByY(-36);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(720), t);
        fadeOut.setToValue(0.0);
        ParallelTransition p1 = new ParallelTransition(right, fadeIn);
        ParallelTransition p2 = new ParallelTransition(up, fadeOut);
        SequentialTransition seq = new SequentialTransition(p1, p2);
        seq.setOnFinished(ev -> overlayGroup.getChildren().remove(t));
        seq.play();
        return t;
    }

    private void showConsole() {
        if (debugStage != null && debugStage.isShowing()) {
            debugStage.toFront();
            return;
        }
        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        dbgStep = new Label();
        dbgStep.setFont(uiFont);
        dbgStats = new Label();
        dbgStats.setFont(uiFont);
        dbgMood = new Label();
        dbgMood.setFont(uiFont);
        Label rLabel = new Label("对手数");
        rLabel.setFont(uiFont);
        dbgRivals = new Spinner<>();
        dbgRivals.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 8, ctx.rivals()));
        dbgRivals.valueProperty().addListener((o, a, b) -> ctx = new SocialContext(b, ctx.lastOtherAffection(), ctx.meanOtherFavor()));
        Label mLabel = new Label("其他平均好感");
        mLabel.setFont(uiFont);
        dbgOthersMean = new Slider(0, 1, ctx.meanOtherFavor());
        dbgOthersMean.setPrefWidth(180);
        dbgOthersMean.valueProperty().addListener((o, a, b) -> ctx = new SocialContext(ctx.rivals(), ctx.lastOtherAffection(), Stats.clamp(b.doubleValue())));
        HBox ctxBox = new HBox(12, rLabel, dbgRivals, mLabel, dbgOthersMean);
        ctxBox.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().addAll(dbgStep, dbgStats, dbgMood, new Separator(), ctxBox);
        debugStage = new Stage();
        debugStage.setTitle("控制台");
        debugStage.setScene(new Scene(root, 520, 200));
        debugStage.show();
        refreshStateTexts();
    }

    private static String formatDelta(String name, double v) {
        DecimalFormat df = new DecimalFormat("+0.000;-0.000");
        return name + " " + df.format(v);
        }

    private String endingName(Stats s, int ticks) {
        if (s.favor() > 0.85 && s.bond() > 0.7 && s.sincerity() > 0.65) return "温馨恋人";
        if (ticks > 45 && s.favor() < 0.25 && s.bond() < 0.2) return "渐行渐远";
        if (ticks > 60 && s.sincerity() < 0.25) return "信任破裂";
        return "进行中";
    }

    private Font loadFont(String path, double size) {
        try {
            InputStream in = getClass().getResourceAsStream(path);
            if (in != null) {
                return Font.loadFont(in, size);
            }
        } catch (Exception ignored) {
        }
        return Font.font(size);
    }

    private Image loadImage(String path) {
        InputStream in = getClass().getResourceAsStream(path);
        if (in == null) throw new RuntimeException("Missing resource: " + path);
        return new Image(in);
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

    public static void main(String[] args) {
        launch(args);
    }

    private interface SupplierEvent {
        InteractionEvent get();
    }
}
