package jp.co.nkts.scoremanager;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int HOLES = 18;
    private static final int PLAYERS = 4;
    private static final int MAX_SCORE = 15;
    private static final String PREF = "nk_score_manager_v22";
    private static final String KEY_LANG = "lang";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_CLUBS = "clubs";
    private static final int REQ_BACKUP_CREATE = 1201;
    private static final int REQ_RESTORE_OPEN = 1202;
    private static final int SCREEN_HOME = 0, SCREEN_ROUND = 1, SCREEN_HISTORY = 2, SCREEN_ANALYSIS = 3, SCREEN_SETTINGS = 4;
    private static final int C_BG = 0xFFF8FAFC, C_CARD = 0xFFFFFFFF, C_TEXT = 0xFF0F172A, C_MUTED = 0xFF64748B, C_BORDER = 0xFFE2E8F0, C_PRIMARY = 0xFF166534, C_PRIMARY_DARK = 0xFF14532D, C_SOFT = 0xFFDCFCE7, C_PANEL = 0xFFEFF6FF, C_DANGER = 0xFFFEE2E2;

    private final int[] defaultPars = {4,4,3,5,4,4,5,3,4,4,5,4,3,4,4,5,3,4};
    private final String[] langCodes = {"ja","en","ko","zh","de"};
    private final String[] langNames = {"日本語","English","한국어","中文","Deutsch"};

    private ScrollView scroll;
    private LinearLayout root;
    private int screen = SCREEN_HOME;
    private String lang = "";
    private boolean registration = false;
    private boolean cancelConfirm = false;
    private boolean binding = false;
    private int activePlayers = 1;
    private int currentHole = 0;
    private int tensPendingPlayer = -1;
    private String course = "";
    private String tee = "";
    private String start = "";

    private final int[] pars = new int[HOLES];
    private final String[] names = {"Player 1","Player 2","Player 3","Player 4"};
    private final int[][] scores = new int[PLAYERS][HOLES];
    private final int[] putts = new int[HOLES];
    private final int[] teeResults = new int[HOLES];
    private final String[] teeClubs = new String[HOLES];
    private String selectedDetail = "";

    private TextView progressText;
    private final TextView[] scoreLabels = new TextView[PLAYERS];
    private final Button[] holeButtons = new Button[HOLES];
    private final Button[] puttButtons = new Button[5];
    private final Button[] teeButtons = new Button[6];

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        initDefaults();
        lang = prefs().getString(KEY_LANG, "");
        restoreDraft();
        setContentView(baseView());
        if (TextUtils.isEmpty(lang)) renderLanguageSelect(true);
        else if (registration) renderRound(false);
        else renderHome();
    }

    @Override protected void onPause() { saveDraft(); super.onPause(); }
    @Override protected void onStop() { saveDraft(); super.onStop(); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_BACKUP_CREATE) writeBackupToUri(data.getData());
        else if (requestCode == REQ_RESTORE_OPEN) restoreBackupFromUri(data.getData());
    }

    private View baseView() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(C_BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(18));
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void initDefaults() {
        System.arraycopy(defaultPars, 0, pars, 0, HOLES);
        String first = clubList()[0];
        for (int h = 0; h < HOLES; h++) teeClubs[h] = first;
    }

    private void renderLanguageSelect(boolean firstLaunch) {
        clearRuntimeViews();
        root.removeAllViews();
        root.addView(hero("Language", firstLaunch ? "Select your language" : t("language")));
        LinearLayout c = card();
        c.addView(section(t("language")));
        c.addView(panel(t("language_note"), false));
        for (int i = 0; i < langCodes.length; i++) {
            final String code = langCodes[i];
            Button b = button(langNames[i], code.equals(lang));
            b.setOnClickListener(v -> { lang = code; prefs().edit().putString(KEY_LANG, code).apply(); toast(t("saved")); renderHome(); });
            c.addView(b, full());
        }
        root.addView(c);
        top();
    }

    private void renderHome() {
        clearRuntimeViews();
        screen = SCREEN_HOME;
        registration = false;
        cancelConfirm = false;
        saveDraft();
        root.removeAllViews();
        root.addView(hero("NK Golf Score", t("concept")));
        LinearLayout pitch = card();
        pitch.addView(section(t("why")));
        pitch.addView(panel("The fastest golf score app in the world.\nWorks offline. No ads. No subscription. Just $1.\n\n" + t("value_points"), true));
        root.addView(pitch);

        LinearLayout c = card();
        c.addView(section(t("round")));
        Button startBtn = button(t("start_round"), true);
        startBtn.setOnClickListener(v -> { resetRound(true); renderRound(false); });
        c.addView(startBtn, full());
        if (hasDraft()) {
            Button resume = button(t("resume_round"), false);
            resume.setOnClickListener(v -> renderRound(false));
            c.addView(resume, full());
        }
        Button h = button(t("history"), false); h.setOnClickListener(v -> renderHistory()); c.addView(h, full());
        Button a = button(t("analysis"), false); a.setOnClickListener(v -> renderAnalysis()); c.addView(a, full());
        Button s = button(t("settings"), false); s.setOnClickListener(v -> renderSettings()); c.addView(s, full());
        root.addView(c);
        root.addView(statsCard());
        addNav();
        top();
    }

    private View statsCard() {
        LinearLayout c = card();
        c.addView(section(t("recent")));
        c.addView(panel(statsText(loadHistory()), true));
        return c;
    }

    private void renderRound(boolean keepScroll) {
        int y = keepScroll && scroll != null ? scroll.getScrollY() : 0;
        clearRuntimeViews();
        screen = SCREEN_ROUND;
        registration = true;
        root.removeAllViews();
        root.addView(roundHeader());
        root.addView(roundSettings());
        root.addView(progressCard());
        root.addView(inputCard());
        root.addView(finishCard());
        addLanguageFooter();
        if (keepScroll) restoreScroll(y); else top();
    }

    private void clearRuntimeViews() {
        progressText = null;
        for (int i = 0; i < scoreLabels.length; i++) scoreLabels[i] = null;
        for (int i = 0; i < holeButtons.length; i++) holeButtons[i] = null;
        for (int i = 0; i < puttButtons.length; i++) puttButtons[i] = null;
        for (int i = 0; i < teeButtons.length; i++) teeButtons[i] = null;
    }

    private View roundHeader() {
        LinearLayout c = card();
        TextView t = text((currentHole + 1) + "H  PAR" + pars[currentHole], 30, C_TEXT, true);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(t);
        TextView sub = text(courseOrDefault() + " / " + tee + " / " + start, 13, C_MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(sub);
        return c;
    }

    private View roundSettings() {
        LinearLayout c = card();
        c.addView(section(t("round_settings")));
        EditText courseInput = input(t("course")); courseInput.setText(course); watch(courseInput, v -> course = v); c.addView(courseInput);
        EditText teeInput = input(t("tee")); teeInput.setText(tee); watch(teeInput, v -> tee = v); c.addView(teeInput);
        EditText startInput = input(t("start")); startInput.setText(start); watch(startInput, v -> start = v); c.addView(startInput);
        LinearLayout pc = row(); pc.setGravity(Gravity.CENTER_VERTICAL);
        pc.addView(text(t("players"), 14, C_TEXT, true), weight(1));
        Spinner sp = spinner(new String[]{"1", "2", "3", "4"});
        binding = true; sp.setSelection(activePlayers - 1); binding = false;
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) { if (!binding && activePlayers != pos + 1) { activePlayers = pos + 1; saveDraft(); renderRound(true); } }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        pc.addView(sp, new LinearLayout.LayoutParams(dp(100), LinearLayout.LayoutParams.WRAP_CONTENT));
        c.addView(pc);
        for (int p = 0; p < activePlayers; p++) {
            final int player = p;
            EditText name = input(p == 0 ? "Player1" : t("player") + (p + 1));
            name.setText(names[p]); watch(name, v -> names[player] = v); c.addView(name);
        }
        return c;
    }

    private View progressCard() {
        LinearLayout c = card();
        c.addView(section(t("progress")));
        progressText = panel("", true);
        c.addView(progressText);
        updateProgressOnly();
        for (int r = 0; r < 3; r++) {
            LinearLayout line = row();
            for (int col = 0; col < 6; col++) {
                int h = r * 6 + col;
                Button b = new Button(this);
                b.setText(String.valueOf(h + 1)); b.setTextSize(12); b.setAllCaps(false);
                final int target = h;
                b.setOnClickListener(v -> { currentHole = target; tensPendingPlayer = -1; saveDraft(); renderRound(false); });
                holeButtons[h] = b;
                styleHoleButton(h);
                line.addView(b, weight(1));
            }
            c.addView(line);
        }
        return c;
    }

    private View inputCard() {
        LinearLayout c = card();
        c.addView(section(t("score_input")));
        c.addView(parPicker());
        c.addView(playerInput(0, true));
        for (int p = 1; p < activePlayers; p++) c.addView(playerInput(p, false));
        LinearLayout nav = row();
        Button prev = button(t("prev"), false); prev.setEnabled(currentHole > 0); prev.setOnClickListener(v -> moveHole(-1));
        Button next = button(t("next"), true); next.setEnabled(currentHole < HOLES - 1); next.setOnClickListener(v -> moveHole(1));
        nav.addView(prev, weight(1)); nav.addView(next, weight(1)); c.addView(nav);
        return c;
    }

    private View parPicker() {
        LinearLayout c = lite();
        c.addView(text("PAR", 12, C_MUTED, true));
        LinearLayout r = row();
        for (int par = 3; par <= 6; par++) {
            final int value = par;
            Button b = choice(String.valueOf(par), pars[currentHole] == par);
            b.setOnClickListener(v -> { pars[currentHole] = value; saveDraft(); renderRound(true); });
            r.addView(b, weight(1));
        }
        c.addView(r);
        return c;
    }

    private View playerInput(int player, boolean detail) {
        LinearLayout c = lite();
        scoreLabels[player] = text(scoreLabelText(player), 17, C_TEXT, true);
        c.addView(scoreLabels[player]);
        c.addView(scoreKeypad(player));
        if (detail) {
            c.addView(puttButtonsView());
            c.addView(teeButtonsView());
        }
        return c;
    }

    private String scoreLabelText(int player) {
        String pending = tensPendingPlayer == player ? "  " + t("ten_mode") : "";
        return displayName(player) + "  SCORE " + scoreText(scores[player][currentHole]) + pending;
    }

    private View scoreKeypad(int player) {
        LinearLayout g = new LinearLayout(this); g.setOrientation(LinearLayout.VERTICAL);
        String[][] rows = {{"1","2","3"},{"4","5","6"},{"7","8","9"},{"1+","0"}};
        for (String[] keys : rows) {
            LinearLayout line = row();
            for (String key : keys) {
                Button b = choice(key, false);
                b.setMinHeight(dp(54));
                b.setOnClickListener(v -> handleScoreKey(player, key));
                line.addView(b, weight(1));
            }
            g.addView(line);
        }
        Button clear = button(t("clear"), false);
        clear.setOnClickListener(v -> { scores[player][currentHole] = 0; tensPendingPlayer = -1; saveDraft(); refreshLiveInput(player); });
        g.addView(clear, full());
        return g;
    }

    private void handleScoreKey(int player, String key) {
        if ("1+".equals(key)) {
            tensPendingPlayer = player;
            refreshLiveInput(player);
            return;
        }
        int digit = num(key, -1);
        if (digit < 0) return;
        if (tensPendingPlayer == player) {
            if (digit <= 5) { scores[player][currentHole] = 10 + digit; tensPendingPlayer = -1; }
            else { toast(t("ten_error")); return; }
        } else {
            scores[player][currentHole] = digit == 0 ? 0 : digit;
        }
        saveDraft();
        refreshLiveInput(player);
    }

    private void refreshLiveInput(int changedPlayer) {
        if (scoreLabels[changedPlayer] != null) scoreLabels[changedPlayer].setText(scoreLabelText(changedPlayer));
        if (tensPendingPlayer >= 0 && tensPendingPlayer < scoreLabels.length && tensPendingPlayer != changedPlayer && scoreLabels[tensPendingPlayer] != null) scoreLabels[tensPendingPlayer].setText(scoreLabelText(tensPendingPlayer));
        updateProgressOnly();
        for (int i = 0; i < HOLES; i++) styleHoleButton(i);
        updatePuttStyles();
        updateTeeStyles();
    }

    private View puttButtonsView() {
        LinearLayout g = new LinearLayout(this); g.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("PAT", 12, C_MUTED, true); label.setGravity(Gravity.CENTER_HORIZONTAL); g.addView(label);
        LinearLayout r = row();
        for (int p = 1; p <= 4; p++) {
            final int value = p;
            Button b = choice(p == 4 ? "4+" : String.valueOf(p), putts[currentHole] == p);
            puttButtons[p] = b;
            b.setOnClickListener(v -> { putts[currentHole] = value; saveDraft(); updatePuttStyles(); });
            r.addView(b, weight(1));
        }
        g.addView(r);
        return g;
    }

    private View teeButtonsView() {
        LinearLayout g = new LinearLayout(this); g.setOrientation(LinearLayout.VERTICAL);
        TextView label = text(t("tee_result"), 12, C_MUTED, true); label.setGravity(Gravity.CENTER_HORIZONTAL); g.addView(label);
        Spinner sp = spinner(clubList());
        int sel = 0; String[] clubs = clubList(); for (int i = 0; i < clubs.length; i++) if (clubs[i].equals(teeClubs[currentHole])) sel = i;
        binding = true; sp.setSelection(sel); binding = false;
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if (!binding) { teeClubs[currentHole] = clubList()[pos]; saveDraft(); } } @Override public void onNothingSelected(AdapterView<?> p) {} });
        g.addView(sp);
        LinearLayout r1 = row(); addTee(r1, "FW", 1); addTee(r1, t("left_rough"), 2); addTee(r1, t("right_rough"), 3); g.addView(r1);
        LinearLayout r2 = row(); addTee(r2, t("left_ob"), 4); addTee(r2, t("right_ob"), 5); addTee(r2, "-", 0); g.addView(r2);
        return g;
    }

    private void addTee(LinearLayout row, String label, int value) {
        Button b = choice(label, teeResults[currentHole] == value);
        teeButtons[value] = b;
        b.setOnClickListener(v -> { teeResults[currentHole] = value; saveDraft(); updateTeeStyles(); updateProgressOnly(); });
        row.addView(b, weight(1));
    }

    private void updateProgressOnly() {
        if (progressText != null) progressText.setText("Player1 " + entered(0) + "/18  /  " + t("missing") + " " + missing());
    }

    private void styleHoleButton(int h) {
        Button b = holeButtons[h];
        if (b == null) return;
        b.setTextColor(h == currentHole ? 0xFFFFFFFF : C_TEXT);
        b.setBackground(rounded(h == currentHole ? C_PRIMARY : (holeComplete(h) ? C_SOFT : C_DANGER), C_BORDER, 12));
    }

    private void updatePuttStyles() {
        for (int p = 1; p <= 4; p++) if (puttButtons[p] != null) setChoiceStyle(puttButtons[p], putts[currentHole] == p);
    }

    private void updateTeeStyles() {
        for (int i = 0; i < teeButtons.length; i++) if (teeButtons[i] != null) setChoiceStyle(teeButtons[i], teeResults[currentHole] == i);
    }

    private void setChoiceStyle(Button b, boolean selected) {
        b.setTextColor(selected ? 0xFFFFFFFF : C_TEXT);
        b.setBackground(rounded(selected ? C_PRIMARY : C_CARD, selected ? C_PRIMARY_DARK : C_BORDER, 14));
    }

    private View finishCard() {
        LinearLayout c = card();
        c.addView(section(t("finish")));
        Button save = button(t("save_analysis"), true); save.setOnClickListener(v -> finishRound()); c.addView(save, full());
        Button cancel = button(t("cancel"), false); cancel.setOnClickListener(v -> { cancelConfirm = true; renderRound(true); }); c.addView(cancel, full());
        if (cancelConfirm) {
            c.addView(panel(t("cancel_confirm"), false));
            LinearLayout r = row();
            Button back = button(t("back_input"), true); back.setOnClickListener(v -> { cancelConfirm = false; renderRound(true); });
            Button home = button(t("back_home"), false); home.setOnClickListener(v -> { registration = false; cancelConfirm = false; saveDraft(); renderHome(); });
            r.addView(back, weight(1)); r.addView(home, weight(1)); c.addView(r);
        }
        return c;
    }

    private void finishRound() {
        int m = missing();
        if (m > 0) { toast(t("missing") + ": " + m); updateProgressOnly(); return; }
        RoundRecord r = buildRecord();
        ArrayList<RoundRecord> list = loadHistory(); list.add(0, r); saveHistory(list);
        selectedDetail = r.scoreCard + "\n\n" + r.analysis;
        resetRound(false);
        toast(t("saved"));
        renderHistory();
    }

    private RoundRecord buildRecord() {
        RoundRecord r = new RoundRecord();
        r.time = System.currentTimeMillis(); r.date = nowDate(); r.course = courseOrDefault(); r.tee = tee; r.total = total(0); r.putts = sum(putts); r.fw = countTee(1); r.teeShots = countTeeTargets();
        r.pars = ser(pars); r.scores = ser(scores[0]); r.teeResults = ser(teeResults); r.teeClubs = ser(teeClubs);
        r.scoreCard = scoreCard(); r.analysis = advice();
        return r;
    }

    private String scoreCard() {
        StringBuilder b = new StringBuilder();
        b.append("NK SCORE CARD\n").append(nowDate()).append("  ").append(courseOrDefault()).append("  ").append(tee).append("\n");
        b.append("HOLE  1  2  3  4  5  6  7  8  9 |OUT|10 11 12 13 14 15 16 17 18 |IN |TOTAL\n");
        b.append("PAR  ").append(rowValues(pars, false)).append("\n");
        for (int p = 0; p < activePlayers; p++) b.append(shortName(displayName(p))).append("   ").append(rowValues(scores[p], true)).append("\n");
        b.append("\nPlayer1 PAT: ").append(sum(putts)).append(" / FW: ").append(countTee(1)).append("/").append(countTeeTargets()).append("\n");
        return b.toString();
    }

    private String advice() {
        int left = countTee(2) + countTee(4), right = countTee(3) + countTee(5), ob = countTee(4) + countTee(5);
        StringBuilder b = new StringBuilder();
        b.append(t("today_analysis")).append("\nScore: ").append(total(0)).append(" / PAT: ").append(sum(putts)).append(" / OB: ").append(ob).append("\n");
        if (right > left) b.append(t("right_miss")).append("\n");
        if (left > right) b.append(t("left_miss")).append("\n");
        if (ob >= 2) b.append(t("ob_advice")).append("\n");
        b.append("\n").append(t("club_analysis")).append("\n").append(currentClubStats());
        return b.toString();
    }

    private String currentClubStats() {
        LinkedHashMap<String, int[]> map = new LinkedHashMap<>();
        for (int h = 0; h < HOLES; h++) {
            String club = TextUtils.isEmpty(teeClubs[h]) ? "-" : teeClubs[h];
            int[] a = map.get(club); if (a == null) a = new int[3];
            a[0]++; if (teeResults[h] == 1) a[1]++; if (teeResults[h] == 4 || teeResults[h] == 5) a[2]++;
            map.put(club, a);
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String,int[]> e: map.entrySet()) b.append(e.getKey()).append(" ").append(t("used")).append(e.getValue()[0]).append(" / FW ").append(e.getValue()[1]).append(" / OB ").append(e.getValue()[2]).append("\n");
        return b.toString();
    }

    private void renderHistory() {
        clearRuntimeViews();
        screen = SCREEN_HISTORY; registration = false; saveDraft(); root.removeAllViews();
        root.addView(hero(t("history"), t("history_sub")));
        LinearLayout c = card(); ArrayList<RoundRecord> list = loadHistory();
        if (list.isEmpty()) c.addView(panel(t("no_history"), false));
        for (RoundRecord r: list) {
            LinearLayout item = lite(); item.addView(text(r.date + " " + r.course + " / " + r.total + " / PAT " + r.putts, 14, C_TEXT, true));
            LinearLayout buttons = row();
            Button detail = button(t("detail"), false); detail.setOnClickListener(v -> { selectedDetail = r.scoreCard + "\n\n" + r.analysis; renderHistory(); });
            Button pdf = button("PDF", true); pdf.setOnClickListener(v -> exportPdf(r.scoreCard, r.analysis));
            buttons.addView(detail, weight(1)); buttons.addView(pdf, weight(1)); item.addView(buttons); c.addView(item);
        }
        if (!TextUtils.isEmpty(selectedDetail)) c.addView(panel(selectedDetail, false));
        root.addView(c); addNav(); top();
    }

    private void renderAnalysis() {
        clearRuntimeViews();
        screen = SCREEN_ANALYSIS; registration = false; saveDraft(); root.removeAllViews();
        root.addView(hero(t("analysis"), t("analysis_sub")));
        ArrayList<RoundRecord> list = loadHistory();
        LinearLayout c = card(); c.addView(panel(statsText(list) + "\n\n" + aggregateClubAnalysis(list), true));
        root.addView(c); addNav(); top();
    }

    private void renderSettings() {
        clearRuntimeViews();
        screen = SCREEN_SETTINGS; registration = false; saveDraft(); root.removeAllViews();
        root.addView(hero(t("settings_short"), t("settings_sub")));
        LinearLayout c = card();
        c.addView(section(t("club_set")));
        EditText clubs = input("DR,3W,5W,UT,5I,6I..."); clubs.setText(TextUtils.join(",", clubList())); c.addView(clubs);
        Button save = button(t("save"), true); save.setOnClickListener(v -> { prefs().edit().putString(KEY_CLUBS, clubs.getText().toString()).apply(); toast(t("saved")); }); c.addView(save, full());
        c.addView(section(t("backup")));
        c.addView(panel(t("backup_note"), false));
        Button backup = button(t("backup_save"), false); backup.setOnClickListener(v -> createBackupDocument()); c.addView(backup, full());
        Button restore = button(t("restore_backup"), true); restore.setOnClickListener(v -> openBackupDocument()); c.addView(restore, full());
        root.addView(c); addNav(); top();
    }

    private String statsText(ArrayList<RoundRecord> list) {
        if (list.isEmpty()) return t("no_data");
        int total = 0, putt = 0, fw = 0, tee = 0;
        for (RoundRecord r: list) { total += r.total; putt += r.putts; fw += r.fw; tee += r.teeShots; }
        return "Rounds " + list.size() + " / AVG " + one(total * 1.0 / list.size()) + " / PAT " + one(putt * 1.0 / list.size()) + " / FW " + pct(fw, tee);
    }

    private String aggregateClubAnalysis(ArrayList<RoundRecord> list) {
        if (list.isEmpty()) return t("club_analysis") + ": " + t("no_data");
        LinkedHashMap<String,int[]> map = new LinkedHashMap<>();
        for (RoundRecord r: list) {
            String[] clubs = deserStr(r.teeClubs, HOLES, "-"); int[] res = deserInt(r.teeResults, HOLES, 0);
            for (int h = 0; h < HOLES; h++) {
                String club = clubs[h]; if (TextUtils.isEmpty(club) || "-".equals(club)) continue;
                int[] a = map.get(club); if (a == null) a = new int[3];
                a[0]++; if (res[h] == 1) a[1]++; if (res[h] == 4 || res[h] == 5) a[2]++;
                map.put(club, a);
            }
        }
        StringBuilder b = new StringBuilder(t("club_analysis") + "\n");
        for (Map.Entry<String,int[]> e: map.entrySet()) b.append(e.getKey()).append(" ").append(t("used")).append(e.getValue()[0]).append(" / FW ").append(pct(e.getValue()[1], e.getValue()[0])).append(" / OB ").append(e.getValue()[2]).append("\n");
        return b.toString();
    }

    private void exportPdf(String scoreCard, String analysis) {
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS); if (dir == null) dir = getFilesDir(); if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "NKScore_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf");
            FileOutputStream out = new FileOutputStream(file); writePdf(scoreCard + "\n\n" + analysis, out); out.close(); toast(t("pdf_saved"));
        } catch(Exception e) { toast(t("pdf_failed")); }
    }

    private void writePdf(String content, OutputStream out) throws Exception {
        PdfDocument pdf = new PdfDocument(); Paint p = new Paint(); p.setAntiAlias(true); p.setTextSize(10f); Paint title = new Paint(); title.setAntiAlias(true); title.setTextSize(18f); title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        int w=842,h=595,m=28,y=m,pageNo=1; PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(w,h,pageNo).create()); Canvas c = page.getCanvas(); c.drawText("NK Golf Score", m, y, title); y += 24;
        for(String line:content.split("\n",-1)) for(String part:wrap(line,115)) { if(y>h-m){ pdf.finishPage(page); page=pdf.startPage(new PdfDocument.PageInfo.Builder(w,h,++pageNo).create()); c=page.getCanvas(); y=m; } c.drawText(part,m,y,p); y+=14; }
        pdf.finishPage(page); pdf.writeTo(out); pdf.close();
    }

    private void createBackupDocument() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, "NKGolfScore_Backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt");
        startActivityForResult(i, REQ_BACKUP_CREATE);
    }

    private void openBackupDocument() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        startActivityForResult(i, REQ_RESTORE_OPEN);
    }

    private void writeBackupToUri(Uri uri) {
        try {
            saveDraft();
            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new Exception("openOutputStream failed");
            out.write(buildBackupText().getBytes(StandardCharsets.UTF_8));
            out.close();
            toast(t("backup_saved"));
        } catch (Exception e) {
            toast(t("backup_failed"));
        }
    }

    private void restoreBackupFromUri(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new Exception("openInputStream failed");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int n;
            while ((n = in.read(data)) > 0) buffer.write(data, 0, n);
            in.close();
            applyBackupText(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            initDefaults();
            lang = prefs().getString(KEY_LANG, "en");
            restoreDraft();
            toast(t("restore_done"));
            renderHome();
        } catch (Exception e) {
            toast(t("restore_failed"));
        }
    }

    private String buildBackupText() {
        StringBuilder b = new StringBuilder("NK_GOLF_SCORE_BACKUP_V24\n");
        Map<String, ?> all = prefs().getAll();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            Object v = e.getValue();
            String key = enc(e.getKey());
            if (v instanceof String) b.append("S|").append(key).append("|").append(enc((String) v)).append("\n");
            else if (v instanceof Integer) b.append("I|").append(key).append("|").append(v).append("\n");
            else if (v instanceof Boolean) b.append("B|").append(key).append("|").append(v).append("\n");
            else if (v instanceof Long) b.append("L|").append(key).append("|").append(v).append("\n");
        }
        return b.toString();
    }

    private void applyBackupText(String raw) throws Exception {
        if (TextUtils.isEmpty(raw) || !(raw.startsWith("NK_GOLF_SCORE_BACKUP_V24") || raw.startsWith("NK_GOLF_SCORE_BACKUP_V23"))) throw new Exception("invalid backup");
        SharedPreferences.Editor e = prefs().edit();
        e.clear();
        for (String line : raw.split("\n", -1)) {
            if (TextUtils.isEmpty(line) || line.startsWith("NK_GOLF_SCORE_BACKUP_")) continue;
            String[] p = line.split("\\|", -1);
            if (p.length < 3) continue;
            String key = dec(p[1]);
            if ("S".equals(p[0])) e.putString(key, dec(p[2]));
            else if ("I".equals(p[0])) e.putInt(key, num(p[2], 0));
            else if ("B".equals(p[0])) e.putBoolean(key, Boolean.parseBoolean(p[2]));
            else if ("L".equals(p[0])) e.putLong(key, longNum(p[2], 0L));
        }
        e.apply();
    }

    private void saveDraft() {
        SharedPreferences.Editor e = prefs().edit();
        e.putString(KEY_LANG, lang); e.putBoolean("registration", registration); e.putString("course", course); e.putString("tee", tee); e.putString("start", start); e.putInt("players", activePlayers); e.putInt("hole", currentHole); e.putString("pars", ser(pars)); e.putString("names", ser(names)); e.putString("putts", ser(putts)); e.putString("teeResults", ser(teeResults)); e.putString("teeClubs", ser(teeClubs));
        for (int p = 0; p < PLAYERS; p++) e.putString("scores" + p, ser(scores[p]));
        e.apply();
    }

    private void restoreDraft() {
        SharedPreferences p = prefs(); registration = p.getBoolean("registration", false); course = p.getString("course", ""); tee = p.getString("tee", ""); start = p.getString("start", ""); activePlayers = bound(p.getInt("players", 1), 1, PLAYERS); currentHole = bound(p.getInt("hole", 0), 0, HOLES - 1);
        restoreInt(p.getString("pars", ""), pars, defaultPars, 3, 6); restoreStr(p.getString("names", ""), names); restoreInt(p.getString("putts", ""), putts, null, 0, 8); restoreInt(p.getString("teeResults", ""), teeResults, null, 0, 5); restoreStr(p.getString("teeClubs", ""), teeClubs);
        for (int i = 0; i < HOLES; i++) if (TextUtils.isEmpty(teeClubs[i])) teeClubs[i] = clubList()[0];
        for (int i = 0; i < PLAYERS; i++) restoreInt(p.getString("scores" + i, ""), scores[i], null, 0, MAX_SCORE);
    }

    private void resetRound(boolean keep) {
        course = ""; tee = ""; start = ""; activePlayers = 1; currentHole = 0; tensPendingPlayer = -1; cancelConfirm = false; registration = keep;
        System.arraycopy(defaultPars,0,pars,0,HOLES); for (int p=0;p<PLAYERS;p++){ names[p]="Player "+(p+1); for(int h=0;h<HOLES;h++) scores[p][h]=0; }
        for (int h=0;h<HOLES;h++){ putts[h]=0; teeResults[h]=0; teeClubs[h]=clubList()[0]; }
        saveDraft();
    }

    private ArrayList<RoundRecord> loadHistory(){ ArrayList<RoundRecord> list = new ArrayList<>(); String raw = prefs().getString(KEY_HISTORY, ""); if(TextUtils.isEmpty(raw)) return list; for(String line: raw.split("\n", -1)){ RoundRecord r = RoundRecord.fromLine(line); if(r != null) list.add(r); } return list; }
    private void saveHistory(ArrayList<RoundRecord> list){ ArrayList<String> lines = new ArrayList<>(); for(int i=0;i<list.size() && i<300;i++) lines.add(list.get(i).toLine()); prefs().edit().putString(KEY_HISTORY, TextUtils.join("\n", lines)).apply(); }
    private String[] clubList(){ String raw = prefs().getString(KEY_CLUBS, "DR,3W,5W,UT,5I,6I,7I,8I,9I,PW,50,56,60,PT"); ArrayList<String> out = new ArrayList<>(); for(String s: raw.split(",")){ String v=s.trim(); if(!TextUtils.isEmpty(v)) out.add(v); } if(out.isEmpty()) out.add("DR"); return out.toArray(new String[0]); }
    private boolean hasDraft(){ if(!TextUtils.isEmpty(course)||!TextUtils.isEmpty(tee)||!TextUtils.isEmpty(start)) return true; for(int p=0;p<PLAYERS;p++) for(int h=0;h<HOLES;h++) if(scores[p][h]>0) return true; return false; }
    private boolean holeComplete(int h){ for(int p=0;p<activePlayers;p++) if(scores[p][h]==0) return false; return true; }
    private int missing(){ int m=0; for(int p=0;p<activePlayers;p++) for(int h=0;h<HOLES;h++) if(scores[p][h]==0) m++; return m; }
    private int entered(int p){ int n=0; for(int h=0;h<HOLES;h++) if(scores[p][h]>0)n++; return n; }
    private int countTee(int code){ int n=0; for(int v: teeResults) if(v==code)n++; return n; }
    private int countTeeTargets(){ int n=0; for(int v: teeResults) if(v>0)n++; return n; }
    private int total(int p){ int t=0; for(int h=0;h<HOLES;h++) t += scores[p][h]; return t; }
    private int sum(int[] a){ int t=0; for(int v:a)t+=v; return t; }
    private void moveHole(int d){ currentHole=bound(currentHole+d,0,HOLES-1); tensPendingPlayer=-1; saveDraft(); renderRound(false); }
    private String rowValues(int[] values, boolean hideZero){ StringBuilder b=new StringBuilder(); int out=0,in=0,total=0; for(int h=0;h<HOLES;h++){int v=values[h]; b.append(hideZero&&v==0?" - ":pad2(v)); if(h<9)out+=v;else in+=v; total+=v; if(h==8)b.append("|").append(pad2(out)).append("|");} b.append("|").append(pad2(in)).append("|").append(pad3(total)); return b.toString(); }

    private void addNav(){ LinearLayout n=row(); Button h=button(t("home"),screen==SCREEN_HOME);h.setOnClickListener(v->renderHome()); Button hi=button(t("history"),screen==SCREEN_HISTORY);hi.setOnClickListener(v->renderHistory()); Button a=button(t("analysis"),screen==SCREEN_ANALYSIS);a.setOnClickListener(v->renderAnalysis()); Button s=button(t("settings_short"),screen==SCREEN_SETTINGS);s.setOnClickListener(v->renderSettings()); n.addView(h,weight(1)); n.addView(hi,weight(1)); n.addView(a,weight(1)); n.addView(s,weight(1)); root.addView(n); addLanguageFooter(); }
    private void addLanguageFooter(){ LinearLayout c=card(); c.addView(section(t("language"))); LinearLayout r=row(); for(int i=0;i<langCodes.length;i++){ final String code=langCodes[i]; Button b=button(langNames[i], code.equals(lang)); b.setTextSize(11); b.setOnClickListener(v->{ lang=code; prefs().edit().putString(KEY_LANG,code).apply(); toast(t("saved")); if(screen==SCREEN_HOME)renderHome(); else if(screen==SCREEN_ROUND)renderRound(true); else if(screen==SCREEN_HISTORY)renderHistory(); else if(screen==SCREEN_ANALYSIS)renderAnalysis(); else renderSettings(); }); r.addView(b,weight(1)); } c.addView(r); root.addView(c); }
    private View hero(String title,String sub){ LinearLayout c=card(); c.setGravity(Gravity.CENTER_HORIZONTAL); ImageView logo=new ImageView(this); logo.setImageResource(getResources().getIdentifier("ic_launcher","drawable",getPackageName())); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(68),dp(68)); lp.gravity=Gravity.CENTER_HORIZONTAL; c.addView(logo,lp); TextView t=text(title,25,C_TEXT,true);t.setGravity(Gravity.CENTER_HORIZONTAL);c.addView(t);TextView st=text(sub,13,C_MUTED,false);st.setGravity(Gravity.CENTER_HORIZONTAL);c.addView(st); return c; }
    private LinearLayout card(){ LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(14),dp(14),dp(14),dp(14)); v.setBackground(rounded(C_CARD,C_BORDER,18)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0,dp(6),0,dp(6)); v.setLayoutParams(p); return v; }
    private LinearLayout lite(){ LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(10),dp(10),dp(10),dp(10)); v.setBackground(rounded(0xFFF8FAFC,C_BORDER,16)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0,dp(5),0,dp(5)); v.setLayoutParams(p); return v; }
    private LinearLayout row(){ LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r; }
    private GradientDrawable rounded(int fill,int stroke,int radius){ GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(1),stroke); return g; }
    private TextView section(String s){ TextView v=text(s,16,C_TEXT,true); v.setPadding(0,0,0,dp(8)); return v; }
    private TextView panel(String s,boolean imp){ TextView v=text(s,imp?15:13,C_TEXT,imp); v.setBackground(rounded(imp?C_SOFT:C_PANEL,imp?C_SOFT:C_BORDER,14)); v.setPadding(dp(12),dp(12),dp(12),dp(12)); return v; }
    private TextView text(String s,int size,int color,boolean bold){ TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); if(bold)v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private Button button(String label,boolean primary){ Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(0xFFFFFFFF); b.setMinHeight(dp(48)); b.setBackgroundResource(getResources().getIdentifier(primary?"button_bg":"secondary_button_bg","drawable",getPackageName())); return b; }
    private Button choice(String label,boolean selected){ Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setTextColor(selected?0xFFFFFFFF:C_TEXT); b.setMinHeight(dp(50)); b.setBackground(rounded(selected?C_PRIMARY:C_CARD,selected?C_PRIMARY_DARK:C_BORDER,14)); return b; }
    private EditText input(String hint){ EditText e=new EditText(this); e.setHint(hint); e.setTextSize(14); e.setSingleLine(true); e.setTextColor(C_TEXT); e.setHintTextColor(0xFF94A3B8); e.setPadding(dp(8),dp(6),dp(8),dp(6)); return e; }
    private Spinner spinner(String[] values){ Spinner s=new Spinner(this); ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values); a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(a); return s; }
    private void watch(EditText e, Sink sink){ e.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ sink.set(s==null?"":s.toString()); saveDraft(); } public void afterTextChanged(Editable e){} }); }
    private LinearLayout.LayoutParams full(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0,dp(5),0,dp(5)); return p; }
    private LinearLayout.LayoutParams weight(float w){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,w); p.setMargins(dp(2),dp(2),dp(2),dp(2)); return p; }

    private String t(String k){ String l=TextUtils.isEmpty(lang)?"en":lang; if("ja".equals(l))return ja(k); if("ko".equals(l))return ko(k); if("zh".equals(l))return zh(k); if("de".equals(l))return de(k); return en(k); }
    private String en(String k){ switch(k){case"concept":return"The fastest golf score app. Works offline.";case"why":return"Why choose this app?";case"value_points":return"Offline / No ads / No subscription / No account / Fewer taps";case"language":return"Language";case"language_note":return"Choose on first launch. You can change it at the bottom anytime.";case"round":return"Round";case"start_round":return"Start Round";case"resume_round":return"Resume Round";case"history":return"History";case"analysis":return"Analysis";case"settings":return"Settings / Backup";case"settings_short":return"Settings";case"recent":return"Recent Stats";case"round_settings":return"Round Settings";case"course":return"Course";case"tee":return"Tee";case"start":return"Start time / OUT / IN";case"players":return"Players";case"player":return"Player";case"progress":return"Progress";case"missing":return"Missing";case"score_input":return"Score Input";case"prev":return"Prev Hole";case"next":return"Next Hole";case"ten_mode":return"10s mode: press 0-5";case"ten_error":return"10s mode supports 10-15 only.";case"clear":return"Clear";case"tee_result":return"Tee / Club / Result";case"left_rough":return"Left Rough";case"right_rough":return"Right Rough";case"left_ob":return"Left OB";case"right_ob":return"Right OB";case"finish":return"Finish";case"save_analysis":return"Save and View Analysis";case"cancel":return"Cancel / End";case"cancel_confirm":return"End score entry? You can return to input if this was a mistake.";case"back_input":return"Back to Input";case"back_home":return"Back Home";case"history_sub":return"Scorecards and PDF";case"analysis_sub":return"Club and trend analysis";case"settings_sub":return"Clubs and backup";case"club_set":return"Club Set";case"save":return"Save";case"backup":return"Backup / Restore";case"backup_note":return"Save a backup file to Google Drive, then restore from that file on this or another device.";case"backup_save":return"Save Backup";case"restore_backup":return"Restore from Backup";case"backup_saved":return"Backup saved";case"backup_failed":return"Backup failed";case"restore_done":return"Restored";case"restore_failed":return"Restore failed";case"no_history":return"No history yet.";case"detail":return"Detail";case"no_data":return"No data";case"club_analysis":return"Club Analysis";case"today_analysis":return"Today's Analysis";case"right_miss":return"Right-side misses are frequent.";case"left_miss":return"Left-side misses are frequent.";case"ob_advice":return"Multiple OBs. Consider clubbing down on tight holes.";case"used":return"used ";case"course_empty":return"No course";case"saved":return"Saved";case"pdf_saved":return"PDF saved";case"pdf_failed":return"PDF failed";}return k;}
    private String ja(String k){ switch(k){case"concept":return"世界最速・オフライン対応のゴルフスコアアプリ";case"why":return"このアプリを選ぶ理由";case"value_points":return"オフライン / 広告なし / サブスクなし / アカウント不要 / 少ないタップ";case"language":return"言語選択";case"language_note":return"初回起動時に選択します。後から最下部で変更できます。";case"round":return"ラウンド";case"start_round":return"ラウンド開始";case"resume_round":return"入力中のラウンドに戻る";case"history":return"履歴";case"analysis":return"分析";case"settings":return"設定・バックアップ";case"settings_short":return"設定";case"recent":return"最近の成績";case"round_settings":return"ラウンド設定";case"course":return"コース名";case"tee":return"ティー";case"start":return"スタート時間 / OUT / IN";case"players":return"人数";case"player":return"同伴者";case"progress":return"進捗";case"missing":return"未入力";case"score_input":return"スコア入力";case"prev":return"前のH";case"next":return"次のH";case"ten_mode":return"10台入力中：0〜5を押す";case"ten_error":return"10台は10〜15のみです。";case"clear":return"未入力に戻す";case"tee_result":return"Tee / クラブ / 結果";case"left_rough":return"左ラフ";case"right_rough":return"右ラフ";case"left_ob":return"左OB";case"right_ob":return"右OB";case"finish":return"終了";case"save_analysis":return"保存して分析を見る";case"cancel":return"キャンセル終了";case"cancel_confirm":return"スコア登録を終了しますか？誤って押した場合は入力画面へ戻れます。";case"back_input":return"登録画面へ戻る";case"back_home":return"ホームへ戻る";case"history_sub":return"スコアカードとPDF";case"analysis_sub":return"クラブ別・傾向分析";case"settings_sub":return"クラブとバックアップ";case"club_set":return"クラブセット";case"save":return"保存";case"backup":return"バックアップ / 復元";case"backup_note":return"Google Driveなどにバックアップファイルを保存し、この端末または別端末でそのファイルから復元できます。";case"backup_save":return"バックアップ保存";case"restore_backup":return"バックアップから復元";case"backup_saved":return"バックアップ保存しました";case"backup_failed":return"バックアップ失敗";case"restore_done":return"復元しました";case"restore_failed":return"復元失敗";case"no_history":return"履歴はまだありません。";case"detail":return"詳細";case"no_data":return"データなし";case"club_analysis":return"クラブ別分析";case"today_analysis":return"今日の自動分析";case"right_miss":return"右方向ミスが多い傾向です。";case"left_miss":return"左方向ミスが多い傾向です。";case"ob_advice":return"OBが複数回あります。狭いホールでは番手を落とす判断が有効です。";case"used":return"使用";case"course_empty":return"未入力コース";case"saved":return"保存しました";case"pdf_saved":return"PDF保存しました";case"pdf_failed":return"PDF保存失敗";}return en(k);}
    private String ko(String k){ switch(k){case"language":return"언어 선택";case"concept":return"세계에서 가장 빠른 오프라인 골프 스코어 앱";case"start_round":return"라운드 시작";case"history":return"기록";case"analysis":return"분석";case"settings_short":return"설정";case"score_input":return"스코어 입력";case"save_analysis":return"저장하고 분석 보기";case"cancel":return"취소 종료";case"back_home":return"홈으로";case"backup":return"백업 / 복원";case"backup_save":return"백업 저장";case"restore_backup":return"백업에서 복원";case"backup_saved":return"백업 저장 완료";case"restore_done":return"복원 완료";case"saved":return"저장했습니다";}return en(k);}
    private String zh(String k){ switch(k){case"language":return"语言选择";case"concept":return"世界最快的离线高尔夫记分应用";case"start_round":return"开始球局";case"history":return"历史";case"analysis":return"分析";case"settings_short":return"设置";case"score_input":return"成绩输入";case"save_analysis":return"保存并查看分析";case"cancel":return"取消结束";case"back_home":return"返回主页";case"backup":return"备份 / 恢复";case"backup_save":return"保存备份";case"restore_backup":return"从备份恢复";case"backup_saved":return"备份已保存";case"restore_done":return"已恢复";case"saved":return"已保存";}return en(k);}
    private String de(String k){ switch(k){case"language":return"Sprache";case"concept":return"Die schnellste Offline-Golfscore-App";case"start_round":return"Runde starten";case"history":return"Verlauf";case"analysis":return"Analyse";case"settings_short":return"Einstellungen";case"score_input":return"Score-Eingabe";case"save_analysis":return"Speichern und Analyse";case"cancel":return"Abbrechen / Ende";case"back_home":return"Zur Startseite";case"backup":return"Backup / Wiederherstellen";case"backup_save":return"Backup speichern";case"restore_backup":return"Backup wiederherstellen";case"backup_saved":return"Backup gespeichert";case"restore_done":return"Wiederhergestellt";case"saved":return"Gespeichert";}return en(k);}

    private String displayName(int p){ return TextUtils.isEmpty(names[p])?"Player "+(p+1):names[p].trim(); }
    private String courseOrDefault(){ return TextUtils.isEmpty(course)?t("course_empty"):course; }
    private String scoreText(int v){ return v<=0?"-":String.valueOf(v); }
    private String shortName(String s){ if(TextUtils.isEmpty(s))return"P   "; String n=s.length()>4?s.substring(0,4):s; while(n.length()<4)n+=" "; return n; }
    private String pad2(int v){ if(v<=0)return" - "; return v<10?" "+v+" ":v+" "; }
    private String pad3(int v){ if(v<10)return"  "+v; if(v<100)return" "+v; return String.valueOf(v); }
    private String pct(int a,int b){ return b==0?"-":one(a*100.0/b)+"%"; }
    private String one(double d){ return String.format(Locale.US,"%.1f",d); }
    private int bound(int v,int min,int max){ return Math.max(min,Math.min(max,v)); }
    private int num(String s,int f){ try{return Integer.parseInt(s.trim());}catch(Exception e){return f;} }
    private long longNum(String s,long f){ try{return Long.parseLong(s.trim());}catch(Exception e){return f;} }
    private String nowDate(){ return new SimpleDateFormat("yyyy/MM/dd",Locale.US).format(new Date()); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private SharedPreferences prefs(){ return getSharedPreferences(PREF,MODE_PRIVATE); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    private void top(){ if(scroll!=null)scroll.post(()->scroll.fullScroll(View.FOCUS_UP)); }
    private void restoreScroll(int y){ if(scroll!=null)scroll.post(()->scroll.scrollTo(0,y)); }
    private ArrayList<String> wrap(String line,int max){ ArrayList<String> r=new ArrayList<>(); if(line==null)line=""; if(line.length()==0){r.add("");return r;} for(int i=0;i<line.length();i+=max)r.add(line.substring(i,Math.min(line.length(),i+max))); return r; }
    private String enc(String s){ return Base64.encodeToString((s==null?"":s).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP); }
    private String dec(String s){ try{return new String(Base64.decode(s,Base64.NO_WRAP),StandardCharsets.UTF_8);}catch(Exception e){return"";} }
    private String ser(int[] a){ ArrayList<String> l=new ArrayList<>(); for(int v:a)l.add(String.valueOf(v)); return TextUtils.join(",",l); }
    private String ser(String[] a){ ArrayList<String> l=new ArrayList<>(); for(String s:a)l.add(enc(s)); return TextUtils.join(",",l); }
    private int[] deserInt(String raw,int len,int fb){ int[] a=new int[len]; for(int i=0;i<len;i++)a[i]=fb; if(TextUtils.isEmpty(raw))return a; String[] p=raw.split(",",-1); for(int i=0;i<len&&i<p.length;i++)a[i]=num(p[i],fb); return a; }
    private String[] deserStr(String raw,int len,String fb){ String[] a=new String[len]; for(int i=0;i<len;i++)a[i]=fb; if(TextUtils.isEmpty(raw))return a; String[] p=raw.split(",",-1); for(int i=0;i<len&&i<p.length;i++)a[i]=dec(p[i]); return a; }
    private void restoreInt(String raw,int[] target,int[] fb,int min,int max){ if(fb!=null)System.arraycopy(fb,0,target,0,Math.min(fb.length,target.length)); if(TextUtils.isEmpty(raw))return; String[] p=raw.split(",",-1); for(int i=0;i<target.length&&i<p.length;i++)target[i]=bound(num(p[i],target[i]),min,max); }
    private void restoreStr(String raw,String[] target){ if(TextUtils.isEmpty(raw))return; String[] p=raw.split(",",-1); for(int i=0;i<target.length&&i<p.length;i++)target[i]=dec(p[i]); }
    private interface Sink { void set(String value); }

    private static class RoundRecord {
        long time; String date=""; String course=""; String tee=""; int total; int putts; int fw; int teeShots; String pars=""; String scores=""; String teeResults=""; String teeClubs=""; String scoreCard=""; String analysis="";
        String toLine(){ return time+"|"+enc(date)+"|"+enc(course)+"|"+enc(tee)+"|"+total+"|"+putts+"|"+fw+"|"+teeShots+"|"+enc(pars)+"|"+enc(scores)+"|"+enc(teeResults)+"|"+enc(teeClubs)+"|"+enc(scoreCard)+"|"+enc(analysis); }
        static RoundRecord fromLine(String line){ try{ if(TextUtils.isEmpty(line))return null; String[] p=line.split("\\|",-1); if(p.length<14)return null; RoundRecord r=new RoundRecord(); r.time=Long.parseLong(p[0]); r.date=dec(p[1]); r.course=dec(p[2]); r.tee=dec(p[3]); r.total=Integer.parseInt(p[4]); r.putts=Integer.parseInt(p[5]); r.fw=Integer.parseInt(p[6]); r.teeShots=Integer.parseInt(p[7]); r.pars=dec(p[8]); r.scores=dec(p[9]); r.teeResults=dec(p[10]); r.teeClubs=dec(p[11]); r.scoreCard=dec(p[12]); r.analysis=dec(p[13]); return r; }catch(Exception e){return null;} }
        private static String enc(String s){ return Base64.encodeToString((s==null?"":s).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP); }
        private static String dec(String s){ try{return new String(Base64.decode(s,Base64.NO_WRAP),StandardCharsets.UTF_8);}catch(Exception e){return"";} }
    }
}
