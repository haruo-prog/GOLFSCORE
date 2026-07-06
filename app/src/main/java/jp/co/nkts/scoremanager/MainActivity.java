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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int HOLES = 18;
    private static final long AUTO_SAVE_INTERVAL_MS = 2500L;
    private static final String PREF_NAME = "nk_score_manager_beta";
    private static final String KEY_DRAFT = "draft_text_v13";
    private static final String KEY_ROUNDS = "rounds_text_v13";

    private static final int COLOR_BG = 0xFFF8FAFC;
    private static final int COLOR_CARD = 0xFFFFFFFF;
    private static final int COLOR_PRIMARY = 0xFF166534;
    private static final int COLOR_PRIMARY_SOFT = 0xFFDCFCE7;
    private static final int COLOR_TEXT = 0xFF0F172A;
    private static final int COLOR_MUTED = 0xFF64748B;
    private static final int COLOR_BORDER = 0xFFE2E8F0;

    private final int[] defaultPars = {4, 4, 3, 5, 4, 4, 5, 3, 4, 4, 5, 4, 3, 4, 4, 5, 3, 4};
    private final String[] fwOptions = {"-", "KEEP", "左", "右", "ラフ", "バンカー", "OB", "1ペナ"};

    private EditText dateEdit;
    private EditText courseEdit;
    private EditText teeEdit;
    private EditText playerEdit;
    private EditText startTimeEdit;
    private TextView summaryText;
    private TextView exportText;
    private TextView pastRoundsText;
    private TextView saveStatusText;

    private final NumberPicker[] parPickers = new NumberPicker[HOLES];
    private final NumberPicker[] scorePickers = new NumberPicker[HOLES];
    private final Spinner[] fwSpinners = new Spinner[HOLES];
    private final NumberPicker[] obPickers = new NumberPicker[HOLES];
    private final NumberPicker[] penaltyPickers = new NumberPicker[HOLES];
    private final NumberPicker[] puttPickers = new NumberPicker[HOLES];
    private final EditText[] noteEdits = new EditText[HOLES];

    private final Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private boolean restoring = false;

    private final Runnable autoSaveRunnable = new Runnable() {
        @Override public void run() {
            saveDraft(false);
            autoSaveHandler.postDelayed(this, AUTO_SAVE_INTERVAL_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
        restoreDraft();
        attachWatchers();
        loadPastRounds();
        updateSummary();
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

    @Override protected void onDestroy() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        super.onDestroy();
    }

    private View createContentView() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int pagePadding = landscape ? dp(16) : dp(14);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pagePadding, pagePadding, pagePadding, pagePadding);
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(createHeroCard(landscape));
        root.addView(createRoundInfoCard(landscape));

        TextView inputTitle = section("スコア入力（スクロール式）");
        root.addView(inputTitle);
        TextView help = text("数字は上下スクロールで変更できます。横画面では2列表示になり、入力欄が広く使えます。", 13, COLOR_MUTED, false);
        help.setPadding(dp(2), 0, dp(2), dp(8));
        root.addView(help);

        root.addView(createHoleList(landscape));

        root.addView(section("集計"));
        summaryText = panel("集計待機中", true);
        root.addView(summaryText);

        root.addView(createButtonArea(landscape));

        saveStatusText = text("自動保存: 待機中", 13, COLOR_MUTED, false);
        saveStatusText.setGravity(Gravity.CENTER_HORIZONTAL);
        saveStatusText.setPadding(0, dp(8), 0, dp(8));
        root.addView(saveStatusText);

        root.addView(section("出力テキスト"));
        exportText = panel("出力内容はここに表示されます。", false);
        root.addView(exportText);

        root.addView(section("保存済みラウンド"));
        pastRoundsText = panel("保存済みラウンドはまだありません。", false);
        root.addView(pastRoundsText);

        TextView credit = text("© 株式会社NKテクニカルサポート", 13, COLOR_MUTED, false);
        credit.setGravity(Gravity.CENTER_HORIZONTAL);
        credit.setPadding(0, dp(24), 0, dp(8));
        root.addView(credit);
        return scroll;
    }

    private View createHeroCard(boolean landscape) {
        LinearLayout card = card();
        card.setGravity(landscape ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
        card.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("nk_logo", "drawable", getPackageName()));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(landscape ? 74 : 82), dp(landscape ? 74 : 82));
        logoParams.gravity = landscape ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL;
        logoParams.setMargins(0, 0, landscape ? dp(16) : 0, landscape ? 0 : dp(8));
        card.addView(logo, logoParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(landscape ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);

        TextView title = text("NK Score Manager", landscape ? 26 : 28, COLOR_TEXT, true);
        title.setGravity(landscape ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        texts.addView(title);

        TextView sub = text("縦横レスポンシブ・スクロール入力・自動集計 V1.3", 14, COLOR_MUTED, false);
        sub.setGravity(landscape ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        sub.setPadding(0, dp(4), 0, 0);
        texts.addView(sub);

        TextView badge = text("GOLF ROUND SCORE", 12, COLOR_PRIMARY, true);
        badge.setGravity(landscape ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        badge.setPadding(0, dp(8), 0, 0);
        texts.addView(badge);

        card.addView(texts, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private View createRoundInfoCard(boolean landscape) {
        LinearLayout card = card();
        card.addView(sectionCompact("ラウンド情報"));

        dateEdit = normalInput("日付 例: " + nowDate());
        dateEdit.setText(nowDate());
        courseEdit = normalInput("ゴルフ場名 例: 有馬CC");
        teeEdit = normalInput("ティー 例: ブルーティー");
        playerEdit = normalInput("プレイヤー名");
        startTimeEdit = normalInput("スタート時間 例: 08:35 / OUT / IN");

        if (landscape) {
            card.addView(inputRow(dateEdit, courseEdit));
            card.addView(inputRow(teeEdit, playerEdit));
            card.addView(startTimeEdit);
        } else {
            card.addView(dateEdit);
            card.addView(courseEdit);
            card.addView(teeEdit);
            card.addView(playerEdit);
            card.addView(startTimeEdit);
        }
        return card;
    }

    private LinearLayout inputRow(View left, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, weightedParams(1f, dp(4), dp(4)));
        row.addView(right, weightedParams(1f, dp(4), dp(4)));
        return row;
    }

    private View createHoleList(boolean landscape) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        if (landscape) {
            for (int i = 0; i < HOLES; i += 2) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.addView(createHoleCard(i, true), weightedParams(1f, dp(4), dp(4)));
                if (i + 1 < HOLES) {
                    row.addView(createHoleCard(i + 1, true), weightedParams(1f, dp(4), dp(4)));
                } else {
                    Space spacer = new Space(this);
                    row.addView(spacer, weightedParams(1f, dp(4), dp(4)));
                }
                container.addView(row);
            }
        } else {
            for (int i = 0; i < HOLES; i++) {
                View card = createHoleCard(i, false);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, dp(6), 0, dp(6));
                container.addView(card, params);
            }
        }
        return container;
    }

    private View createHoleCard(int index, boolean compact) {
        LinearLayout card = card();
        card.setPadding(dp(compact ? 10 : 12), dp(12), dp(compact ? 10 : 12), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView hole = text((index + 1) + "H", 20, COLOR_TEXT, true);
        header.addView(hole, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tip = text(index < 9 ? "OUT" : "IN", 12, COLOR_PRIMARY, true);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(dp(10), dp(4), dp(10), dp(4));
        tip.setBackground(rounded(COLOR_PRIMARY_SOFT, COLOR_PRIMARY_SOFT, 999));
        header.addView(tip);
        card.addView(header);

        scorePickers[index] = picker(0, 20, 0, zeroDashValues(20), compact ? 76 : 86);
        parPickers[index] = picker(3, 6, defaultPars[index], null, compact ? 62 : 68);
        puttPickers[index] = picker(0, 6, 0, zeroDashValues(6), compact ? 62 : 68);
        obPickers[index] = picker(0, 5, 0, zeroDashValues(5), compact ? 56 : 62);
        penaltyPickers[index] = picker(0, 5, 0, zeroDashValues(5), compact ? 56 : 62);
        fwSpinners[index] = spinner(fwOptions);
        noteEdits[index] = normalInput("このホールのメモ");
        noteEdits[index].setSingleLine(true);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.addView(pickerBlock("SCORE", scorePickers[index]), weightedParams(1.2f, dp(2), dp(2)));
        topRow.addView(pickerBlock("PAR", parPickers[index]), weightedParams(0.9f, dp(2), dp(2)));
        topRow.addView(spinnerBlock("FW", fwSpinners[index]), weightedParams(1.3f, dp(2), dp(2)));
        card.addView(topRow);

        LinearLayout detailRow = new LinearLayout(this);
        detailRow.setOrientation(LinearLayout.HORIZONTAL);
        detailRow.setGravity(Gravity.CENTER_VERTICAL);
        detailRow.addView(pickerBlock("Putt", puttPickers[index]), weightedParams(1f, dp(2), dp(2)));
        detailRow.addView(pickerBlock("OB", obPickers[index]), weightedParams(1f, dp(2), dp(2)));
        detailRow.addView(pickerBlock("Pen", penaltyPickers[index]), weightedParams(1f, dp(2), dp(2)));
        card.addView(detailRow);

        card.addView(noteEdits[index]);
        return card;
    }

    private View createButtonArea(boolean landscape) {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        area.setPadding(0, dp(12), 0, dp(4));

        Button saveButton = button("ラウンド保存", true);
        saveButton.setOnClickListener(v -> saveRound());
        Button copyButton = button("出力コピー", false);
        copyButton.setOnClickListener(v -> copyExportText());
        Button clearButton = button("入力リセット", false);
        clearButton.setOnClickListener(v -> clearInputs());

        area.addView(saveButton, weightedParams(1f, dp(3), dp(3)));
        area.addView(copyButton, weightedParams(1f, dp(3), dp(3)));
        area.addView(clearButton, weightedParams(1f, dp(3), dp(3)));
        return area;
    }

    private LinearLayout pickerBlock(String label, NumberPicker picker) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView text = text(label, 11, COLOR_MUTED, true);
        text.setGravity(Gravity.CENTER);
        block.addView(text);
        block.addView(picker);
        return block;
    }

    private LinearLayout spinnerBlock(String label, Spinner spinner) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView text = text(label, 11, COLOR_MUTED, true);
        text.setGravity(Gravity.CENTER);
        block.addView(text);
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
        picker.setValue(value);
        picker.setWrapSelectorWheel(false);
        picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        picker.setMinimumWidth(dp(widthDp));
        picker.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT));
        picker.setOnLongPressUpdateInterval(120);
        return picker;
    }

    private String[] zeroDashValues(int max) {
        String[] values = new String[max + 1];
        values[0] = "-";
        for (int i = 1; i <= max; i++) values[i] = String.valueOf(i);
        return values;
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
        view.setBackground(rounded(important ? COLOR_PRIMARY_SOFT : 0xFFE2E8F0, important ? COLOR_PRIMARY_SOFT : COLOR_BORDER, 14));
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

    private void attachWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateSummary(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        dateEdit.addTextChangedListener(watcher);
        courseEdit.addTextChangedListener(watcher);
        teeEdit.addTextChangedListener(watcher);
        playerEdit.addTextChangedListener(watcher);
        startTimeEdit.addTextChangedListener(watcher);
        for (int i = 0; i < HOLES; i++) {
            NumberPicker.OnValueChangeListener numberListener = (picker, oldVal, newVal) -> updateSummary();
            parPickers[i].setOnValueChangedListener(numberListener);
            scorePickers[i].setOnValueChangedListener(numberListener);
            obPickers[i].setOnValueChangedListener(numberListener);
            penaltyPickers[i].setOnValueChangedListener(numberListener);
            puttPickers[i].setOnValueChangedListener(numberListener);
            noteEdits[i].addTextChangedListener(watcher);
            fwSpinners[i].setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateSummary(); }
                @Override public void onNothingSelected(AdapterView<?> parent) { updateSummary(); }
            });
        }
    }

    private void updateSummary() {
        if (restoring || summaryText == null) return;
        int outScore = 0;
        int inScore = 0;
        int totalScore = 0;
        int outPar = 0;
        int inPar = 0;
        int totalPar = 0;
        int playedPar = 0;
        int enteredHoles = 0;
        int putts = 0;
        int puttHoles = 0;
        int fwTargets = 0;
        int fwOk = 0;
        int obTotal = 0;
        int penaltyTotal = 0;

        for (int i = 0; i < HOLES; i++) {
            int par = parPickers[i].getValue();
            int score = scorePickers[i].getValue();
            int putt = puttPickers[i].getValue();
            int ob = obPickers[i].getValue();
            int penalty = penaltyPickers[i].getValue();
            totalPar += par;
            if (i < 9) outPar += par;
            else inPar += par;

            if (score > 0) {
                enteredHoles++;
                totalScore += score;
                playedPar += par;
                if (i < 9) outScore += score;
                else inScore += score;
            }
            obTotal += ob;
            penaltyTotal += penalty;
            if (putt > 0) {
                putts += putt;
                puttHoles++;
            }
            String fw = String.valueOf(fwSpinners[i].getSelectedItem());
            if (par >= 4 && !"-".equals(fw)) {
                fwTargets++;
                if ("KEEP".equals(fw)) fwOk++;
            }
        }

        String scoreDiff = "入力待ち";
        if (enteredHoles > 0) {
            int diff = totalScore - playedPar;
            scoreDiff = diff == 0 ? "EVEN" : (diff > 0 ? "+" + diff : String.valueOf(diff));
        }
        double fwRate = fwTargets > 0 ? (fwOk * 100.0 / fwTargets) : 0.0;
        double avgPutt = puttHoles > 0 ? (putts * 1.0 / puttHoles) : 0.0;

        String summary = "入力ホール: " + enteredHoles + "/18\n"
                + "OUT " + outScore + " / PAR " + outPar + "\n"
                + "IN  " + inScore + " / PAR " + inPar + "\n"
                + "TOTAL " + (enteredHoles > 0 ? String.valueOf(totalScore) : "-") + " / PAR " + totalPar + " / " + scoreDiff + "\n"
                + "FWキープ: " + fwOk + "/" + fwTargets + "（" + String.format(Locale.US, "%.1f", fwRate) + "%）\n"
                + "パット: " + putts + " / 平均 " + String.format(Locale.US, "%.2f", avgPutt) + "\n"
                + "OB: " + obTotal + " / Penalty: " + penaltyTotal;
        summaryText.setText(summary);
        exportText.setText(buildExportText(summary));
    }

    private String buildExportText(String summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("NK Score Export\n");
        builder.append("日付: ").append(value(dateEdit)).append("\n");
        builder.append("コース: ").append(value(courseEdit)).append("\n");
        builder.append("ティー: ").append(value(teeEdit)).append("\n");
        builder.append("プレイヤー: ").append(value(playerEdit)).append("\n");
        builder.append("スタート: ").append(value(startTimeEdit)).append("\n");
        builder.append("====================\n");
        for (int i = 0; i < HOLES; i++) {
            builder.append(i + 1).append("H / PAR:").append(parPickers[i].getValue())
                    .append(" / SCORE:").append(formatPickerValue(scorePickers[i].getValue()))
                    .append(" / FW:").append(fwSpinners[i].getSelectedItem())
                    .append(" / OB:").append(formatPickerValue(obPickers[i].getValue()))
                    .append(" / Pen:").append(formatPickerValue(penaltyPickers[i].getValue()))
                    .append(" / Putt:").append(formatPickerValue(puttPickers[i].getValue()));
            String note = value(noteEdits[i]);
            if (!TextUtils.isEmpty(note)) builder.append(" / メモ:").append(note);
            builder.append("\n");
            if (i == 8) builder.append("---------- IN / OUT ----------\n");
        }
        builder.append("====================\n");
        builder.append(summary).append("\n");
        return builder.toString();
    }

    private String formatPickerValue(int value) {
        return value == 0 ? "-" : String.valueOf(value);
    }

    private void saveRound() {
        updateSummary();
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String old = prefs.getString(KEY_ROUNDS, "");
        String next = nowFull() + "\n" + exportText.getText().toString() + "\n\n" + old;
        if (next.length() > 25000) next = next.substring(0, 25000);
        prefs.edit().putString(KEY_ROUNDS, next).apply();
        loadPastRounds();
        saveDraft(false);
        Toast.makeText(this, "ラウンドを保存しました。", Toast.LENGTH_SHORT).show();
    }

    private void loadPastRounds() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String rounds = prefs.getString(KEY_ROUNDS, "");
        pastRoundsText.setText(TextUtils.isEmpty(rounds) ? "保存済みラウンドはまだありません。" : rounds);
    }

    private void copyExportText() {
        updateSummary();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("NK Score Export", exportText.getText().toString()));
            Toast.makeText(this, "出力テキストをコピーしました。", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearInputs() {
        restoring = true;
        dateEdit.setText(nowDate());
        courseEdit.setText("");
        teeEdit.setText("");
        playerEdit.setText("");
        startTimeEdit.setText("");
        for (int i = 0; i < HOLES; i++) {
            parPickers[i].setValue(defaultPars[i]);
            scorePickers[i].setValue(0);
            fwSpinners[i].setSelection(0);
            obPickers[i].setValue(0);
            penaltyPickers[i].setValue(0);
            puttPickers[i].setValue(0);
            noteEdits[i].setText("");
        }
        restoring = false;
        updateSummary();
        saveDraft(true);
        Toast.makeText(this, "入力をリセットしました。", Toast.LENGTH_SHORT).show();
    }

    private void startAutoSaveTimer() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_INTERVAL_MS);
    }

    private void saveDraft(boolean showToast) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_DRAFT, serialize()).apply();
        if (saveStatusText != null) saveStatusText.setText("自動保存: " + nowFull());
        if (showToast) Toast.makeText(this, "下書きを保存しました。", Toast.LENGTH_SHORT).show();
    }

    private void restoreDraft() {
        restoring = true;
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String draft = prefs.getString(KEY_DRAFT, "");
        if (!TextUtils.isEmpty(draft)) {
            String[] lines = draft.split("\n", -1);
            if (lines.length >= 5) {
                dateEdit.setText(unescape(lines[0]));
                courseEdit.setText(unescape(lines[1]));
                teeEdit.setText(unescape(lines[2]));
                playerEdit.setText(unescape(lines[3]));
                startTimeEdit.setText(unescape(lines[4]));
            }
            for (int i = 0; i < HOLES; i++) {
                int lineIndex = i + 5;
                if (lineIndex >= lines.length) break;
                String[] parts = lines[lineIndex].split("\t", -1);
                if (parts.length >= 7) {
                    parPickers[i].setValue(bound(parseInt(unescape(parts[0]), defaultPars[i]), 3, 6));
                    scorePickers[i].setValue(bound(parseInt(unescape(parts[1]), 0), 0, 20));
                    setSpinnerByValue(fwSpinners[i], unescape(parts[2]));
                    obPickers[i].setValue(bound(parseInt(unescape(parts[3]), 0), 0, 5));
                    penaltyPickers[i].setValue(bound(parseInt(unescape(parts[4]), 0), 0, 5));
                    puttPickers[i].setValue(bound(parseInt(unescape(parts[5]), 0), 0, 6));
                    noteEdits[i].setText(unescape(parts[6]));
                }
            }
        }
        restoring = false;
    }

    private String serialize() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add(escape(value(dateEdit)));
        lines.add(escape(value(courseEdit)));
        lines.add(escape(value(teeEdit)));
        lines.add(escape(value(playerEdit)));
        lines.add(escape(value(startTimeEdit)));
        for (int i = 0; i < HOLES; i++) {
            lines.add(parPickers[i].getValue() + "\t"
                    + scorePickers[i].getValue() + "\t"
                    + escape(String.valueOf(fwSpinners[i].getSelectedItem())) + "\t"
                    + obPickers[i].getValue() + "\t"
                    + penaltyPickers[i].getValue() + "\t"
                    + puttPickers[i].getValue() + "\t"
                    + escape(value(noteEdits[i])));
        }
        return TextUtils.join("\n", lines);
    }

    private void setSpinnerByValue(Spinner spinner, String value) {
        for (int i = 0; i < fwOptions.length; i++) {
            if (fwOptions[i].equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
        spinner.setSelection(0);
    }

    private int parseInt(String text, int fallback) {
        try {
            String clean = text == null ? "" : text.trim();
            if (clean.isEmpty() || "-".equals(clean)) return fallback;
            return Integer.parseInt(clean);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int bound(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String value(EditText editText) {
        if (editText == null || editText.getText() == null) return "";
        return editText.getText().toString().trim();
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\t", " ").replace("\n", " ");
    }

    private String unescape(String text) {
        if (text == null) return "";
        return text.replace("\\\\", "\\");
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
}
