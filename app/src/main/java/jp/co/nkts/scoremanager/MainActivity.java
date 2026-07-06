package jp.co.nkts.scoremanager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int HOLES = 18;
    private static final int PLAYERS = 4;
    private static final int SCORE_MAX = 15;
    private static final long AUTO_SAVE_INTERVAL_MS = 1000L;
    private static final String PREF_NAME = "nk_score_manager_beta";
    private static final String KEY_PREFIX = "v15_";
    private static final String KEY_HISTORY = "v15_history";

    private static final int COLOR_BG = 0xFFF8FAFC;
    private static final int COLOR_CARD = 0xFFFFFFFF;
    private static final int COLOR_PRIMARY = 0xFF166534;
    private static final int COLOR_PRIMARY_DARK = 0xFF14532D;
    private static final int COLOR_PRIMARY_SOFT = 0xFFDCFCE7;
    private static final int COLOR_TEXT = 0xFF0F172A;
    private static final int COLOR_MUTED = 0xFF64748B;
    private static final int COLOR_BORDER = 0xFFE2E8F0;
    private static final int COLOR_PANEL = 0xFFEFF6FF;
    private static final int COLOR_DANGER = 0xFFB91C1C;
    private static final int COLOR_WARNING = 0xFF92400E;

    private final int[] defaultPars = {4, 4, 3, 5, 4, 4, 5, 3, 4, 4, 5, 4, 3, 4, 4, 5, 3, 4};
    private final String[] playerCountOptions = {"1名", "2名", "3名", "4名"};
    private final String[] teeResultLabels = {"未選択", "フェアウェイ", "ラフ", "OB"};
    private final String[] directionLabels = {"未選択", "左", "中央", "右"};

    private LinearLayout root;
    private ScrollView scrollView;
    private TextView saveStatusText;
    private TextView selectedHistoryDetailText;

    private String dateText = "";
    private String courseText = "";
    private String teeText = "";
    private String startTimeText = "";
    private int activePlayers = 1;
    private int currentHole = 0;
    private boolean registrationMode = false;
    private String selectedHistoryDetail = "";

    private final int[] pars = new int[HOLES];
    private final String[] playerNames = {"Player 1", "Player 2", "Player 3", "Player 4"};
    private final int[][] scores = new int[PLAYERS][HOLES];
    private final int[] player1Putts = new int[HOLES];
    private final int[] teeResults = new int[HOLES];
    private final int[] directions = new int[HOLES];

    private boolean binding = false;

    private final Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoSaveRunnable = new Runnable() {
        @Override public void run() {
            saveDraft(false);
            autoSaveHandler.postDelayed(this, AUTO_SAVE_INTERVAL_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initDefaults();
        restoreDraft();
        setContentView(createBaseView());
        if (registrationMode) renderRegistrationScreen();
        else renderHomeScreen();
        startAutoSaveTimer();
    }

    @Override protected void onPause() {
        saveDraft(false);
        super.onPause();
    }

    @Override protected void onStop() {
        saveDraft(false);
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        saveDraft(false);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        saveDraft(false);
        super.onDestroy();
    }

    private View createBaseView() {
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(18));
        scrollView.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private void initDefaults() {
        System.arraycopy(defaultPars, 0, pars, 0, HOLES);
        dateText = nowDate();
        for (int p = 0; p < PLAYERS; p++) playerNames[p] = "Player " + (p + 1);
    }

    private void renderHomeScreen() {
        registrationMode = false;
        saveDraft(false);
        root.removeAllViews();
        root.addView(createHeroCard());
        root.addView(createHomeActionCard());
        root.addView(createStatsCard());
        root.addView(createHistoryCard());
        saveStatusText = text("保存状態: ホーム表示中", 13, COLOR_MUTED, false);
        saveStatusText.setGravity(Gravity.CENTER_HORIZONTAL);
        saveStatusText.setPadding(0, dp(10), 0, dp(8));
        root.addView(saveStatusText);
        scrollTop();
    }

    private View createHeroCard() {
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("nk_logo", "drawable", getPackageName()));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        logoParams.setMargins(0, 0, 0, dp(8));
        card.addView(logo, logoParams);
        TextView title = text("NK Score Manager", 27, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(title);
        TextView sub = text("履歴・平均・簡単登録 V1.5", 14, COLOR_MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        sub.setPadding(0, dp(4), 0, 0);
        card.addView(sub);
        return card;
    }

    private View createHomeActionCard() {
        LinearLayout card = card();
        card.addView(sectionCompact("フロントページ"));
        TextView help = text("カート移動中でも押しやすい登録画面に切り替えます。登録中は全入力が終わるまで登録画面を維持します。", 13, COLOR_MUTED, false);
        help.setPadding(0, 0, 0, dp(8));
        card.addView(help);

        if (hasAnyDraft()) {
            Button resume = button("入力中のラウンドに戻る", true);
            resume.setOnClickListener(v -> {
                registrationMode = true;
                renderRegistrationScreen();
                saveDraft(false);
            });
            card.addView(resume, fullWidthButtonParams());
        }

        Button start = button("新規スコア登録", true);
        start.setOnClickListener(v -> {
            resetRound(false);
            registrationMode = true;
            renderRegistrationScreen();
            saveDraft(false);
        });
        card.addView(start, fullWidthButtonParams());
        return card;
    }

    private View createStatsCard() {
        LinearLayout card = card();
        card.addView(sectionCompact("平均スコア"));
        ArrayList<RoundRecord> records = loadHistoryRecords();
        TextView stats = text(buildStatsText(records), 14, COLOR_TEXT, true);
        stats.setBackground(rounded(COLOR_PRIMARY_SOFT, COLOR_PRIMARY_SOFT, 14));
        stats.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.addView(stats);
        return card;
    }

    private View createHistoryCard() {
        LinearLayout card = card();
        card.addView(sectionCompact("過去のプレイ一覧"));
        ArrayList<RoundRecord> records = loadHistoryRecords();
        if (records.isEmpty()) {
            TextView empty = text("保存済みプレイはまだありません。", 14, COLOR_MUTED, false);
            empty.setPadding(0, dp(6), 0, dp(6));
            card.addView(empty);
        } else {
            for (int i = 0; i < records.size() && i < 30; i++) {
                RoundRecord record = records.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setBackground(rounded(0xFFF8FAFC, COLOR_BORDER, 14));
                row.setPadding(dp(10), dp(10), dp(10), dp(10));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rp.setMargins(0, dp(5), 0, dp(5));
                TextView summary = text(record.date + "  " + record.course + "\nPlayer1 TOTAL: " + record.total + " / Putt: " + record.putts + " / FW: " + record.fwKeep + "/" + record.fwTarget, 13, COLOR_TEXT, true);
                row.addView(summary);
                Button detail = button("履歴を見る", false);
                detail.setOnClickListener(v -> {
                    selectedHistoryDetail = record.detail;
                    renderHomeScreen();
                });
                row.addView(detail, fullWidthButtonParams());
                card.addView(row, rp);
            }
        }
        selectedHistoryDetailText = panel(TextUtils.isEmpty(selectedHistoryDetail) ? "履歴を選択すると詳細が表示されます。" : selectedHistoryDetail, false);
        card.addView(selectedHistoryDetailText);
        return card;
    }

    private void renderRegistrationScreen() {
        registrationMode = true;
        root.removeAllViews();
        root.addView(createRegistrationHeaderCard());
        root.addView(createRoundInfoCard());
        root.addView(createPlayerSetupCard());
        root.addView(createHoleCard());
        root.addView(createSummaryCard());
        root.addView(createRegistrationButtons());
        saveStatusText = text("保存状態: 登録中", 13, COLOR_MUTED, false);
        saveStatusText.setGravity(Gravity.CENTER_HORIZONTAL);
        saveStatusText.setPadding(0, dp(10), 0, dp(8));
        root.addView(saveStatusText);
        scrollTop();
    }

    private View createRegistrationHeaderCard() {
        LinearLayout card = card();
        TextView title = text("スコア登録モード", 22, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(title);
        TextView sub = text("1Hずつ大きいボタンで入力。全ホール登録完了までこの画面を維持します。", 13, COLOR_MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        sub.setPadding(0, dp(6), 0, 0);
        card.addView(sub);
        return card;
    }

    private View createRoundInfoCard() {
        LinearLayout card = card();
        card.addView(sectionCompact("ラウンド情報"));
        EditText dateEdit = normalInput("日付");
        dateEdit.setText(dateText);
        attachTextWatcher(dateEdit, value -> dateText = value);
        EditText courseEdit = normalInput("ゴルフ場名");
        courseEdit.setText(courseText);
        attachTextWatcher(courseEdit, value -> courseText = value);
        EditText teeEdit = normalInput("ティー");
        teeEdit.setText(teeText);
        attachTextWatcher(teeEdit, value -> teeText = value);
        EditText startEdit = normalInput("スタート時間 / OUT / IN");
        startEdit.setText(startTimeText);
        attachTextWatcher(startEdit, value -> startTimeText = value);
        card.addView(dateEdit);
        card.addView(courseEdit);
        card.addView(teeEdit);
        card.addView(startEdit);
        return card;
    }

    private View createPlayerSetupCard() {
        LinearLayout card = card();
        card.addView(sectionCompact("人数・プレイヤー名"));
        LinearLayout countRow = new LinearLayout(this);
        countRow.setOrientation(LinearLayout.HORIZONTAL);
        countRow.setGravity(Gravity.CENTER_VERTICAL);
        countRow.addView(text("入力人数", 15, COLOR_TEXT, true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Spinner playerCountSpinner = spinner(playerCountOptions);
        playerCountSpinner.setSelection(activePlayers - 1);
        playerCountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (binding) return;
                int next = position + 1;
                if (next != activePlayers) {
                    activePlayers = next;
                    saveDraft(false);
                    renderRegistrationScreen();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        countRow.addView(playerCountSpinner, new LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(countRow);

        for (int p = 0; p < activePlayers; p++) {
            final int player = p;
            EditText name = normalInput(p == 0 ? "Player1（詳細分析対象）" : "Player" + (p + 1));
            name.setText(playerNames[p]);
            attachTextWatcher(name, value -> playerNames[player] = value);
            card.addView(name);
        }
        return card;
    }

    private View createHoleCard() {
        LinearLayout card = card();
        TextView title = text((currentHole + 1) + "H", 28, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(title);
        TextView progress = text(buildProgressText(), 13, COLOR_MUTED, true);
        progress.setGravity(Gravity.CENTER_HORIZONTAL);
        progress.setPadding(0, dp(4), 0, dp(8));
        card.addView(progress);

        card.addView(createParControl());
        card.addView(createPlayer1Input());
        for (int p = 1; p < activePlayers; p++) card.addView(createOtherPlayerInput(p));
        card.addView(createHoleNavButtons());
        return card;
    }

    private View createParControl() {
        LinearLayout wrap = cardLite();
        wrap.addView(text("PAR", 13, COLOR_MUTED, true));
        wrap.addView(stepper("PAR", pars[currentHole] == 0 ? "-" : String.valueOf(pars[currentHole]), () -> {
            pars[currentHole] = Math.max(3, pars[currentHole] - 1);
            saveDraft(false);
            renderRegistrationScreen();
        }, () -> {
            pars[currentHole] = Math.min(6, pars[currentHole] + 1);
            saveDraft(false);
            renderRegistrationScreen();
        }, null));
        return wrap;
    }

    private View createPlayer1Input() {
        LinearLayout wrap = cardLite();
        wrap.addView(text(displayPlayerName(0) + "（詳細入力）", 18, COLOR_TEXT, true));
        wrap.addView(stepper("SCORE", formatValue(scores[0][currentHole]), () -> changeScore(0, -1), () -> changeScore(0, 1), () -> clearScore(0)));
        wrap.addView(stepper("PAT", formatValue(player1Putts[currentHole]), () -> changePutt(-1), () -> changePutt(1), () -> clearPutt()));
        wrap.addView(choiceGroup("ティーショット結果", teeResultLabels, teeResults[currentHole], index -> {
            teeResults[currentHole] = index;
            saveDraft(false);
            renderRegistrationScreen();
        }));
        wrap.addView(choiceGroup("狙いに対しての方向性", directionLabels, directions[currentHole], index -> {
            directions[currentHole] = index;
            saveDraft(false);
            renderRegistrationScreen();
        }));
        return wrap;
    }

    private View createOtherPlayerInput(int player) {
        LinearLayout wrap = cardLite();
        wrap.addView(text(displayPlayerName(player), 18, COLOR_TEXT, true));
        TextView note = text("PAT数・ティーショット分析は不要。スコアだけ入力します。", 12, COLOR_MUTED, false);
        note.setPadding(0, dp(2), 0, dp(4));
        wrap.addView(note);
        wrap.addView(stepper("SCORE", formatValue(scores[player][currentHole]), () -> changeScore(player, -1), () -> changeScore(player, 1), () -> clearScore(player)));
        return wrap;
    }

    private View createHoleNavButtons() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button prev = button("前のH", false);
        prev.setEnabled(currentHole > 0);
        prev.setOnClickListener(v -> moveHole(-1));
        Button next = button(currentHole == HOLES - 1 ? "最終H" : "次のH", true);
        next.setEnabled(currentHole < HOLES - 1);
        next.setOnClickListener(v -> moveHole(1));
        nav.addView(prev, weightedParams(1f, dp(3), dp(3)));
        nav.addView(next, weightedParams(1f, dp(3), dp(3)));
        return nav;
    }

    private View createSummaryCard() {
        LinearLayout card = card();
        card.addView(sectionCompact("現在の集計"));
        TextView summary = panel(buildLiveSummary(), true);
        card.addView(summary);
        return card;
    }

    private View createRegistrationButtons() {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        Button save = button("ここまで保存", false);
        save.setOnClickListener(v -> saveDraft(true));
        area.addView(save, fullWidthButtonParams());

        Button finish = button("全入力完了 → 履歴に保存", true);
        finish.setOnClickListener(v -> finishRound());
        area.addView(finish, fullWidthButtonParams());

        TextView hint = text("全プレイヤーの18ホールスコアが入るまで、登録画面は閉じません。", 12, COLOR_MUTED, false);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        hint.setPadding(0, dp(4), 0, dp(8));
        area.addView(hint);
        return area;
    }

    private LinearLayout stepper(String label, String value, Runnable minus, Runnable plus, Runnable clear) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(8));
        TextView labelView = text(label, 12, COLOR_MUTED, true);
        labelView.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(labelView);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button minusButton = bigButton("−", false);
        minusButton.setOnClickListener(v -> minus.run());
        TextView valueView = text(value, 34, COLOR_TEXT, true);
        valueView.setGravity(Gravity.CENTER);
        valueView.setBackground(rounded(0xFFFFFFFF, COLOR_BORDER, 16));
        Button plusButton = bigButton("＋", true);
        plusButton.setOnClickListener(v -> plus.run());
        row.addView(minusButton, weightedParams(1f, dp(3), dp(3)));
        row.addView(valueView, weightedParams(1.2f, dp(3), dp(3)));
        row.addView(plusButton, weightedParams(1f, dp(3), dp(3)));
        box.addView(row);

        if (clear != null) {
            Button clearButton = button("未入力に戻す", false);
            clearButton.setOnClickListener(v -> clear.run());
            box.addView(clearButton, fullWidthButtonParams());
        }
        return box;
    }

    private LinearLayout choiceGroup(String title, String[] labels, int selected, ChoiceSink sink) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(0, dp(8), 0, dp(8));
        TextView titleView = text(title, 12, COLOR_MUTED, true);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        group.addView(titleView);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 1; i < labels.length; i++) {
            final int index = i;
            Button b = choiceButton(labels[i], selected == i);
            b.setOnClickListener(v -> sink.set(index));
            row.addView(b, weightedParams(1f, dp(3), dp(3)));
        }
        group.addView(row);
        if (selected > 0) {
            Button clear = button("選択解除", false);
            clear.setOnClickListener(v -> sink.set(0));
            group.addView(clear, fullWidthButtonParams());
        }
        return group;
    }

    private void changeScore(int player, int delta) {
        int current = scores[player][currentHole];
        if (current == 0 && delta > 0) current = 1;
        else current = bound(current + delta, 0, SCORE_MAX);
        scores[player][currentHole] = current;
        saveDraft(false);
        renderRegistrationScreen();
    }

    private void clearScore(int player) {
        scores[player][currentHole] = 0;
        saveDraft(false);
        renderRegistrationScreen();
    }

    private void changePutt(int delta) {
        int current = player1Putts[currentHole];
        if (current == 0 && delta > 0) current = 1;
        else current = bound(current + delta, 0, 8);
        player1Putts[currentHole] = current;
        saveDraft(false);
        renderRegistrationScreen();
    }

    private void clearPutt() {
        player1Putts[currentHole] = 0;
        saveDraft(false);
        renderRegistrationScreen();
    }

    private void moveHole(int delta) {
        currentHole = bound(currentHole + delta, 0, HOLES - 1);
        saveDraft(false);
        renderRegistrationScreen();
    }

    private void finishRound() {
        int missing = countMissingScores();
        if (missing > 0) {
            Toast.makeText(this, "未入力スコアがあります: " + missing + "件", Toast.LENGTH_SHORT).show();
            registrationMode = true;
            renderRegistrationScreen();
            return;
        }
        String export = buildExportText();
        RoundRecord record = buildRoundRecord(export);
        ArrayList<RoundRecord> records = loadHistoryRecords();
        records.add(0, record);
        saveHistoryRecords(records);
        Toast.makeText(this, "履歴に保存しました。", Toast.LENGTH_SHORT).show();
        resetRound(false);
        selectedHistoryDetail = export;
        registrationMode = false;
        saveDraft(false);
        renderHomeScreen();
    }

    private String buildProgressText() {
        int p1Done = countEnteredForPlayer(0);
        return "進捗 Player1: " + p1Done + "/18  /  全体未入力: " + countMissingScores();
    }

    private String buildLiveSummary() {
        StringBuilder builder = new StringBuilder();
        for (int p = 0; p < activePlayers; p++) {
            int total = 0;
            int parTotal = 0;
            int entered = 0;
            for (int h = 0; h < HOLES; h++) {
                if (scores[p][h] > 0) {
                    entered++;
                    total += scores[p][h];
                    parTotal += pars[h];
                }
            }
            int diff = total - parTotal;
            String diffText = entered == 0 ? "-" : (diff == 0 ? "EVEN" : (diff > 0 ? "+" + diff : String.valueOf(diff)));
            builder.append(displayPlayerName(p)).append("：")
                    .append(entered).append("H / TOTAL ")
                    .append(entered == 0 ? "-" : total)
                    .append(" / ").append(diffText);
            if (p == 0) builder.append(" / PAT ").append(sum(player1Putts));
            builder.append("\n");
        }
        builder.append("Player1 Tee: FW ").append(countTeeResult(1))
                .append(" / ラフ ").append(countTeeResult(2))
                .append(" / OB ").append(countTeeResult(3));
        return builder.toString().trim();
    }

    private String buildExportText() {
        StringBuilder builder = new StringBuilder();
        builder.append("NK Score Export V1.5\n");
        builder.append("日付: ").append(dateText).append("\n");
        builder.append("コース: ").append(courseText).append("\n");
        builder.append("ティー: ").append(teeText).append("\n");
        builder.append("スタート: ").append(startTimeText).append("\n");
        builder.append("人数: ").append(activePlayers).append("名\n");
        builder.append("====================\n");
        for (int h = 0; h < HOLES; h++) {
            builder.append(h + 1).append("H / PAR:").append(pars[h]).append("\n");
            for (int p = 0; p < activePlayers; p++) {
                builder.append("  ").append(displayPlayerName(p))
                        .append(" / SCORE:").append(formatValue(scores[p][h]));
                if (p == 0) {
                    builder.append(" / PAT:").append(formatValue(player1Putts[h]))
                            .append(" / Tee:").append(teeResultLabels[teeResults[h]])
                            .append(" / Direction:").append(directionLabels[directions[h]]);
                }
                builder.append("\n");
            }
            if (h == 8) builder.append("---------- IN / OUT ----------\n");
        }
        builder.append("====================\n");
        builder.append(buildLiveSummary()).append("\n");
        return builder.toString();
    }

    private RoundRecord buildRoundRecord(String export) {
        RoundRecord record = new RoundRecord();
        record.timestamp = parseDateMillis(dateText);
        record.date = dateText;
        record.course = TextUtils.isEmpty(courseText) ? "未入力" : courseText;
        record.total = totalScore(0);
        record.parTotal = totalParForEntered(0);
        record.putts = sum(player1Putts);
        record.fwKeep = countTeeResult(1);
        record.fwTarget = countTeeTargets();
        record.detail = export;
        return record;
    }

    private String buildStatsText(ArrayList<RoundRecord> records) {
        long now = System.currentTimeMillis();
        String three = averageText(records, now - 90L * 24L * 60L * 60L * 1000L, "過去3カ月");
        String year = averageText(records, now - 365L * 24L * 60L * 60L * 1000L, "過去1年");
        return three + "\n" + year;
    }

    private String averageText(ArrayList<RoundRecord> records, long since, String label) {
        int count = 0;
        int total = 0;
        int putts = 0;
        int fwKeep = 0;
        int fwTarget = 0;
        for (RoundRecord r : records) {
            if (r.timestamp >= since) {
                count++;
                total += r.total;
                putts += r.putts;
                fwKeep += r.fwKeep;
                fwTarget += r.fwTarget;
            }
        }
        if (count == 0) return label + ": データなし";
        String avgScore = String.format(Locale.US, "%.1f", total * 1.0 / count);
        String avgPutt = String.format(Locale.US, "%.1f", putts * 1.0 / count);
        String fw = fwTarget == 0 ? "-" : String.format(Locale.US, "%.1f%%", fwKeep * 100.0 / fwTarget);
        return label + ": " + count + "回 / 平均 " + avgScore + " / PAT平均 " + avgPutt + " / FW " + fw;
    }

    private void saveDraft(boolean showToast) {
        SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
        editor.putBoolean(KEY_PREFIX + "registrationMode", registrationMode);
        editor.putString(KEY_PREFIX + "date", dateText);
        editor.putString(KEY_PREFIX + "course", courseText);
        editor.putString(KEY_PREFIX + "tee", teeText);
        editor.putString(KEY_PREFIX + "start", startTimeText);
        editor.putInt(KEY_PREFIX + "activePlayers", activePlayers);
        editor.putInt(KEY_PREFIX + "currentHole", currentHole);
        editor.putString(KEY_PREFIX + "pars", serializeIntArray(pars));
        editor.putString(KEY_PREFIX + "names", serializeStringArray(playerNames));
        editor.putString(KEY_PREFIX + "putts", serializeIntArray(player1Putts));
        editor.putString(KEY_PREFIX + "teeResults", serializeIntArray(teeResults));
        editor.putString(KEY_PREFIX + "directions", serializeIntArray(directions));
        for (int p = 0; p < PLAYERS; p++) editor.putString(KEY_PREFIX + "scores_" + p, serializeIntArray(scores[p]));
        editor.putString(KEY_PREFIX + "lastSaved", nowFull());
        boolean ok = editor.commit();
        if (saveStatusText != null) saveStatusText.setText(ok ? "保存状態: 保存済み " + nowFull() : "保存状態: 保存失敗");
        if (showToast) Toast.makeText(this, ok ? "保存しました。" : "保存に失敗しました。", Toast.LENGTH_SHORT).show();
    }

    private void restoreDraft() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        registrationMode = prefs.getBoolean(KEY_PREFIX + "registrationMode", false);
        dateText = prefs.getString(KEY_PREFIX + "date", nowDate());
        courseText = prefs.getString(KEY_PREFIX + "course", "");
        teeText = prefs.getString(KEY_PREFIX + "tee", "");
        startTimeText = prefs.getString(KEY_PREFIX + "start", "");
        activePlayers = bound(prefs.getInt(KEY_PREFIX + "activePlayers", 1), 1, PLAYERS);
        currentHole = bound(prefs.getInt(KEY_PREFIX + "currentHole", 0), 0, HOLES - 1);
        restoreIntArray(prefs.getString(KEY_PREFIX + "pars", ""), pars, defaultPars, 3, 6);
        restoreStringArray(prefs.getString(KEY_PREFIX + "names", ""), playerNames);
        restoreIntArray(prefs.getString(KEY_PREFIX + "putts", ""), player1Putts, null, 0, 8);
        restoreIntArray(prefs.getString(KEY_PREFIX + "teeResults", ""), teeResults, null, 0, teeResultLabels.length - 1);
        restoreIntArray(prefs.getString(KEY_PREFIX + "directions", ""), directions, null, 0, directionLabels.length - 1);
        for (int p = 0; p < PLAYERS; p++) restoreIntArray(prefs.getString(KEY_PREFIX + "scores_" + p, ""), scores[p], null, 0, SCORE_MAX);
    }

    private void resetRound(boolean keepMode) {
        dateText = nowDate();
        courseText = "";
        teeText = "";
        startTimeText = "";
        activePlayers = 1;
        currentHole = 0;
        System.arraycopy(defaultPars, 0, pars, 0, HOLES);
        for (int p = 0; p < PLAYERS; p++) {
            playerNames[p] = "Player " + (p + 1);
            for (int h = 0; h < HOLES; h++) scores[p][h] = 0;
        }
        for (int h = 0; h < HOLES; h++) {
            player1Putts[h] = 0;
            teeResults[h] = 0;
            directions[h] = 0;
        }
        registrationMode = keepMode;
    }

    private boolean hasAnyDraft() {
        if (!TextUtils.isEmpty(courseText) || !TextUtils.isEmpty(teeText) || !TextUtils.isEmpty(startTimeText)) return true;
        for (int p = 0; p < PLAYERS; p++) for (int h = 0; h < HOLES; h++) if (scores[p][h] > 0) return true;
        return false;
    }

    private int countMissingScores() {
        int missing = 0;
        for (int p = 0; p < activePlayers; p++) for (int h = 0; h < HOLES; h++) if (scores[p][h] == 0) missing++;
        return missing;
    }

    private int countEnteredForPlayer(int player) {
        int count = 0;
        for (int h = 0; h < HOLES; h++) if (scores[player][h] > 0) count++;
        return count;
    }

    private int totalScore(int player) {
        int total = 0;
        for (int h = 0; h < HOLES; h++) total += scores[player][h];
        return total;
    }

    private int totalParForEntered(int player) {
        int total = 0;
        for (int h = 0; h < HOLES; h++) if (scores[player][h] > 0) total += pars[h];
        return total;
    }

    private int sum(int[] values) {
        int total = 0;
        for (int value : values) total += value;
        return total;
    }

    private int countTeeResult(int result) {
        int count = 0;
        for (int value : teeResults) if (value == result) count++;
        return count;
    }

    private int countTeeTargets() {
        int count = 0;
        for (int value : teeResults) if (value > 0) count++;
        return count;
    }

    private ArrayList<RoundRecord> loadHistoryRecords() {
        ArrayList<RoundRecord> records = new ArrayList<>();
        String raw = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getString(KEY_HISTORY, "");
        if (TextUtils.isEmpty(raw)) return records;
        String[] lines = raw.split("\n", -1);
        for (String line : lines) {
            if (TextUtils.isEmpty(line)) continue;
            RoundRecord record = RoundRecord.fromLine(line);
            if (record != null) records.add(record);
        }
        return records;
    }

    private void saveHistoryRecords(ArrayList<RoundRecord> records) {
        ArrayList<String> lines = new ArrayList<>();
        for (int i = 0; i < records.size() && i < 200; i++) lines.add(records.get(i).toLine());
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putString(KEY_HISTORY, TextUtils.join("\n", lines)).commit();
    }

    private void attachTextWatcher(EditText editText, TextValueSink sink) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (binding) return;
                sink.set(s == null ? "" : s.toString());
                saveDraft(false);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setMinimumWidth(dp(100));
        return spinner;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(COLOR_CARD, COLOR_BORDER, 18));
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout cardLite() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(0xFFF8FAFC, COLOR_BORDER, 16));
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        card.setLayoutParams(params);
        return card;
    }

    private GradientDrawable rounded(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams weightedParams(float weight, int horizontalMargin, int verticalMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin);
        return params;
    }

    private LinearLayout.LayoutParams fullWidthButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, dp(5));
        return params;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(0xFFFFFFFF);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setBackgroundResource(getResources().getIdentifier(primary ? "button_bg" : "secondary_button_bg", "drawable", getPackageName()));
        return button;
    }

    private Button bigButton(String label, boolean primary) {
        Button button = button(label, primary);
        button.setTextSize(24);
        button.setMinHeight(dp(62));
        return button;
    }

    private Button choiceButton(String label, boolean selected) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(selected ? 0xFFFFFFFF : COLOR_TEXT);
        button.setMinHeight(dp(56));
        button.setBackground(rounded(selected ? COLOR_PRIMARY : 0xFFFFFFFF, selected ? COLOR_PRIMARY_DARK : COLOR_BORDER, 16));
        return button;
    }

    private EditText normalInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(0xFF94A3B8);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        return input;
    }

    private TextView sectionCompact(String value) {
        TextView view = text(value, 16, COLOR_TEXT, true);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private TextView panel(String value, boolean important) {
        TextView view = text(value, important ? 15 : 13, important ? COLOR_TEXT : 0xFF1E293B, important);
        view.setBackground(rounded(important ? COLOR_PRIMARY_SOFT : COLOR_PANEL, important ? COLOR_PRIMARY_SOFT : COLOR_BORDER, 14));
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private String displayPlayerName(int index) {
        String name = playerNames[index];
        return TextUtils.isEmpty(name) ? "Player " + (index + 1) : name.trim();
    }

    private String formatValue(int value) {
        return value <= 0 ? "-" : String.valueOf(value);
    }

    private String serializeIntArray(int[] values) {
        ArrayList<String> parts = new ArrayList<>();
        for (int value : values) parts.add(String.valueOf(value));
        return TextUtils.join(",", parts);
    }

    private void restoreIntArray(String saved, int[] target, int[] fallback, int min, int max) {
        if (fallback != null && fallback.length == target.length) System.arraycopy(fallback, 0, target, 0, target.length);
        if (TextUtils.isEmpty(saved)) return;
        String[] parts = saved.split(",", -1);
        for (int i = 0; i < target.length && i < parts.length; i++) target[i] = bound(parseInt(parts[i], target[i]), min, max);
    }

    private String serializeStringArray(String[] values) {
        ArrayList<String> parts = new ArrayList<>();
        for (String value : values) parts.add(encode(value == null ? "" : value));
        return TextUtils.join(",", parts);
    }

    private void restoreStringArray(String saved, String[] target) {
        if (TextUtils.isEmpty(saved)) return;
        String[] parts = saved.split(",", -1);
        for (int i = 0; i < target.length && i < parts.length; i++) target[i] = decode(parts[i]);
    }

    private String encode(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private String decode(String value) {
        try {
            return new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long parseDateMillis(String date) {
        try {
            return new SimpleDateFormat("yyyy/MM/dd", Locale.US).parse(date).getTime();
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }

    private int bound(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String nowDate() {
        return new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date());
    }

    private String nowFull() {
        return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(new Date());
    }

    private void startAutoSaveTimer() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_INTERVAL_MS);
    }

    private void scrollTop() {
        if (scrollView != null) scrollView.post(() -> scrollView.fullScroll(View.FOCUS_UP));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface TextValueSink {
        void set(String value);
    }

    private interface ChoiceSink {
        void set(int index);
    }

    private static class RoundRecord {
        long timestamp;
        String date;
        String course;
        int total;
        int parTotal;
        int putts;
        int fwKeep;
        int fwTarget;
        String detail;

        String toLine() {
            return timestamp + "|" + enc(date) + "|" + enc(course) + "|" + total + "|" + parTotal + "|" + putts + "|" + fwKeep + "|" + fwTarget + "|" + enc(detail);
        }

        static RoundRecord fromLine(String line) {
            try {
                String[] parts = line.split("\\|", -1);
                if (parts.length < 9) return null;
                RoundRecord r = new RoundRecord();
                r.timestamp = Long.parseLong(parts[0]);
                r.date = dec(parts[1]);
                r.course = dec(parts[2]);
                r.total = Integer.parseInt(parts[3]);
                r.parTotal = Integer.parseInt(parts[4]);
                r.putts = Integer.parseInt(parts[5]);
                r.fwKeep = Integer.parseInt(parts[6]);
                r.fwTarget = Integer.parseInt(parts[7]);
                r.detail = dec(parts[8]);
                return r;
            } catch (Exception ignored) {
                return null;
            }
        }

        private static String enc(String value) {
            return Base64.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }

        private static String dec(String value) {
            try {
                return new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return "";
            }
        }
    }
}
