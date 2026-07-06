package jp.co.nkts.scoremanager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private static final String KEY_DRAFT = "draft_text_v12";
    private static final String KEY_ROUNDS = "rounds_text_v12";

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

    private final EditText[] parEdits = new EditText[HOLES];
    private final EditText[] scoreEdits = new EditText[HOLES];
    private final Spinner[] fwSpinners = new Spinner[HOLES];
    private final EditText[] obEdits = new EditText[HOLES];
    private final EditText[] penaltyEdits = new EditText[HOLES];
    private final EditText[] puttEdits = new EditText[HOLES];
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
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF8FAFC);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        scroll.addView(root);

        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("nk_logo", "drawable", getPackageName()));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(86), dp(86));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(logo, logoParams);

        TextView title = text("NK Score Manager", 28, 0xFF0F172A, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        TextView sub = text("18ホール入力・自動集計・履歴保存・テキスト出力 V1.2", 14, 0xFF475569, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        root.addView(section("ラウンド情報"));
        dateEdit = normalInput("日付 例: " + nowDate());
        dateEdit.setText(nowDate());
        courseEdit = normalInput("ゴルフ場名 例: 有馬CC");
        teeEdit = normalInput("ティー 例: ブルーティー");
        playerEdit = normalInput("プレイヤー名");
        startTimeEdit = normalInput("スタート時間 例: 08:35 / OUT / IN");
        root.addView(dateEdit);
        root.addView(courseEdit);
        root.addView(teeEdit);
        root.addView(playerEdit);
        root.addView(startTimeEdit);

        root.addView(section("スコア入力"));
        TextView help = text("横にスクロールできます。PAR・SCORE・PUTTは数字、FWはKEEP/左右/OB等を選択します。", 13, 0xFF64748B, false);
        help.setPadding(0, 0, 0, dp(8));
        root.addView(help);

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(8);
        grid.setPadding(0, dp(6), 0, dp(6));
        addHeader(grid, "H", 44);
        addHeader(grid, "PAR", 64);
        addHeader(grid, "SCORE", 76);
        addHeader(grid, "FW", 118);
        addHeader(grid, "OB", 64);
        addHeader(grid, "Pen", 64);
        addHeader(grid, "Putt", 64);
        addHeader(grid, "メモ", 170);

        for (int i = 0; i < HOLES; i++) {
            addCell(grid, smallLabel(String.valueOf(i + 1), true), 44);
            parEdits[i] = numberInput(String.valueOf(defaultPars[i]), 64);
            scoreEdits[i] = numberInput("", 76);
            fwSpinners[i] = spinner(fwOptions, 118);
            obEdits[i] = numberInput("", 64);
            penaltyEdits[i] = numberInput("", 64);
            puttEdits[i] = numberInput("", 64);
            noteEdits[i] = normalInput("メモ");
            noteEdits[i].setSingleLine(true);
            addCell(grid, parEdits[i], 64);
            addCell(grid, scoreEdits[i], 76);
            addCell(grid, fwSpinners[i], 118);
            addCell(grid, obEdits[i], 64);
            addCell(grid, penaltyEdits[i], 64);
            addCell(grid, puttEdits[i], 64);
            addCell(grid, noteEdits[i], 170);
        }
        horizontal.addView(grid);
        root.addView(horizontal);

        root.addView(section("集計"));
        summaryText = panel("集計待機中");
        root.addView(summaryText);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setPadding(0, dp(12), 0, dp(4));
        Button saveButton = button("このラウンドを履歴保存", true);
        saveButton.setOnClickListener(v -> saveRound());
        Button copyButton = button("出力テキストをコピー", false);
        copyButton.setOnClickListener(v -> copyExportText());
        Button clearButton = button("入力をリセット", false);
        clearButton.setOnClickListener(v -> clearInputs());
        buttons.addView(saveButton);
        buttons.addView(copyButton);
        buttons.addView(clearButton);
        root.addView(buttons);

        saveStatusText = text("自動保存: 待機中", 13, 0xFF64748B, false);
        saveStatusText.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(saveStatusText);

        root.addView(section("出力テキスト"));
        exportText = panel("出力内容はここに表示されます。");
        root.addView(exportText);

        root.addView(section("保存済みラウンド"));
        pastRoundsText = panel("保存済みラウンドはまだありません。");
        root.addView(pastRoundsText);

        TextView credit = text("© 株式会社NKテクニカルサポート", 13, 0xFF64748B, false);
        credit.setGravity(Gravity.CENTER_HORIZONTAL);
        credit.setPadding(0, dp(24), 0, dp(8));
        root.addView(credit);
        return scroll;
    }

    private void addHeader(GridLayout grid, String value, int widthDp) {
        TextView view = smallLabel(value, true);
        view.setBackgroundColor(0xFFDCFCE7);
        addCell(grid, view, widthDp);
    }

    private void addCell(GridLayout grid, View view, int widthDp) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = dp(widthDp);
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        view.setLayoutParams(params);
        grid.addView(view);
    }

    private TextView smallLabel(String value, boolean bold) {
        TextView view = text(value, 14, 0xFF0F172A, bold);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(4), dp(10), dp(4), dp(10));
        return view;
    }

    private Spinner spinner(String[] values, int widthDp) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setMinimumWidth(dp(widthDp));
        return spinner;
    }

    private EditText normalInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        input.setTextColor(0xFF0F172A);
        input.setHintTextColor(0xFF94A3B8);
        return input;
    }

    private EditText numberInput(String value, int widthDp) {
        EditText input = normalInput("");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setGravity(Gravity.CENTER);
        input.setText(value);
        input.setMinWidth(dp(widthDp));
        input.setSelectAllOnFocus(true);
        return input;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(0xFFFFFFFF);
        button.setAllCaps(false);
        button.setBackgroundResource(getResources().getIdentifier(primary ? "button_bg" : "secondary_button_bg", "drawable", getPackageName()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, dp(5));
        button.setLayoutParams(params);
        return button;
    }

    private TextView section(String value) {
        TextView view = text(value, 17, 0xFF0F172A, true);
        view.setPadding(0, dp(18), 0, dp(8));
        return view;
    }

    private TextView panel(String value) {
        TextView view = text(value, 13, 0xFF1E293B, false);
        view.setBackgroundColor(0xFFE2E8F0);
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
            parEdits[i].addTextChangedListener(watcher);
            scoreEdits[i].addTextChangedListener(watcher);
            obEdits[i].addTextChangedListener(watcher);
            penaltyEdits[i].addTextChangedListener(watcher);
            puttEdits[i].addTextChangedListener(watcher);
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
        int putts = 0;
        int puttHoles = 0;
        int fwTargets = 0;
        int fwOk = 0;
        int obTotal = 0;
        int penaltyTotal = 0;

        for (int i = 0; i < HOLES; i++) {
            int par = intFromString(parEdits[i].getText().toString());
            int score = intFromString(scoreEdits[i].getText().toString());
            int putt = intFromString(puttEdits[i].getText().toString());
            int ob = intFromString(obEdits[i].getText().toString());
            int penalty = intFromString(penaltyEdits[i].getText().toString());
            if (i < 9) {
                outPar += par;
                outScore += score;
            } else {
                inPar += par;
                inScore += score;
            }
            totalPar += par;
            totalScore += score;
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

        int diff = totalScore - totalPar;
        double fwRate = fwTargets > 0 ? (fwOk * 100.0 / fwTargets) : 0.0;
        double avgPutt = puttHoles > 0 ? (putts * 1.0 / puttHoles) : 0.0;
        String scoreDiff = diff > 0 ? "+" + diff : String.valueOf(diff);
        if (diff == 0) scoreDiff = "EVEN";

        String summary = "OUT " + outScore + " / PAR " + outPar + "\n"
                + "IN  " + inScore + " / PAR " + inPar + "\n"
                + "TOTAL " + totalScore + " / PAR " + totalPar + " / " + scoreDiff + "\n"
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
            builder.append(i + 1).append("H / PAR:").append(value(parEdits[i]))
                    .append(" / SCORE:").append(value(scoreEdits[i]))
                    .append(" / FW:").append(fwSpinners[i].getSelectedItem())
                    .append(" / OB:").append(value(obEdits[i]))
                    .append(" / Pen:").append(value(penaltyEdits[i]))
                    .append(" / Putt:").append(value(puttEdits[i]));
            String note = value(noteEdits[i]);
            if (!TextUtils.isEmpty(note)) builder.append(" / メモ:").append(note);
            builder.append("\n");
            if (i == 8) builder.append("---------- IN / OUT ----------\n");
        }
        builder.append("====================\n");
        builder.append(summary).append("\n");
        return builder.toString();
    }

    private void saveRound() {
        updateSummary();
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String old = prefs.getString(KEY_ROUNDS, "");
        String next = nowFull() + "\n" + exportText.getText().toString() + "\n\n" + old;
        if (next.length() > 20000) next = next.substring(0, 20000);
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
            parEdits[i].setText(String.valueOf(defaultPars[i]));
            scoreEdits[i].setText("");
            fwSpinners[i].setSelection(0);
            obEdits[i].setText("");
            penaltyEdits[i].setText("");
            puttEdits[i].setText("");
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
                    parEdits[i].setText(unescape(parts[0]));
                    scoreEdits[i].setText(unescape(parts[1]));
                    setSpinnerByValue(fwSpinners[i], unescape(parts[2]));
                    obEdits[i].setText(unescape(parts[3]));
                    penaltyEdits[i].setText(unescape(parts[4]));
                    puttEdits[i].setText(unescape(parts[5]));
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
            lines.add(escape(value(parEdits[i])) + "\t"
                    + escape(value(scoreEdits[i])) + "\t"
                    + escape(String.valueOf(fwSpinners[i].getSelectedItem())) + "\t"
                    + escape(value(obEdits[i])) + "\t"
                    + escape(value(penaltyEdits[i])) + "\t"
                    + escape(value(puttEdits[i])) + "\t"
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

    private int intFromString(String text) {
        try {
            String clean = text == null ? "" : text.trim();
            if (clean.isEmpty()) return 0;
            return Integer.parseInt(clean);
        } catch (Exception ignored) {
            return 0;
        }
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
