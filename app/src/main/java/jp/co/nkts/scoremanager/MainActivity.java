package jp.co.nkts.scoremanager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
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
import android.widget.NumberPicker;
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
    private static final long AUTO_SAVE_INTERVAL_MS = 1200L;
    private static final String PREF_NAME = "nk_score_manager_beta";
    private static final String KEY_PREFIX = "v14_";

    private static final int COLOR_BG = 0xFFF8FAFC;
    private static final int COLOR_CARD = 0xFFFFFFFF;
    private static final int COLOR_PRIMARY = 0xFF166534;
    private static final int COLOR_PRIMARY_DARK = 0xFF14532D;
    private static final int COLOR_PRIMARY_SOFT = 0xFFDCFCE7;
    private static final int COLOR_TEXT = 0xFF0F172A;
    private static final int COLOR_MUTED = 0xFF64748B;
    private static final int COLOR_BORDER = 0xFFE2E8F0;
    private static final int COLOR_PANEL = 0xFFEFF6FF;

    private final int[] defaultPars = {4, 4, 3, 5, 4, 4, 5, 3, 4, 4, 5, 4, 3, 4, 4, 5, 3, 4};
    private final String[] fwOptions = {"-", "KEEP", "左", "右", "ラフ", "バンカー", "OB", "1ペナ"};
    private final String[] playerCountOptions = {"1名", "2名", "3名", "4名"};

    private EditText dateEdit;
    private EditText courseEdit;
    private EditText teeEdit;
    private EditText startTimeEdit;
    private Spinner playerCountSpinner;
    private LinearLayout playerNamesContainer;
    private LinearLayout holeInputContainer;
    private TextView holeTitleText;
    private TextView holeSubText;
    private TextView summaryText;
    private TextView exportText;
    private TextView saveStatusText;
    private Button prevButton;
    private Button nextButton;

    private String dateText = "";
    private String courseText = "";
    private String teeText = "";
    private String startTimeText = "";
    private int activePlayers = 1;
    private int currentHole = 0;
    private boolean binding = false;

    private final int[] pars = new int[HOLES];
    private final String[] playerNames = {"Player 1", "Player 2", "Player 3", "Player 4"};
    private final int[][] scores = new int[PLAYERS][HOLES];
    private final int[][] putts = new int[PLAYERS][HOLES];
    private final int[][] fwIndexes = new int[PLAYERS][HOLES];
    private final int[][] obs = new int[PLAYERS][HOLES];
    private final int[][] penalties = new int[PLAYERS][HOLES];
    private final String[][] notes = new String[PLAYERS][HOLES];

    private NumberPicker parPicker;
    private final NumberPicker[] scorePickers = new NumberPicker[PLAYERS];
    private final NumberPicker[] puttPickers = new NumberPicker[PLAYERS];
    private final NumberPicker[] obPickers = new NumberPicker[PLAYERS];
    private final NumberPicker[] penaltyPickers = new NumberPicker[PLAYERS];
    private final Spinner[] fwSpinners = new Spinner[PLAYERS];
    private final EditText[] noteEdits = new EditText[PLAYERS];

    private final Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoSaveRunnable = new Runnable() {
        @Override public void run() {
            saveState(false);
            autoSaveHandler.postDelayed(this, AUTO_SAVE_INTERVAL_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initDefaults();
        restoreState();
        setContentView(createContentView());
        renderPlayerNameFields();
        renderHole();
        updateSummary();
        startAutoSaveTimer();
    }

    @Override protected void onPause() {
        saveState(false);
        super.onPause();
    }

    @Override protected void onStop() {
        saveState(false);
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        saveState(false);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        saveState(false);
        super.onDestroy();
    }

    private void initDefaults() {
        System.arraycopy(defaultPars, 0, pars, 0, HOLES);
        if (TextUtils.isEmpty(dateText)) dateText = nowDate();
        for (int p = 0; p < PLAYERS; p++) {
            for (int h = 0; h < HOLES; h++) {
                notes[p][h] = "";
                fwIndexes[p][h] = 0;
            }
        }
    }

    private View createContentView() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(landscape ? 18 : 14), dp(14), dp(landscape ? 18 : 14), dp(18));
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(createHeroCard(landscape));
        root.addView(createRoundCard(landscape));
        root.addView(createPlayerSetupCard());
        root.addView(createHoleNavigatorCard());

        holeInputContainer = new LinearLayout(this);
        holeInputContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(holeInputContainer);

        root.addView(section("集計"));
        summaryText = panel("集計待機中", true);
        root.addView(summaryText);
        root.addView(createButtonArea(landscape));

        saveStatusText = text("保存状態: 起動中", 13, COLOR_MUTED, false);
        saveStatusText.setGravity(Gravity.CENTER_HORIZONTAL);
        saveStatusText.setPadding(0, dp(8), 0, dp(8));
        root.addView(saveStatusText);

        root.addView(section("出力テキスト"));
        exportText = panel("出力内容はここに表示されます。", false);
        root.addView(exportText);

        TextView credit = text("© 株式会社NKテクニカルサポート", 13, COLOR_MUTED, false);
        credit.setGravity(Gravity.CENTER_HORIZONTAL);
        credit.setPadding(0, dp(22), 0, dp(6));
        root.addView(credit);
        return scroll;
    }

    private View createHeroCard(boolean landscape) {
        LinearLayout card = card();
        card.setGravity(landscape ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
        card.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("nk_logo", "drawable", getPackageName()));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(landscape ? 66 : 78), dp(landscape ? 66 : 78));
        logoParams.gravity = landscape ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL;
        logoParams.setMargins(0, 0, landscape ? dp(16) : 0, landscape ? 0 : dp(8));
        card.addView(logo, logoParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(landscape ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
        TextView title = text("NK Score Manager", landscape ? 25 : 27, COLOR_TEXT, true);
        title.setGravity(landscape ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        texts.addView(title);
        TextView sub = text("1Hずつ入力・最大4名・スコア1〜15・自動保存 V1.4", 14, COLOR_MUTED, false);
        sub.setGravity(landscape ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        sub.setPadding(0, dp(4), 0, 0);
        texts.addView(sub);
        TextView badge = text("ROUND SCORE INPUT", 12, COLOR_PRIMARY, true);
        badge.setGravity(landscape ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        badge.setPadding(0, dp(8), 0, 0);
        texts.addView(badge);
        card.addView(texts, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private View createRoundCard(boolean landscape) {
        LinearLayout card = card();
        card.addView(sectionCompact("ラウンド情報"));
        dateEdit = normalInput("日付 例: " + nowDate());
        dateEdit.setText(TextUtils.isEmpty(dateText) ? nowDate() : dateText);
        courseEdit = normalInput("ゴルフ場名");
        courseEdit.setText(courseText);
        teeEdit = normalInput("ティー 例: ブルーティー");
        teeEdit.setText(teeText);
        startTimeEdit = normalInput("スタート時間 / OUT / IN");
        startTimeEdit.setText(startTimeText);

        attachRoundWatcher(dateEdit, value -> dateText = value);
        attachRoundWatcher(courseEdit, value -> courseText = value);
        attachRoundWatcher(teeEdit, value -> teeText = value);
        attachRoundWatcher(startTimeEdit, value -> startTimeText = value);

        if (landscape) {
            card.addView(inputRow(dateEdit, courseEdit));
            card.addView(inputRow(teeEdit, startTimeEdit));
        } else {
            card.addView(dateEdit);
            card.addView(courseEdit);
            card.addView(teeEdit);
            card.addView(startTimeEdit);
        }
        return card;
    }

    private View createPlayerSetupCard() {
        LinearLayout card = card();
        card.addView(sectionCompact("プレイヤー設定（最大4名）"));

        LinearLayout countRow = new LinearLayout(this);
        countRow.setOrientation(LinearLayout.HORIZONTAL);
        countRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView countLabel = text("入力人数", 14, COLOR_TEXT, true);
        countRow.addView(countLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        playerCountSpinner = spinner(playerCountOptions);
        playerCountSpinner.setSelection(bound(activePlayers, 1, PLAYERS) - 1);
        playerCountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (binding) return;
                activePlayers = position + 1;
                renderPlayerNameFields();
                renderHole();
                updateSummary();
                saveState(false);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        countRow.addView(playerCountSpinner, new LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(countRow);

        playerNamesContainer = new LinearLayout(this);
        playerNamesContainer.setOrientation(LinearLayout.VERTICAL);
        playerNamesContainer.setPadding(0, dp(8), 0, 0);
        card.addView(playerNamesContainer);
        return card;
    }

    private View createHoleNavigatorCard() {
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        holeTitleText = text("1H", 25, COLOR_TEXT, true);
        top.addView(holeTitleText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        holeSubText = text("PAR 4", 14, COLOR_MUTED, true);
        holeSubText.setGravity(Gravity.END);
        top.addView(holeSubText);
        card.addView(top);

        TextView help = text("1ホールずつ入力します。前後ボタンでホール移動しても、入力内容は自動保存されます。", 13, COLOR_MUTED, false);
        help.setPadding(0, dp(6), 0, dp(10));
        card.addView(help);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        prevButton = button("前のH", false);
        prevButton.setOnClickListener(v -> moveHole(-1));
        nextButton = button("次のH", true);
        nextButton.setOnClickListener(v -> moveHole(1));
        nav.addView(prevButton, weightedParams(1f, dp(3), dp(3)));
        nav.addView(nextButton, weightedParams(1f, dp(3), dp(3)));
        card.addView(nav);
        return card;
    }

    private void renderPlayerNameFields() {
        if (playerNamesContainer == null) return;
        binding = true;
        playerNamesContainer.removeAllViews();
        for (int i = 0; i < activePlayers; i++) {
            final int player = i;
            EditText edit = normalInput("プレイヤー" + (i + 1) + "名");
            edit.setText(playerNames[i]);
            edit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    playerNames[player] = s == null ? "" : s.toString();
                    updateSummary();
                    saveState(false);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            playerNamesContainer.addView(edit);
        }
        binding = false;
    }

    private void renderHole() {
        if (holeInputContainer == null) return;
        binding = true;
        holeInputContainer.removeAllViews();
        holeTitleText.setText((currentHole + 1) + "H");
        holeSubText.setText((currentHole < 9 ? "OUT" : "IN") + " / PAR " + pars[currentHole]);
        prevButton.setEnabled(currentHole > 0);
        nextButton.setEnabled(currentHole < HOLES - 1);

        LinearLayout parCard = card();
        parCard.addView(sectionCompact("ホール設定"));
        LinearLayout parRow = new LinearLayout(this);
        parRow.setOrientation(LinearLayout.HORIZONTAL);
        parRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView parLabel = text("このホールのPAR", 14, COLOR_TEXT, true);
        parRow.addView(parLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        parPicker = picker(3, 6, pars[currentHole], null, 86);
        parPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            if (binding) return;
            pars[currentHole] = newVal;
            holeSubText.setText((currentHole < 9 ? "OUT" : "IN") + " / PAR " + pars[currentHole]);
            updateSummary();
            saveState(false);
        });
        parRow.addView(parPicker);
        parCard.addView(parRow);
        holeInputContainer.addView(parCard);

        for (int p = 0; p < activePlayers; p++) {
            holeInputContainer.addView(createPlayerHoleCard(p));
        }
        binding = false;
    }

    private View createPlayerHoleCard(int player) {
        LinearLayout card = card();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView title = text(displayPlayerName(player), 18, COLOR_TEXT, true);
        title.setPadding(0, 0, 0, dp(8));
        card.addView(title);

        final int p = player;
        final int h = currentHole;
        scorePickers[p] = picker(0, SCORE_MAX, scores[p][h], zeroDashValues(SCORE_MAX), 92);
        scorePickers[p].setOnValueChangedListener((picker, oldVal, newVal) -> {
            if (binding) return;
            scores[p][h] = newVal;
            updateSummary();
            saveState(false);
        });
        puttPickers[p] = picker(0, 6, putts[p][h], zeroDashValues(6), 72);
        puttPickers[p].setOnValueChangedListener((picker, oldVal, newVal) -> {
            if (binding) return;
            putts[p][h] = newVal;
            updateSummary();
            saveState(false);
        });
        fwSpinners[p] = spinner(fwOptions);
        fwSpinners[p].setSelection(bound(fwIndexes[p][h], 0, fwOptions.length - 1));
        fwSpinners[p].setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (binding) return;
                fwIndexes[p][h] = position;
                updateSummary();
                saveState(false);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        LinearLayout mainRow = new LinearLayout(this);
        mainRow.setOrientation(LinearLayout.HORIZONTAL);
        mainRow.setGravity(Gravity.CENTER_VERTICAL);
        mainRow.addView(pickerBlock("SCORE", scorePickers[p]), weightedParams(1.2f, dp(2), dp(2)));
        mainRow.addView(pickerBlock("Putt", puttPickers[p]), weightedParams(1f, dp(2), dp(2)));
        mainRow.addView(spinnerBlock("FW", fwSpinners[p]), weightedParams(1.4f, dp(2), dp(2)));
        card.addView(mainRow);

        obPickers[p] = picker(0, 5, obs[p][h], zeroDashValues(5), 70);
        obPickers[p].setOnValueChangedListener((picker, oldVal, newVal) -> {
            if (binding) return;
            obs[p][h] = newVal;
            updateSummary();
            saveState(false);
        });
        penaltyPickers[p] = picker(0, 5, penalties[p][h], zeroDashValues(5), 70);
        penaltyPickers[p].setOnValueChangedListener((picker, oldVal, newVal) -> {
            if (binding) return;
            penalties[p][h] = newVal;
            updateSummary();
            saveState(false);
        });
        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        subRow.addView(pickerBlock("OB", obPickers[p]), weightedParams(1f, dp(2), dp(2)));
        subRow.addView(pickerBlock("Pen", penaltyPickers[p]), weightedParams(1f, dp(2), dp(2)));
        card.addView(subRow);

        noteEdits[p] = normalInput("メモ（任意）");
        noteEdits[p].setText(notes[p][h] == null ? "" : notes[p][h]);
        noteEdits[p].addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (binding) return;
                notes[p][h] = s == null ? "" : s.toString();
                saveState(false);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        card.addView(noteEdits[p]);
        return card;
    }

    private void moveHole(int direction) {
        int next = bound(currentHole + direction, 0, HOLES - 1);
        if (next == currentHole) return;
        currentHole = next;
        renderHole();
        updateSummary();
        saveState(false);
    }

    private View createButtonArea(boolean landscape) {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        area.setPadding(0, dp(12), 0, dp(4));

        Button saveButton = button("保存", true);
        saveButton.setOnClickListener(v -> saveState(true));
        Button copyButton = button("出力コピー", false);
        copyButton.setOnClickListener(v -> copyExportText());
        Button clearButton = button("リセット", false);
        clearButton.setOnClickListener(v -> clearInputs());

        area.addView(saveButton, weightedParams(1f, dp(3), dp(3)));
        area.addView(copyButton, weightedParams(1f, dp(3), dp(3)));
        area.addView(clearButton, weightedParams(1f, dp(3), dp(3)));
        return area;
    }

    private void updateSummary() {
        if (summaryText == null || exportText == null) return;
        StringBuilder summary = new StringBuilder();
        summary.append("現在: ").append(currentHole + 1).append("H / PAR ").append(pars[currentHole]).append("\n");
        for (int p = 0; p < activePlayers; p++) {
            int total = 0;
            int parTotal = 0;
            int entered = 0;
            int puttTotal = 0;
            int puttEntered = 0;
            int fwTargets = 0;
            int fwKeep = 0;
            int obTotal = 0;
            int penTotal = 0;
            for (int h = 0; h < HOLES; h++) {
                if (scores[p][h] > 0) {
                    entered++;
                    total += scores[p][h];
                    parTotal += pars[h];
                }
                if (putts[p][h] > 0) {
                    puttEntered++;
                    puttTotal += putts[p][h];
                }
                if (pars[h] >= 4 && fwIndexes[p][h] > 0) {
                    fwTargets++;
                    if ("KEEP".equals(fwOptions[fwIndexes[p][h]])) fwKeep++;
                }
                obTotal += obs[p][h];
                penTotal += penalties[p][h];
            }
            int diff = total - parTotal;
            String diffText = entered == 0 ? "-" : (diff == 0 ? "EVEN" : (diff > 0 ? "+" + diff : String.valueOf(diff)));
            String avgPutt = puttEntered == 0 ? "-" : String.format(Locale.US, "%.2f", puttTotal * 1.0 / puttEntered);
            String fwRate = fwTargets == 0 ? "-" : String.format(Locale.US, "%.1f%%", fwKeep * 100.0 / fwTargets);
            summary.append(displayPlayerName(p)).append("：")
                    .append(entered).append("H入力 / TOTAL ").append(entered == 0 ? "-" : total)
                    .append(" / ").append(diffText)
                    .append(" / Putt ").append(puttTotal).append(" 平均 ").append(avgPutt)
                    .append(" / FW ").append(fwKeep).append("/").append(fwTargets).append(" ").append(fwRate)
                    .append(" / OB ").append(obTotal)
                    .append(" / Pen ").append(penTotal)
                    .append("\n");
        }
        summaryText.setText(summary.toString().trim());
        exportText.setText(buildExportText(summary.toString().trim()));
    }

    private String buildExportText(String summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("NK Score Export V1.4\n");
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
                        .append(" / SCORE:").append(formatScore(scores[p][h]))
                        .append(" / Putt:").append(formatScore(putts[p][h]))
                        .append(" / FW:").append(fwOptions[bound(fwIndexes[p][h], 0, fwOptions.length - 1)])
                        .append(" / OB:").append(formatScore(obs[p][h]))
                        .append(" / Pen:").append(formatScore(penalties[p][h]));
                if (!TextUtils.isEmpty(notes[p][h])) builder.append(" / メモ:").append(notes[p][h]);
                builder.append("\n");
            }
            if (h == 8) builder.append("---------- IN / OUT ----------\n");
        }
        builder.append("====================\n");
        builder.append(summary).append("\n");
        return builder.toString();
    }

    private void saveState(boolean showToast) {
        SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
        editor.putString(KEY_PREFIX + "date", dateText);
        editor.putString(KEY_PREFIX + "course", courseText);
        editor.putString(KEY_PREFIX + "tee", teeText);
        editor.putString(KEY_PREFIX + "start", startTimeText);
        editor.putInt(KEY_PREFIX + "activePlayers", activePlayers);
        editor.putInt(KEY_PREFIX + "currentHole", currentHole);
        editor.putString(KEY_PREFIX + "pars", serializeIntArray(pars));
        editor.putString(KEY_PREFIX + "playerNames", serializeStringArray(playerNames));
        for (int p = 0; p < PLAYERS; p++) {
            editor.putString(KEY_PREFIX + "scores_" + p, serializeIntArray(scores[p]));
            editor.putString(KEY_PREFIX + "putts_" + p, serializeIntArray(putts[p]));
            editor.putString(KEY_PREFIX + "fw_" + p, serializeIntArray(fwIndexes[p]));
            editor.putString(KEY_PREFIX + "obs_" + p, serializeIntArray(obs[p]));
            editor.putString(KEY_PREFIX + "pen_" + p, serializeIntArray(penalties[p]));
            editor.putString(KEY_PREFIX + "notes_" + p, serializeStringArray(notes[p]));
        }
        editor.putString(KEY_PREFIX + "lastSaved", nowFull());
        editor.apply();
        if (saveStatusText != null) saveStatusText.setText("保存状態: 自動保存済み " + nowFull());
        if (showToast) Toast.makeText(this, "保存しました。", Toast.LENGTH_SHORT).show();
    }

    private void restoreState() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        dateText = prefs.getString(KEY_PREFIX + "date", nowDate());
        courseText = prefs.getString(KEY_PREFIX + "course", "");
        teeText = prefs.getString(KEY_PREFIX + "tee", "");
        startTimeText = prefs.getString(KEY_PREFIX + "start", "");
        activePlayers = bound(prefs.getInt(KEY_PREFIX + "activePlayers", 1), 1, PLAYERS);
        currentHole = bound(prefs.getInt(KEY_PREFIX + "currentHole", 0), 0, HOLES - 1);
        restoreIntArray(prefs.getString(KEY_PREFIX + "pars", ""), pars, defaultPars, 3, 6);
        restoreStringArray(prefs.getString(KEY_PREFIX + "playerNames", ""), playerNames);
        for (int p = 0; p < PLAYERS; p++) {
            restoreIntArray(prefs.getString(KEY_PREFIX + "scores_" + p, ""), scores[p], null, 0, SCORE_MAX);
            restoreIntArray(prefs.getString(KEY_PREFIX + "putts_" + p, ""), putts[p], null, 0, 6);
            restoreIntArray(prefs.getString(KEY_PREFIX + "fw_" + p, ""), fwIndexes[p], null, 0, fwOptions.length - 1);
            restoreIntArray(prefs.getString(KEY_PREFIX + "obs_" + p, ""), obs[p], null, 0, 5);
            restoreIntArray(prefs.getString(KEY_PREFIX + "pen_" + p, ""), penalties[p], null, 0, 5);
            restoreStringArray(prefs.getString(KEY_PREFIX + "notes_" + p, ""), notes[p]);
        }
    }

    private void clearInputs() {
        binding = true;
        activePlayers = 1;
        currentHole = 0;
        dateText = nowDate();
        courseText = "";
        teeText = "";
        startTimeText = "";
        System.arraycopy(defaultPars, 0, pars, 0, HOLES);
        for (int p = 0; p < PLAYERS; p++) {
            playerNames[p] = "Player " + (p + 1);
            for (int h = 0; h < HOLES; h++) {
                scores[p][h] = 0;
                putts[p][h] = 0;
                fwIndexes[p][h] = 0;
                obs[p][h] = 0;
                penalties[p][h] = 0;
                notes[p][h] = "";
            }
        }
        dateEdit.setText(dateText);
        courseEdit.setText(courseText);
        teeEdit.setText(teeText);
        startTimeEdit.setText(startTimeText);
        playerCountSpinner.setSelection(0);
        binding = false;
        renderPlayerNameFields();
        renderHole();
        updateSummary();
        saveState(true);
    }

    private void copyExportText() {
        updateSummary();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("NK Score Export", exportText.getText().toString()));
            Toast.makeText(this, "出力テキストをコピーしました。", Toast.LENGTH_SHORT).show();
        }
    }

    private void attachRoundWatcher(EditText editText, TextValueSink sink) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (binding) return;
                sink.set(s == null ? "" : s.toString());
                updateSummary();
                saveState(false);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private LinearLayout pickerBlock(String label, NumberPicker picker) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView labelView = text(label, 11, COLOR_MUTED, true);
        labelView.setGravity(Gravity.CENTER);
        block.addView(labelView);
        block.addView(picker);
        return block;
    }

    private LinearLayout spinnerBlock(String label, Spinner spinner) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView labelView = text(label, 11, COLOR_MUTED, true);
        labelView.setGravity(Gravity.CENTER);
        block.addView(labelView);
        block.addView(spinner, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return block;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setMinimumWidth(dp(88));
        return spinner;
    }

    private NumberPicker picker(int min, int max, int value, String[] displayedValues, int widthDp) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        if (displayedValues != null) picker.setDisplayedValues(displayedValues);
        picker.setValue(bound(value, min, max));
        picker.setWrapSelectorWheel(false);
        picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        picker.setMinimumWidth(dp(widthDp));
        picker.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT));
        picker.setOnLongPressUpdateInterval(110);
        return picker;
    }

    private String[] zeroDashValues(int max) {
        String[] values = new String[max + 1];
        values[0] = "-";
        for (int i = 1; i <= max; i++) values[i] = String.valueOf(i);
        return values;
    }

    private LinearLayout inputRow(View left, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, weightedParams(1f, dp(4), dp(4)));
        row.addView(right, weightedParams(1f, dp(4), dp(4)));
        return row;
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

    private EditText normalInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(0xFF94A3B8);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        return input;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(0xFFFFFFFF);
        button.setAllCaps(false);
        button.setBackgroundResource(getResources().getIdentifier(primary ? "button_bg" : "secondary_button_bg", "drawable", getPackageName()));
        return button;
    }

    private TextView section(String value) {
        TextView view = text(value, 17, COLOR_TEXT, true);
        view.setPadding(dp(2), dp(18), dp(2), dp(8));
        return view;
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

    private void startAutoSaveTimer() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_INTERVAL_MS);
    }

    private String displayPlayerName(int index) {
        String name = playerNames[index];
        return TextUtils.isEmpty(name) ? "Player " + (index + 1) : name.trim();
    }

    private String formatScore(int value) {
        return value == 0 ? "-" : String.valueOf(value);
    }

    private String serializeIntArray(int[] values) {
        ArrayList<String> parts = new ArrayList<>();
        for (int value : values) parts.add(String.valueOf(value));
        return TextUtils.join(",", parts);
    }

    private void restoreIntArray(String saved, int[] target, int[] fallback, int min, int max) {
        if (fallback != null && fallback.length == target.length) {
            System.arraycopy(fallback, 0, target, 0, target.length);
        }
        if (TextUtils.isEmpty(saved)) return;
        String[] parts = saved.split(",", -1);
        for (int i = 0; i < target.length && i < parts.length; i++) {
            target[i] = bound(parseInt(parts[i], target[i]), min, max);
        }
    }

    private String serializeStringArray(String[] values) {
        ArrayList<String> parts = new ArrayList<>();
        for (String value : values) parts.add(encode(value == null ? "" : value));
        return TextUtils.join(",", parts);
    }

    private void restoreStringArray(String saved, String[] target) {
        if (TextUtils.isEmpty(saved)) return;
        String[] parts = saved.split(",", -1);
        for (int i = 0; i < target.length && i < parts.length; i++) {
            target[i] = decode(parts[i]);
        }
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

    private int bound(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String nowDate() {
        return new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date());
    }

    private String nowFull() {
        return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(new Date());
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface TextValueSink {
        void set(String value);
    }
}
