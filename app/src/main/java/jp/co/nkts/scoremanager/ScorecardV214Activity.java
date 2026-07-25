package jp.co.nkts.scoremanager;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ScorecardV214Activity extends Activity {
    private static final int HOLES = 18;
    private static final int PLAYERS = 4;
    private static final int MAX_SCORE = 15;
    private static final int FREE_LIMIT = 5;
    private static final int REQ_CSV = 601;
    private static final int REQ_BACKUP = 602;
    private static final int REQ_RESTORE = 603;
    private static final int REQ_PDF = 604;

    private static final String PREF = "gso_public_v214";
    private static final String OLD_PREF = "gso_public_v213";
    private static final String KEY_LANG = "lang";
    private static final String KEY_HISTORY = "history_v214";
    private static final String KEY_PROFILE_NAME = "profile_name";

    private static final int C_BG = 0xFFF5F8FB;
    private static final int C_CARD = 0xFFFFFFFF;
    private static final int C_TEXT = 0xFF0F172A;
    private static final int C_MUTED = 0xFF64748B;
    private static final int C_BORDER = 0xFFCBD5E1;
    private static final int C_GREEN = 0xFF166534;
    private static final int C_GREEN_D = 0xFF14532D;
    private static final int C_BLUE = 0xFF087DB8;
    private static final int C_BLUE_D = 0xFF075985;
    private static final int C_BLUE_SOFT = 0xFFDDF1FC;
    private static final int C_PAR = 0xFFF1F7D9;
    private static final int C_GRAY = 0xFFE5E7EB;
    private static final int C_GRAY_D = 0xFF4B5563;
    private static final int C_TOTAL = 0xFFFFF7D6;
    private static final int C_LOCK = 0xFFFFF7ED;

    private final String[] langCodes = {"ja", "en", "es", "fr", "ko", "zh", "tw", "de"};
    private final String[] langNames = {"日本語", "English", "Español", "Français", "한국어", "简体中文", "繁體中文", "Deutsch"};
    private final String[] langLabels = {"JP", "EN", "ES", "FR", "KO", "简", "繁", "DE"};
    private final int[] defaultPars = {4,4,3,5,4,4,5,3,4,4,5,4,3,4,4,5,3,4};

    private ScrollView scroll;
    private LinearLayout root;
    private String lang = "";
    private String profileName = "";
    private String roundDate = "";
    private String course = "";
    private String tee = "";
    private String memo = "";
    private String csvFrom = "";
    private String csvTo = "";
    private String selectedRecordLine = "";
    private String pdfRecordLine = "";
    private int screen = 0;
    private int currentHole = 0;
    private int activePlayers = 1;
    private int tensPlayer = -1;
    private boolean inRound = false;
    private boolean confirmCancel = false;

    private final int[] pars = new int[HOLES];
    private final int[][] scores = new int[PLAYERS][HOLES];
    private final int[] putts = new int[HOLES];
    private final int[] teeResult = new int[HOLES];
    private final String[] names = {"Player 1", "Player 2", "Player 3", "Player 4"};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        migrateOldData();
        initPars();
        loadDraft();
        setContentView(baseView());
        if (TextUtils.isEmpty(lang)) renderLanguageSelect();
        else if (inRound) renderRound(false);
        else renderHome();
    }

    @Override protected void onPause() { saveDraft(); super.onPause(); }
    @Override protected void onStop() { saveDraft(); super.onStop(); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_CSV) writeText(uri, buildCsv(), true, t("csv_saved"), t("csv_failed"));
        else if (requestCode == REQ_BACKUP) writeText(uri, buildBackup(), false, t("backup_saved"), t("backup_failed"));
        else if (requestCode == REQ_RESTORE) restoreBackup(uri);
        else if (requestCode == REQ_PDF) writeScorecardPdf(uri);
    }

    private boolean paid() { return BuildConfig.PAID_EDITION; }
    private SharedPreferences prefs() { return getSharedPreferences(PREF, MODE_PRIVATE); }

    private void migrateOldData() {
        SharedPreferences current = prefs();
        if (current.getBoolean("migrated_v213", false)) return;
        SharedPreferences old = getSharedPreferences(OLD_PREF, MODE_PRIVATE);
        SharedPreferences.Editor e = current.edit();
        if (TextUtils.isEmpty(current.getString(KEY_LANG, ""))) e.putString(KEY_LANG, old.getString("lang", ""));
        String migratedProfile = "";
        String oldNames = old.getString("names", "");
        String[] decoded = deserializeStrings(oldNames, PLAYERS, "");
        if (!TextUtils.isEmpty(decoded[0]) && !decoded[0].toLowerCase(Locale.ROOT).startsWith("player")) migratedProfile = decoded[0];
        if (!TextUtils.isEmpty(migratedProfile)) e.putString(KEY_PROFILE_NAME, migratedProfile);
        String oldHistory = old.getString("history", "");
        if (TextUtils.isEmpty(current.getString(KEY_HISTORY, "")) && !TextUtils.isEmpty(oldHistory)) {
            ArrayList<Record> converted = convertLegacyHistory(oldHistory, migratedProfile);
            e.putString(KEY_HISTORY, recordsToText(converted));
        }
        e.putBoolean("migrated_v213", true).apply();
    }

    private ArrayList<Record> convertLegacyHistory(String raw, String migratedProfile) {
        ArrayList<Record> out = new ArrayList<>();
        for (String line : raw.split("\\n", -1)) {
            LegacyRecord old = LegacyRecord.from(line);
            if (old == null) continue;
            Record r = new Record();
            r.date = old.date;
            r.course = old.course;
            r.tee = old.tee;
            r.memo = old.memo;
            r.activePlayers = 1;
            String playerOne = TextUtils.isEmpty(migratedProfile) ? "Player 1" : migratedProfile;
            r.names = serializeStrings(new String[]{playerOne, "", "", ""});
            r.pars = old.pars;
            r.scores0 = old.scores;
            r.scores1 = r.scores2 = r.scores3 = serializeInts(new int[HOLES]);
            r.putts = old.puttData;
            r.teeResults = old.teeData;
            out.add(r);
        }
        return out;
    }

    private View baseView() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(C_BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(20));
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void renderLanguageSelect() {
        screen = 5;
        root.removeAllViews();
        root.addView(hero("Golf Scorecard Offline", "Language / 言語 / Idioma / Langue / 언어 / 语言 / 語言 / Sprache"));
        LinearLayout c = card();
        c.addView(section("Language"));
        c.addView(info("Select your language. It can be changed later from the bottom of the app.", false));
        for (int i = 0; i < langCodes.length; i++) {
            final String code = langCodes[i];
            Button b = primary(langNames[i]);
            b.setOnClickListener(v -> { lang = code; prefs().edit().putString(KEY_LANG, lang).apply(); renderHome(); });
            c.addView(b, fullWithMargin());
        }
        root.addView(c);
        top();
    }

    private void renderHome() {
        screen = 0;
        inRound = false;
        saveDraft();
        root.removeAllViews();
        root.addView(hero(t("app_title"), paid() ? t("edition_paid") : t("edition_free")));
        root.addView(profileSummaryCard());
        root.addView(licenseCard());
        LinearLayout main = card();
        main.addView(section(t("round")));
        Button start = primary(t("start_round"));
        start.setOnClickListener(v -> {
            if (TextUtils.isEmpty(profileName)) { toast(t("register_name_first")); renderSettings(); return; }
            newRound(); renderRound(false);
        });
        main.addView(start, fullWithMargin());
        if (hasDraft()) { Button resume = secondary(t("resume_round")); resume.setOnClickListener(v -> renderRound(false)); main.addView(resume, fullWithMargin()); }
        Button history = secondary(t("history")); history.setOnClickListener(v -> renderHistory()); main.addView(history, fullWithMargin());
        Button analysis = secondary(t("analysis")); analysis.setOnClickListener(v -> renderAnalysis()); main.addView(analysis, fullWithMargin());
        Button settings = secondary(t("settings")); settings.setOnClickListener(v -> renderSettings()); main.addView(settings, fullWithMargin());
        root.addView(main);
        root.addView(csvCard());
        root.addView(statsCard());
        nav(); top();
    }

    private View profileSummaryCard() {
        LinearLayout c = card();
        c.addView(section(t("registered_player")));
        if (TextUtils.isEmpty(profileName)) {
            c.addView(info(t("name_not_registered"), false));
            Button b = primary(t("register_name")); b.setOnClickListener(v -> renderSettings()); c.addView(b, fullWithMargin());
        } else c.addView(info(profileName, true));
        return c;
    }

    private View licenseCard() {
        LinearLayout c = card();
        if (paid()) {
            c.addView(section(t("license_paid_title")));
            c.addView(info(t("license_paid_text"), true));
        } else {
            int n = loadHistory().size();
            c.addView(section(t("license_free_title")));
            c.addView(info(t("saved_rounds") + ": " + Math.min(n, FREE_LIMIT) + "/" + FREE_LIMIT + "\n" + t("license_free_text"), false));
            Button up = primary(t("upgrade")); up.setOnClickListener(v -> toast(t("test_pro"))); c.addView(up, fullWithMargin());
        }
        return c;
    }

    private View csvCard() {
        if (!paid()) return locked(t("csv_export"));
        LinearLayout c = card();
        c.addView(section(t("csv_export")));
        c.addView(info(t("csv_note"), false));
        EditText from = dateInput(t("from_date"), csvFrom, value -> { csvFrom = value; prefs().edit().putString("csvFrom", value).apply(); });
        EditText to = dateInput(t("to_date"), csvTo, value -> { csvTo = value; prefs().edit().putString("csvTo", value).apply(); });
        c.addView(from); c.addView(to);
        Button save = primary(t("csv_save"));
        save.setOnClickListener(v -> createDocument(REQ_CSV, "text/csv", "GolfScore_" + fileDate(csvFrom, "from") + "_" + fileDate(csvTo, "to") + ".csv"));
        c.addView(save, fullWithMargin());
        return c;
    }

    private View statsCard() {
        LinearLayout c = card(); c.addView(section(t("recent"))); c.addView(info(stats(loadHistory()), true)); return c;
    }

    private View locked(String title) {
        LinearLayout c = card(); c.addView(section(title)); c.addView(info(t("locked"), false));
        Button b = primary(t("upgrade")); b.setOnClickListener(v -> toast(t("test_pro"))); c.addView(b, fullWithMargin()); return c;
    }

    private void renderRound(boolean keepScroll) {
        screen = 1; inRound = true; int oldY = keepScroll ? scroll.getScrollY() : 0; root.removeAllViews();
        LinearLayout head = card();
        TextView hole = text((currentHole + 1) + "H  PAR " + pars[currentHole], 38, C_TEXT, true); hole.setGravity(Gravity.CENTER); head.addView(hole);
        TextView sub = text(roundDate + "  " + safe(course, t("course_empty")), 17, C_MUTED, false); sub.setGravity(Gravity.CENTER); head.addView(sub);
        root.addView(head); root.addView(roundSettings()); root.addView(holeChooser()); root.addView(scoreInput()); root.addView(finishCard()); languageFooter();
        if (keepScroll) restoreScroll(oldY); else top();
    }

    private View roundSettings() {
        LinearLayout c = card(); c.addView(section(t("round_settings")));
        c.addView(dateInput(t("round_date"), roundDate, value -> { roundDate = value; saveDraft(); }));
        EditText courseInput = input(t("course")); courseInput.setText(course); courseInput.addTextChangedListener(w(value -> { course = value; saveDraft(); })); c.addView(courseInput);
        EditText teeInput = input(t("tee")); teeInput.setText(tee); teeInput.addTextChangedListener(w(value -> { tee = value; saveDraft(); })); c.addView(teeInput);
        EditText memoInput = input(t("start_memo")); memoInput.setText(memo); memoInput.addTextChangedListener(w(value -> { memo = value; saveDraft(); })); c.addView(memoInput);
        c.addView(text(t("players"), 18, C_TEXT, true));
        LinearLayout countRow = row();
        for (int i = 1; i <= PLAYERS; i++) { final int count = i; Button b = choice(String.valueOf(i), activePlayers == i); b.setOnClickListener(v -> { activePlayers = count; saveDraft(); renderRound(true); }); countRow.addView(b, weight()); }
        c.addView(countRow);
        LinearLayout playerOne = lite(); playerOne.addView(text(t("registered_player"), 15, C_MUTED, true)); playerOne.addView(text(safe(names[0], profileName), 22, C_TEXT, true)); c.addView(playerOne);
        for (int p = 1; p < activePlayers; p++) { final int index = p; EditText nameInput = input(t("player") + " " + (p + 1)); nameInput.setText(names[p]); nameInput.addTextChangedListener(w(value -> { names[index] = value; saveDraft(); })); c.addView(nameInput); }
        return c;
    }

    private View holeChooser() {
        LinearLayout c = card(); c.addView(section(t("progress"))); c.addView(text(safe(names[0], profileName) + "  " + entered(0) + "/18  /  " + t("missing") + " " + missing(), 19, C_TEXT, true));
        for (int r = 0; r < 3; r++) { LinearLayout line = row(); for (int col = 0; col < 6; col++) { int h = r * 6 + col; Button b = choice(String.valueOf(h + 1), h == currentHole); b.setTextSize(20); b.setMinHeight(dp(56)); final int target = h; b.setOnClickListener(v -> { currentHole = target; tensPlayer = -1; saveDraft(); renderRound(false); }); line.addView(b, weight()); } c.addView(line); }
        return c;
    }

    private View scoreInput() {
        LinearLayout c = card(); c.addView(section(t("score_input")));
        LinearLayout parRow = row();
        for (int value = 3; value <= 6; value++) { final int parValue = value; Button b = choice("PAR " + value, pars[currentHole] == value); b.setTextSize(20); b.setOnClickListener(v -> { pars[currentHole] = parValue; saveDraft(); renderRound(true); }); parRow.addView(b, weight()); }
        c.addView(parRow);
        for (int p = 0; p < activePlayers; p++) c.addView(playerScore(p));
        LinearLayout navRow = row();
        Button prev = secondary(t("prev")); prev.setEnabled(currentHole > 0); prev.setOnClickListener(v -> { currentHole--; saveDraft(); renderRound(false); });
        Button next = primary(t("next")); next.setEnabled(currentHole < HOLES - 1); next.setOnClickListener(v -> { currentHole++; saveDraft(); renderRound(false); });
        navRow.addView(prev, weight()); navRow.addView(next, weight()); c.addView(navRow); return c;
    }

    private View playerScore(int player) {
        LinearLayout c = lite();
        String playerName = safe(names[player], t("player") + " " + (player + 1));
        c.addView(text(playerName + "  SCORE " + (scores[player][currentHole] == 0 ? "-" : scores[player][currentHole]) + (tensPlayer == player ? "  10+" : ""), 28, C_TEXT, true));
        String[][] keys = {{"1","2","3"},{"4","5","6"},{"7","8","9"},{"1+","0",t("clear")}};
        for (String[] keyRow : keys) { LinearLayout row = row(); for (String key : keyRow) { Button b = choice(key, false); b.setTextSize(28); b.setMinHeight(dp(72)); b.setOnClickListener(v -> scoreKey(player, key)); row.addView(b, weight()); } c.addView(row); }
        if (player == 0) {
            c.addView(text("PT", 17, C_MUTED, true));
            LinearLayout ptRow = row();
            for (int value = 1; value <= 4; value++) { final int ptValue = value; Button b = choice(value == 4 ? "4+" : String.valueOf(value), putts[currentHole] == value); b.setTextSize(24); b.setOnClickListener(v -> { putts[currentHole] = ptValue; saveDraft(); renderRound(true); }); ptRow.addView(b, weight()); }
            c.addView(ptRow); c.addView(text(t("tee_result"), 16, C_MUTED, true));
            LinearLayout teeRow = row(); String[] labels = {"-", "FW", t("left_short"), t("right_short"), "L-OB", "R-OB"};
            for (int i = 0; i < labels.length; i++) { final int value = i; Button b = choice(labels[i], teeResult[currentHole] == i); b.setTextSize(14); b.setOnClickListener(v -> { teeResult[currentHole] = value; saveDraft(); renderRound(true); }); teeRow.addView(b, weight()); }
            c.addView(teeRow);
        }
        return c;
    }

    private void scoreKey(int player, String key) {
        if (key.equals(t("clear"))) { scores[player][currentHole] = 0; tensPlayer = -1; saveDraft(); renderRound(true); return; }
        if ("1+".equals(key)) { tensPlayer = player; renderRound(true); return; }
        int digit = number(key, -1); if (digit < 0) return;
        if (tensPlayer == player) { if (digit <= 5) { scores[player][currentHole] = 10 + digit; tensPlayer = -1; } else { toast(t("ten_error")); return; } }
        else scores[player][currentHole] = digit == 0 ? 0 : digit;
        saveDraft(); renderRound(true);
    }

    private View finishCard() {
        LinearLayout c = card(); c.addView(section(t("finish")));
        Button save = primary(t("save_round")); save.setOnClickListener(v -> finishRound()); c.addView(save, fullWithMargin());
        Button cancel = secondary(t("cancel")); cancel.setOnClickListener(v -> { confirmCancel = true; renderRound(true); }); c.addView(cancel, fullWithMargin());
        if (confirmCancel) { c.addView(info(t("cancel_confirm"), false)); LinearLayout row = row(); Button back = primary(t("back_input")); back.setOnClickListener(v -> { confirmCancel = false; renderRound(true); }); Button home = secondary(t("back_home")); home.setOnClickListener(v -> { inRound = false; saveDraft(); renderHome(); }); row.addView(back, weight()); row.addView(home, weight()); c.addView(row); }
        return c;
    }

    private void finishRound() {
        if (!validDate(roundDate)) { toast(t("invalid_date")); return; }
        if (missing() > 0) { toast(t("missing") + ": " + missing()); return; }
        ArrayList<Record> records = loadHistory();
        if (!paid() && records.size() >= FREE_LIMIT) { toast(t("free_limit_reached")); renderHome(); return; }
        Record r = new Record();
        r.date = roundDate; r.course = safe(course, t("course_empty")); r.tee = tee; r.memo = memo; r.activePlayers = activePlayers; r.names = serializeStrings(names); r.pars = serializeInts(pars); r.scores0 = serializeInts(scores[0]); r.scores1 = serializeInts(scores[1]); r.scores2 = serializeInts(scores[2]); r.scores3 = serializeInts(scores[3]); r.putts = serializeInts(putts); r.teeResults = serializeInts(teeResult);
        records.add(0, r); saveHistory(records); inRound = false; saveDraft(); selectedRecordLine = r.toLine(); renderHistory();
    }

    private void renderHistory() {
        screen = 2; inRound = false; saveDraft(); root.removeAllViews(); root.addView(hero(t("history"), t("history_sub")));
        LinearLayout listCard = card(); ArrayList<Record> records = loadHistory();
        if (records.isEmpty()) listCard.addView(info(t("no_history"), false));
        for (Record r : records) {
            LinearLayout item = lite(); int[] playerOneScores = r.getScores(0);
            item.addView(text(r.date + "  " + r.course + "\n" + safe(r.getNames()[0], t("player") + " 1") + "  SCORE " + sum(playerOneScores), 18, C_TEXT, true));
            LinearLayout buttons = row();
            Button detail = secondary(t("detail")); detail.setOnClickListener(v -> { selectedRecordLine = r.toLine(); renderHistory(); });
            Button pdf = primary(t("pdf_save")); pdf.setOnClickListener(v -> { if (!paid()) { toast(t("locked")); return; } pdfRecordLine = r.toLine(); createDocument(REQ_PDF, "application/pdf", "GolfScore_" + fileDate(r.date, "date") + ".pdf"); });
            buttons.addView(detail, weight()); buttons.addView(pdf, weight()); item.addView(buttons); listCard.addView(item);
        }
        root.addView(listCard);
        if (!TextUtils.isEmpty(selectedRecordLine)) { Record selected = Record.from(selectedRecordLine); if (selected != null) root.addView(historyScorecard(selected)); }
        nav(); top();
    }

    private View historyScorecard(Record record) {
        LinearLayout card = card(); card.addView(section(record.course));
        card.addView(text(record.date + "  " + t("tee") + ": " + record.tee + (TextUtils.isEmpty(record.memo) ? "" : "  " + record.memo), 16, C_MUTED, false));
        HorizontalScrollView horizontal = new HorizontalScrollView(this); horizontal.setFillViewport(false);
        LinearLayout table = new LinearLayout(this); table.setOrientation(LinearLayout.VERTICAL);
        table.addView(tableHeader(record)); table.addView(sectionBar(t("front_nine"), record.activePlayers));
        for (int hole = 0; hole < 9; hole++) table.addView(tableHoleRow(record, hole));
        table.addView(tableSubtotalRow(record, false)); table.addView(sectionBar(t("back_nine"), record.activePlayers));
        for (int hole = 9; hole < 18; hole++) table.addView(tableHoleRow(record, hole));
        table.addView(tableSubtotalRow(record, true)); table.addView(tableTotalRow(record));
        horizontal.addView(table); card.addView(horizontal, fullWithMargin()); return card;
    }

    private View tableHeader(Record record) {
        LinearLayout row = row(); row.addView(tableCell(t("hole"), 52, C_BLUE, 0xFFFFFFFF, true)); row.addView(tableCell("PAR", 52, C_BLUE, 0xFFFFFFFF, true));
        String[] recordNames = record.getNames();
        for (int p = 0; p < record.activePlayers; p++) { row.addView(tableCell(safe(recordNames[p], t("player") + " " + (p + 1)), p == 0 ? 112 : 132, C_BLUE, 0xFFFFFFFF, true)); if (p == 0) row.addView(tableCell("PT", 46, C_BLUE, 0xFFFFFFFF, true)); }
        return row;
    }

    private View sectionBar(String title, int playerCount) {
        TextView v = text(title, 16, 0xFFFFFFFF, true); v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(dp(10), 0, 0, 0); v.setBackgroundColor(C_GRAY_D); v.setMinHeight(dp(34));
        int width = 52 + 52 + 112 + 46 + 132 * Math.max(0, playerCount - 1); v.setLayoutParams(new LinearLayout.LayoutParams(dp(width), dp(34))); return v;
    }

    private View tableHoleRow(Record record, int hole) {
        LinearLayout row = row(); int stripe = hole % 2 == 0 ? 0xFFF8FAFC : 0xFFEFF4F8; int[] recordPars = record.getPars(); int[] recordPutts = record.getPutts();
        row.addView(tableCell(String.valueOf(hole < 9 ? hole + 1 : hole - 8), 52, stripe, C_TEXT, true)); row.addView(tableCell(String.valueOf(recordPars[hole]), 52, C_PAR, C_TEXT, false));
        for (int p = 0; p < record.activePlayers; p++) { int score = record.getScores(p)[hole]; row.addView(tableCell(scoreTextWithDelta(score, recordPars[hole]), p == 0 ? 112 : 132, p == 0 ? C_BLUE_SOFT : stripe, C_TEXT, true)); if (p == 0) row.addView(tableCell(recordPutts[hole] == 0 ? "-" : String.valueOf(recordPutts[hole]), 46, stripe, C_TEXT, false)); }
        return row;
    }

    private View tableSubtotalRow(Record record, boolean back) {
        LinearLayout row = row(); int from = back ? 9 : 0; int to = back ? 18 : 9; int[] recordPars = record.getPars();
        row.addView(tableCell(back ? t("back_total") : t("front_total"), 52, C_GRAY, C_TEXT, true)); row.addView(tableCell(String.valueOf(sumRange(recordPars, from, to)), 52, C_GRAY, C_TEXT, true));
        int[] recordPutts = record.getPutts();
        for (int p = 0; p < record.activePlayers; p++) { int total = sumRange(record.getScores(p), from, to); int parTotal = sumRange(recordPars, from, to); row.addView(tableCell(total + "  " + signed(total - parTotal), p == 0 ? 112 : 132, p == 0 ? C_BLUE_SOFT : C_GRAY, p == 0 ? C_BLUE_D : C_TEXT, true)); if (p == 0) row.addView(tableCell(String.valueOf(sumRange(recordPutts, from, to)), 46, C_GRAY, C_TEXT, true)); }
        return row;
    }

    private View tableTotalRow(Record record) {
        LinearLayout row = row(); int[] recordPars = record.getPars(); int parTotal = sum(recordPars);
        row.addView(tableCell(t("total"), 52, C_TOTAL, C_TEXT, true)); row.addView(tableCell(String.valueOf(parTotal), 52, C_TOTAL, C_TEXT, true));
        for (int p = 0; p < record.activePlayers; p++) { int total = sum(record.getScores(p)); row.addView(tableCell(total + "  " + signed(total - parTotal), p == 0 ? 112 : 132, C_TOTAL, p == 0 ? C_BLUE_D : C_TEXT, true)); if (p == 0) row.addView(tableCell(String.valueOf(sum(record.getPutts())), 46, C_TOTAL, C_TEXT, true)); }
        return row;
    }

    private TextView tableCell(String value, int widthDp, int background, int color, boolean bold) {
        TextView v = text(value, 16, color, bold); v.setGravity(Gravity.CENTER); v.setBackground(cellBackground(background)); v.setPadding(dp(4), dp(4), dp(4), dp(4)); v.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(52))); return v;
    }

    private GradientDrawable cellBackground(int fill) { GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setStroke(dp(1), C_BORDER); return g; }
    private String scoreTextWithDelta(int score, int par) { if (score <= 0) return "-"; int delta = score - par; return score + (delta == 0 ? "  -" : "  " + signed(delta)); }
    private String signed(int value) { return value > 0 ? "+" + value : String.valueOf(value); }

    private void renderAnalysis() {
        screen = 3; root.removeAllViews(); root.addView(hero(t("analysis"), paid() ? t("analysis_sub") : t("pro_feature")));
        if (!paid()) { root.addView(locked(t("analysis"))); nav(); top(); return; }
        LinearLayout c = card(); c.addView(info(stats(loadHistory()) + "\n\n" + trend(loadHistory()), true)); root.addView(c); nav(); top();
    }

    private void renderSettings() {
        screen = 4; root.removeAllViews(); root.addView(hero(t("settings"), paid() ? t("edition_paid") : t("edition_free")));
        LinearLayout profile = card(); profile.addView(section(t("player_profile"))); profile.addView(info(t("profile_note"), false));
        EditText nameInput = input(t("your_name")); nameInput.setText(profileName); profile.addView(nameInput);
        Button saveName = primary(t("save_name"));
        saveName.setOnClickListener(v -> { String newName = nameInput.getText().toString().trim(); if (TextUtils.isEmpty(newName)) { toast(t("name_required")); return; } profileName = newName; prefs().edit().putString(KEY_PROFILE_NAME, profileName).apply(); if (!inRound) names[0] = profileName; toast(t("name_saved")); renderSettings(); });
        profile.addView(saveName, fullWithMargin()); root.addView(profile);
        LinearLayout support = card(); support.addView(section(t("support")));
        Button review = secondary(t("review")); review.setOnClickListener(v -> openMarket()); support.addView(review, fullWithMargin());
        Button contact = secondary(t("contact")); contact.setOnClickListener(v -> mail(t("mail_subject_contact"))); support.addView(contact, fullWithMargin());
        Button idea = secondary(t("idea")); idea.setOnClickListener(v -> mail(t("mail_subject_idea"))); support.addView(idea, fullWithMargin()); root.addView(support);
        LinearLayout data = card(); data.addView(section(t("backup")));
        if (!paid()) data.addView(info(t("backup_locked"), false));
        else { data.addView(info(t("backup_note"), false)); Button backup = secondary(t("backup_save")); backup.setOnClickListener(v -> createDocument(REQ_BACKUP, "text/plain", "GolfScore_Backup_" + todayFile() + ".txt")); data.addView(backup, fullWithMargin()); Button restore = primary(t("restore_backup")); restore.setOnClickListener(v -> openDocument()); data.addView(restore, fullWithMargin()); }
        root.addView(data); nav(); top();
    }

    private String buildCsv() {
        StringBuilder b = new StringBuilder("Date,Course,Tee,Memo,Player,Total"); for (int h = 1; h <= HOLES; h++) b.append(",H").append(h); b.append("\n");
        int from = dateNumber(csvFrom, 0); int to = dateNumber(csvTo, 99999999);
        for (Record r : loadHistory()) { int date = dateNumber(r.date, 0); if (date < from || date > to) continue; String[] recordNames = r.getNames(); for (int p = 0; p < r.activePlayers; p++) { int[] playerScores = r.getScores(p); b.append(csv(r.date)).append(',').append(csv(r.course)).append(',').append(csv(r.tee)).append(',').append(csv(r.memo)).append(',').append(csv(recordNames[p])).append(',').append(sum(playerScores)); for (int value : playerScores) b.append(',').append(value); b.append('\n'); } }
        return b.toString();
    }

    private String buildBackup() { return "GSO_BACKUP_V214\n" + enc(profileName) + "\n" + enc(prefs().getString(KEY_HISTORY, "")) + "\n"; }

    private void restoreBackup(Uri uri) {
        try { String raw = read(uri); if (!raw.startsWith("GSO_BACKUP_V214")) throw new Exception("invalid backup"); String[] lines = raw.split("\\n", -1); if (lines.length < 3) throw new Exception("invalid backup"); profileName = dec(lines[1]); prefs().edit().putString(KEY_PROFILE_NAME, profileName).putString(KEY_HISTORY, dec(lines[2])).apply(); toast(t("restore_done")); renderHome(); }
        catch (Exception e) { toast(t("restore_failed")); }
    }

    private void writeScorecardPdf(Uri uri) {
        try { Record record = Record.from(pdfRecordLine); if (record == null) throw new Exception("record missing"); OutputStream out = getContentResolver().openOutputStream(uri); if (out == null) throw new Exception("cannot open file"); PdfDocument pdf = new PdfDocument(); int pageWidth = 595; int pageHeight = 842; PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()); Canvas canvas = page.getCanvas(); drawPdfScorecard(canvas, record, pageWidth, pageHeight); pdf.finishPage(page); pdf.writeTo(out); pdf.close(); out.close(); toast(t("pdf_saved")); }
        catch (Exception e) { toast(t("pdf_failed")); }
    }

    private void drawPdfScorecard(Canvas c, Record record, int pageWidth, int pageHeight) {
        Paint title = pdfPaint(19, true, C_TEXT); Paint normal = pdfPaint(10, false, C_TEXT); Paint bold = pdfPaint(10, true, C_TEXT); Paint whiteBold = pdfPaint(9, true, 0xFFFFFFFF);
        int margin = 20; c.drawText(t("scorecard_title"), margin, 28, title); c.drawText(record.course, margin, 48, bold); c.drawText(record.date + "   " + t("tee") + ": " + record.tee + (TextUtils.isEmpty(record.memo) ? "" : "   " + record.memo), margin, 64, normal);
        String[] recordNames = record.getNames(); int playerColumns = record.activePlayers; float x = margin; float y = 82; float holeW = 36; float parW = 36; float ptW = 32; float available = pageWidth - margin * 2 - holeW - parW - ptW; float playerW = available / Math.max(1, playerColumns); float rowH = 27;
        drawPdfCell(c, x, y, holeW, rowH, t("hole"), C_BLUE, whiteBold); x += holeW; drawPdfCell(c, x, y, parW, rowH, "PAR", C_BLUE, whiteBold); x += parW;
        for (int p = 0; p < playerColumns; p++) { drawPdfCell(c, x, y, playerW, rowH, shorten(recordNames[p], 13), C_BLUE, whiteBold); x += playerW; if (p == 0) { drawPdfCell(c, x, y, ptW, rowH, "PT", C_BLUE, whiteBold); x += ptW; } }
        y += rowH; y = drawPdfHalf(c, record, 0, 9, y, holeW, parW, playerW, ptW, rowH, bold, normal); y = drawPdfHalf(c, record, 9, 18, y, holeW, parW, playerW, ptW, rowH, bold, normal);
        x = margin; int[] recordPars = record.getPars();
        drawPdfCell(c, x, y, holeW, rowH + 3, t("total"), C_TOTAL, bold); x += holeW; drawPdfCell(c, x, y, parW, rowH + 3, String.valueOf(sum(recordPars)), C_TOTAL, bold); x += parW;
        for (int p = 0; p < playerColumns; p++) { int total = sum(record.getScores(p)); drawPdfCell(c, x, y, playerW, rowH + 3, total + "  " + signed(total - sum(recordPars)), C_TOTAL, bold); x += playerW; if (p == 0) { drawPdfCell(c, x, y, ptW, rowH + 3, String.valueOf(sum(record.getPutts())), C_TOTAL, bold); x += ptW; } }
        c.drawText("Golf Scorecard Offline", margin, pageHeight - 18, normal);
    }

    private float drawPdfHalf(Canvas c, Record record, int from, int to, float y, float holeW, float parW, float playerW, float ptW, float rowH, Paint bold, Paint normal) {
        int margin = 20; float totalWidth = holeW + parW + playerW * record.activePlayers + ptW; drawPdfCell(c, margin, y, totalWidth, 22, from == 0 ? t("front_nine") : t("back_nine"), C_GRAY_D, pdfPaint(9, true, 0xFFFFFFFF)); y += 22;
        int[] recordPars = record.getPars(); int[] recordPutts = record.getPutts();
        for (int hole = from; hole < to; hole++) { float x = margin; int stripe = hole % 2 == 0 ? 0xFFF8FAFC : 0xFFEFF4F8; drawPdfCell(c, x, y, holeW, rowH, String.valueOf(hole < 9 ? hole + 1 : hole - 8), stripe, bold); x += holeW; drawPdfCell(c, x, y, parW, rowH, String.valueOf(recordPars[hole]), C_PAR, normal); x += parW; for (int p = 0; p < record.activePlayers; p++) { drawPdfCell(c, x, y, playerW, rowH, scoreTextWithDelta(record.getScores(p)[hole], recordPars[hole]), p == 0 ? C_BLUE_SOFT : stripe, bold); x += playerW; if (p == 0) { drawPdfCell(c, x, y, ptW, rowH, recordPutts[hole] == 0 ? "-" : String.valueOf(recordPutts[hole]), stripe, normal); x += ptW; } } y += rowH; }
        float x = margin; int parTotal = sumRange(recordPars, from, to); drawPdfCell(c, x, y, holeW, rowH, from == 0 ? t("front_total") : t("back_total"), C_GRAY, bold); x += holeW; drawPdfCell(c, x, y, parW, rowH, String.valueOf(parTotal), C_GRAY, bold); x += parW;
        for (int p = 0; p < record.activePlayers; p++) { int total = sumRange(record.getScores(p), from, to); drawPdfCell(c, x, y, playerW, rowH, total + "  " + signed(total - parTotal), p == 0 ? C_BLUE_SOFT : C_GRAY, bold); x += playerW; if (p == 0) { drawPdfCell(c, x, y, ptW, rowH, String.valueOf(sumRange(recordPutts, from, to)), C_GRAY, bold); x += ptW; } }
        return y + rowH;
    }

    private void drawPdfCell(Canvas c, float x, float y, float width, float height, String value, int fill, Paint textPaint) {
        Paint fillPaint = new Paint(); fillPaint.setColor(fill); fillPaint.setStyle(Paint.Style.FILL); Paint linePaint = new Paint(); linePaint.setColor(C_BORDER); linePaint.setStyle(Paint.Style.STROKE); linePaint.setStrokeWidth(0.8f); c.drawRect(x, y, x + width, y + height, fillPaint); c.drawRect(x, y, x + width, y + height, linePaint); String text = value == null ? "" : value; float textWidth = textPaint.measureText(text); c.drawText(text, x + Math.max(2, (width - textWidth) / 2f), y + height * 0.67f, textPaint);
    }

    private Paint pdfPaint(float size, boolean bold, int color) { Paint p = new Paint(); p.setAntiAlias(true); p.setTextSize(size); p.setColor(color); if (bold) p.setTypeface(Typeface.DEFAULT_BOLD); return p; }

    private void newRound() {
        inRound = true; roundDate = today(); course = ""; tee = ""; memo = ""; currentHole = 0; activePlayers = 1; tensPlayer = -1; confirmCancel = false; names[0] = profileName;
        for (int p = 1; p < PLAYERS; p++) names[p] = t("player") + " " + (p + 1);
        for (int p = 0; p < PLAYERS; p++) for (int h = 0; h < HOLES; h++) scores[p][h] = 0;
        initPars(); for (int h = 0; h < HOLES; h++) { putts[h] = 0; teeResult[h] = 0; } saveDraft();
    }

    private void initPars() { System.arraycopy(defaultPars, 0, pars, 0, HOLES); }

    private void loadDraft() {
        SharedPreferences p = prefs(); lang = p.getString(KEY_LANG, ""); profileName = p.getString(KEY_PROFILE_NAME, ""); csvFrom = p.getString("csvFrom", ""); csvTo = p.getString("csvTo", ""); inRound = p.getBoolean("inRound", false); roundDate = p.getString("roundDate", today()); course = p.getString("course", ""); tee = p.getString("tee", ""); memo = p.getString("memo", ""); currentHole = clamp(p.getInt("hole", 0), 0, HOLES - 1); activePlayers = clamp(p.getInt("players", 1), 1, PLAYERS);
        restoreInts(p.getString("pars", ""), pars, defaultPars, 3, 6); restoreInts(p.getString("putts", ""), putts, null, 0, 9); restoreInts(p.getString("teeResults", ""), teeResult, null, 0, 5); restoreStrings(p.getString("names", ""), names); for (int i = 0; i < PLAYERS; i++) restoreInts(p.getString("scores" + i, ""), scores[i], null, 0, MAX_SCORE); if (!inRound && !TextUtils.isEmpty(profileName)) names[0] = profileName;
    }

    private void saveDraft() {
        SharedPreferences.Editor e = prefs().edit(); e.putString(KEY_LANG, lang).putString(KEY_PROFILE_NAME, profileName).putBoolean("inRound", inRound).putString("roundDate", roundDate).putString("course", course).putString("tee", tee).putString("memo", memo).putInt("hole", currentHole).putInt("players", activePlayers).putString("pars", serializeInts(pars)).putString("putts", serializeInts(putts)).putString("teeResults", serializeInts(teeResult)).putString("names", serializeStrings(names)); for (int i = 0; i < PLAYERS; i++) e.putString("scores" + i, serializeInts(scores[i])); e.apply();
    }

    private ArrayList<Record> loadHistory() { ArrayList<Record> records = new ArrayList<>(); String raw = prefs().getString(KEY_HISTORY, ""); if (TextUtils.isEmpty(raw)) return records; for (String line : raw.split("\\n", -1)) { Record r = Record.from(line); if (r != null) records.add(r); } return records; }
    private void saveHistory(ArrayList<Record> records) { int limit = paid() ? 400 : FREE_LIMIT; ArrayList<String> lines = new ArrayList<>(); for (int i = 0; i < records.size() && i < limit; i++) lines.add(records.get(i).toLine()); prefs().edit().putString(KEY_HISTORY, TextUtils.join("\n", lines)).apply(); }
    private String recordsToText(ArrayList<Record> records) { ArrayList<String> lines = new ArrayList<>(); for (Record r : records) lines.add(r.toLine()); return TextUtils.join("\n", lines); }

    private String stats(ArrayList<Record> records) {
        if (records.isEmpty()) return t("no_data"); int totalScore = 0; int totalPutts = 0; int fairways = 0; int teeShots = 0;
        for (Record r : records) { totalScore += sum(r.getScores(0)); totalPutts += sum(r.getPutts()); fairways += count(r.getTeeResults(), 1); teeShots += countNonZero(r.getTeeResults()); }
        return t("rounds") + ": " + records.size() + "\n" + t("avg_score") + ": " + one(totalScore * 1.0 / records.size()) + "\n" + t("avg_pt") + ": " + one(totalPutts * 1.0 / records.size()) + "\nFW: " + (teeShots == 0 ? "-" : one(fairways * 100.0 / teeShots) + "%");
    }

    private String trend(ArrayList<Record> records) { if (records.isEmpty()) return t("no_data"); Record r = records.get(0); return t("latest_round") + "\n" + r.date + "  " + r.course + "\nSCORE " + sum(r.getScores(0)) + " / PT " + sum(r.getPutts()) + " / OB " + (count(r.getTeeResults(), 4) + count(r.getTeeResults(), 5)); }
    private int entered(int player) { int n = 0; for (int value : scores[player]) if (value > 0) n++; return n; }
    private int missing() { int n = 0; for (int p = 0; p < activePlayers; p++) for (int h = 0; h < HOLES; h++) if (scores[p][h] == 0) n++; return n; }
    private int sum(int[] values) { int n = 0; for (int value : values) n += value; return n; }
    private int sumRange(int[] values, int from, int to) { int n = 0; for (int i = from; i < to; i++) n += values[i]; return n; }
    private int count(int[] values, int target) { int n = 0; for (int value : values) if (value == target) n++; return n; }
    private int countNonZero(int[] values) { int n = 0; for (int value : values) if (value != 0) n++; return n; }
    private boolean hasDraft() { if (!inRound) return false; for (int p = 0; p < PLAYERS; p++) for (int h = 0; h < HOLES; h++) if (scores[p][h] > 0) return true; return !TextUtils.isEmpty(course) || !TextUtils.isEmpty(tee); }

    private EditText dateInput(String label, String value, Sink sink) { EditText e = input("YYYY/MM/DD  " + label); e.setText(value); e.setInputType(InputType.TYPE_CLASS_DATETIME); e.setFocusable(false); e.setOnClickListener(v -> showDatePicker(e, sink)); return e; }
    private void showDatePicker(EditText target, Sink sink) { Calendar calendar = Calendar.getInstance(); int date = dateNumber(target.getText().toString(), -1); if (date > 0) { calendar.set(Calendar.YEAR, date / 10000); calendar.set(Calendar.MONTH, (date / 100) % 100 - 1); calendar.set(Calendar.DAY_OF_MONTH, date % 100); } new DatePickerDialog(this, (view, year, month, day) -> { String formatted = String.format(Locale.US, "%04d/%02d/%02d", year, month + 1, day); target.setText(formatted); sink.set(formatted); }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show(); }

    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(14), dp(14), dp(14), dp(14)); v.setBackground(rounded(C_CARD, C_BORDER, 18)); LinearLayout.LayoutParams p = full(); p.setMargins(0, dp(6), 0, dp(6)); v.setLayoutParams(p); return v; }
    private LinearLayout lite() { LinearLayout v = card(); v.setBackground(rounded(0xFFF9FBFD, C_BORDER, 16)); return v; }
    private View hero(String title, String subtitle) { LinearLayout c = card(); TextView titleView = text(title, 26, C_TEXT, true); titleView.setGravity(Gravity.CENTER); c.addView(titleView); TextView subtitleView = text(subtitle, 16, C_MUTED, false); subtitleView.setGravity(Gravity.CENTER); c.addView(subtitleView); return c; }
    private TextView section(String value) { TextView v = text(value, 20, C_TEXT, true); v.setPadding(0, 0, 0, dp(8)); return v; }
    private TextView info(String value, boolean strong) { TextView v = text(value, strong ? 18 : 16, C_TEXT, strong); v.setPadding(dp(12), dp(12), dp(12), dp(12)); v.setBackground(rounded(strong ? C_BLUE_SOFT : C_LOCK, C_BORDER, 14)); return v; }
    private TextView text(String value, int size, int color, boolean bold) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private EditText input(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setTextSize(18); e.setSingleLine(true); e.setTextColor(C_TEXT); e.setPadding(dp(8), dp(8), dp(8), dp(8)); return e; }
    private Button primary(String value) { Button b = button(value); b.setBackground(rounded(C_GREEN, C_GREEN_D, 14)); b.setTextColor(0xFFFFFFFF); return b; }
    private Button secondary(String value) { Button b = button(value); b.setBackground(rounded(C_CARD, C_BORDER, 14)); b.setTextColor(C_TEXT); return b; }
    private Button choice(String value, boolean selected) { Button b = button(value); b.setBackground(rounded(selected ? C_GREEN : C_CARD, selected ? C_GREEN_D : C_BORDER, 14)); b.setTextColor(selected ? 0xFFFFFFFF : C_TEXT); return b; }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(16); b.setMinHeight(dp(56)); b.setSingleLine(false); b.setGravity(Gravity.CENTER); b.setPadding(dp(6), dp(4), dp(6), dp(4)); return b; }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams fullWithMargin() { LinearLayout.LayoutParams p = full(); p.setMargins(0, dp(5), 0, dp(5)); return p; }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1); p.setMargins(dp(2), dp(2), dp(2), dp(2)); return p; }
    private GradientDrawable rounded(int fill, int stroke, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(1), stroke); return g; }

    private void nav() { LinearLayout n = row(); Button home = secondary(t("home")); home.setOnClickListener(v -> renderHome()); Button history = secondary(t("history")); history.setOnClickListener(v -> renderHistory()); Button analysis = secondary(t("analysis")); analysis.setOnClickListener(v -> renderAnalysis()); Button settings = secondary(t("settings")); settings.setOnClickListener(v -> renderSettings()); n.addView(home, weight()); n.addView(history, weight()); n.addView(analysis, weight()); n.addView(settings, weight()); root.addView(n); languageFooter(); }
    private void languageFooter() { LinearLayout c = card(); c.addView(section(t("language"))); LinearLayout r = row(); for (int i = 0; i < langCodes.length; i++) { final String code = langCodes[i]; Button b = choice(langLabels[i], code.equals(lang)); b.setTextSize(10); b.setMinHeight(dp(42)); b.setOnClickListener(v -> { lang = code; prefs().edit().putString(KEY_LANG, lang).apply(); if (screen == 1) renderRound(true); else if (screen == 2) renderHistory(); else if (screen == 3) renderAnalysis(); else if (screen == 4) renderSettings(); else renderHome(); }); r.addView(b, weight()); } c.addView(r); root.addView(c); }
    private TextWatcher w(Sink sink) { return new TextWatcher() { @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {} @Override public void onTextChanged(CharSequence s, int start, int before, int count) { sink.set(s == null ? "" : s.toString()); } @Override public void afterTextChanged(Editable s) {} }; }
    private interface Sink { void set(String value); }

    private int languageIndex() { for (int i = 0; i < langCodes.length; i++) if (langCodes[i].equals(lang)) return i; return 1; }
    private String L(String ja, String en, String es, String fr, String ko, String zh, String tw, String de) { String[] values = {ja, en, es, fr, ko, zh, tw, de}; return values[languageIndex()]; }

    private String t(String key) {
        switch (key) {
            case "app_title": return L("ゴルフスコアカード オフライン", "Golf Scorecard Offline", "Tarjeta de golf sin conexión", "Carte de score golf hors ligne", "오프라인 골프 스코어카드", "离线高尔夫记分卡", "離線高爾夫計分卡", "Golf-Scorekarte offline");
            case "language": return L("言語", "Language", "Idioma", "Langue", "언어", "语言", "語言", "Sprache");
            case "edition_paid": return L("Pro / 永久ライセンス", "Pro / Lifetime License", "Pro / Licencia permanente", "Pro / Licence à vie", "Pro / 평생 라이선스", "Pro / 永久许可", "Pro / 永久授權", "Pro / Dauerlizenz");
            case "edition_free": return L("無料体験版", "Free Trial", "Prueba gratuita", "Essai gratuit", "무료 체험판", "免费试用版", "免費試用版", "Kostenlose Testversion");
            case "registered_player": return L("登録プレイヤー", "Registered player", "Jugador registrado", "Joueur enregistré", "등록 플레이어", "已注册球员", "已註冊球員", "Registrierter Spieler");
            case "player_profile": return L("Player1 本人登録", "Player 1 profile", "Perfil del jugador 1", "Profil du joueur 1", "플레이어 1 프로필", "球员1资料", "球員1資料", "Spieler-1-Profil");
            case "profile_note": return L("登録した名前は新しいラウンドのPlayer1に自動表示されます。進行中のラウンド名は変更しません。", "The registered name is automatically used for Player 1 in new rounds. It does not overwrite an active round.", "El nombre registrado se usa automáticamente para el jugador 1 en rondas nuevas.", "Le nom enregistré est utilisé automatiquement pour le joueur 1 des nouvelles parties.", "등록한 이름은 새 라운드의 플레이어 1에 자동으로 표시됩니다.", "注册姓名会自动用于新一轮的球员1。", "註冊姓名會自動用於新一輪的球員1。", "Der gespeicherte Name wird bei neuen Runden automatisch für Spieler 1 verwendet.");
            case "your_name": return L("あなたの名前", "Your name", "Tu nombre", "Votre nom", "이름", "您的姓名", "您的姓名", "Ihr Name");
            case "register_name": return L("名前を登録", "Register name", "Registrar nombre", "Enregistrer le nom", "이름 등록", "注册姓名", "註冊姓名", "Namen registrieren");
            case "save_name": return L("名前を保存", "Save name", "Guardar nombre", "Enregistrer le nom", "이름 저장", "保存姓名", "儲存姓名", "Namen speichern");
            case "name_saved": return L("名前を保存しました", "Name saved", "Nombre guardado", "Nom enregistré", "이름을 저장했습니다", "姓名已保存", "姓名已儲存", "Name gespeichert");
            case "name_required": return L("名前を入力してください", "Enter your name", "Introduce tu nombre", "Saisissez votre nom", "이름을 입력하세요", "请输入姓名", "請輸入姓名", "Bitte Namen eingeben");
            case "name_not_registered": return L("Player1の名前が未登録です。ラウンド開始前に登録してください。", "Player 1 has not been registered. Register a name before starting a round.", "El jugador 1 no está registrado.", "Le joueur 1 n'est pas enregistré.", "플레이어 1 이름이 등록되지 않았습니다.", "尚未注册球员1姓名。", "尚未註冊球員1姓名。", "Spieler 1 ist noch nicht registriert.");
            case "register_name_first": return L("先にPlayer1の名前を登録してください", "Register Player 1 first", "Registra primero al jugador 1", "Enregistrez d'abord le joueur 1", "먼저 플레이어 1을 등록하세요", "请先注册球员1", "請先註冊球員1", "Registrieren Sie zuerst Spieler 1");
            case "round": return L("ラウンド", "Round", "Ronda", "Partie", "라운드", "球局", "球局", "Runde");
            case "start_round": return L("ラウンド開始", "Start round", "Iniciar ronda", "Commencer la partie", "라운드 시작", "开始球局", "開始球局", "Runde starten");
            case "resume_round": return L("入力中のラウンドに戻る", "Resume active round", "Continuar ronda", "Reprendre la partie", "진행 중인 라운드로 돌아가기", "继续当前球局", "繼續目前球局", "Aktive Runde fortsetzen");
            case "history": return L("履歴", "History", "Historial", "Historique", "기록", "历史", "歷史", "Verlauf");
            case "history_sub": return L("見やすいスコアカード表示", "Readable scorecard history", "Historial de tarjetas legibles", "Historique des cartes lisibles", "보기 쉬운 스코어카드 기록", "清晰的记分卡历史", "清晰的計分卡歷史", "Übersichtliche Scorekarten");
            case "analysis": return L("分析", "Analysis", "Análisis", "Analyse", "분석", "分析", "分析", "Analyse");
            case "analysis_sub": return L("スコア傾向の集計", "Score trend summary", "Resumen de tendencias", "Résumé des tendances", "스코어 추세 요약", "成绩趋势汇总", "成績趨勢彙總", "Score-Trendübersicht");
            case "settings": return L("設定", "Settings", "Ajustes", "Réglages", "설정", "设置", "設定", "Einstellungen");
            case "home": return L("ホーム", "Home", "Inicio", "Accueil", "홈", "主页", "首頁", "Start");
            case "recent": return L("最近の成績", "Recent performance", "Rendimiento reciente", "Résultats récents", "최근 성적", "近期成绩", "近期成績", "Letzte Ergebnisse");
            case "round_settings": return L("ラウンド設定", "Round settings", "Ajustes de ronda", "Réglages de partie", "라운드 설정", "球局设置", "球局設定", "Rundeneinstellungen");
            case "round_date": return L("ラウンド日付", "Round date", "Fecha de ronda", "Date de partie", "라운드 날짜", "球局日期", "球局日期", "Rundendatum");
            case "course": return L("コース名", "Course", "Campo", "Parcours", "코스", "球场", "球場", "Platz");
            case "course_empty": return L("未入力コース", "Unnamed course", "Campo sin nombre", "Parcours sans nom", "코스 미입력", "未命名球场", "未命名球場", "Unbenannter Platz");
            case "tee": return L("ティー", "Tee", "Tee", "Tee", "티", "发球台", "發球台", "Abschlag");
            case "start_memo": return L("開始時間 / メモ", "Start time / memo", "Hora / nota", "Heure / note", "시작 시간 / 메모", "开始时间 / 备注", "開始時間 / 備註", "Startzeit / Notiz");
            case "players": return L("人数", "Players", "Jugadores", "Joueurs", "인원", "人数", "人數", "Spieler");
            case "player": return L("プレイヤー", "Player", "Jugador", "Joueur", "플레이어", "球员", "球員", "Spieler");
            case "progress": return L("進捗", "Progress", "Progreso", "Progression", "진행", "进度", "進度", "Fortschritt");
            case "missing": return L("未入力", "Missing", "Sin ingresar", "Manquant", "미입력", "未输入", "未輸入", "Fehlend");
            case "score_input": return L("スコア入力", "Score input", "Introducir score", "Saisie du score", "스코어 입력", "输入成绩", "輸入成績", "Score eingeben");
            case "clear": return L("消去", "Clear", "Borrar", "Effacer", "지우기", "清除", "清除", "Löschen");
            case "prev": return L("前のホール", "Previous hole", "Hoyo anterior", "Trou précédent", "이전 홀", "上一洞", "上一洞", "Vorheriges Loch");
            case "next": return L("次のホール", "Next hole", "Hoyo siguiente", "Trou suivant", "다음 홀", "下一洞", "下一洞", "Nächstes Loch");
            case "finish": return L("終了", "Finish", "Finalizar", "Terminer", "종료", "结束", "結束", "Beenden");
            case "save_round": return L("ラウンドを保存", "Save round", "Guardar ronda", "Enregistrer la partie", "라운드 저장", "保存球局", "儲存球局", "Runde speichern");
            case "cancel": return L("キャンセル終了", "Cancel / end", "Cancelar / terminar", "Annuler / terminer", "취소 / 종료", "取消 / 结束", "取消 / 結束", "Abbrechen / beenden");
            case "cancel_confirm": return L("入力を終了してホームへ戻りますか？", "End entry and return home?", "¿Terminar y volver al inicio?", "Terminer et revenir à l'accueil ?", "입력을 종료하고 홈으로 돌아갈까요?", "结束输入并返回主页？", "結束輸入並返回首頁？", "Eingabe beenden und zurück?");
            case "back_input": return L("入力へ戻る", "Back to input", "Volver", "Retour à la saisie", "입력으로 돌아가기", "返回输入", "返回輸入", "Zurück zur Eingabe");
            case "back_home": return L("ホームへ戻る", "Return home", "Volver al inicio", "Retour à l'accueil", "홈으로 돌아가기", "返回主页", "返回首頁", "Zur Startseite");
            case "tee_result": return L("ティーショット結果", "Tee-shot result", "Resultado de salida", "Résultat du départ", "티샷 결과", "开球结果", "開球結果", "Abschlagergebnis");
            case "left_short": return L("左", "Left", "Izq.", "Gauche", "왼쪽", "左", "左", "Links");
            case "right_short": return L("右", "Right", "Der.", "Droite", "오른쪽", "右", "右", "Rechts");
            case "ten_error": return L("10台は10〜15のみです", "10+ supports 10 to 15", "10+ admite 10 a 15", "10+ accepte 10 à 15", "10+는 10~15만 지원", "10+仅支持10到15", "10+僅支援10到15", "10+ unterstützt 10 bis 15");
            case "invalid_date": return L("正しい日付を選択してください", "Select a valid date", "Selecciona una fecha válida", "Sélectionnez une date valide", "올바른 날짜를 선택하세요", "请选择有效日期", "請選擇有效日期", "Gültiges Datum wählen");
            case "front_nine": return L("前半", "Front nine", "Primeros 9", "Aller", "전반", "前九洞", "前九洞", "Erste Neun");
            case "back_nine": return L("後半", "Back nine", "Últimos 9", "Retour", "후반", "后九洞", "後九洞", "Zweite Neun");
            case "front_total": return L("前半", "OUT", "IDA", "ALLER", "전반", "前九", "前九", "OUT");
            case "back_total": return L("後半", "IN", "VUELTA", "RETOUR", "후반", "后九", "後九", "IN");
            case "total": return L("合計", "Total", "Total", "Total", "합계", "总计", "總計", "Gesamt");
            case "hole": return L("Hole", "Hole", "Hoyo", "Trou", "홀", "洞", "洞", "Loch");
            case "detail": return L("スコアカード表示", "View scorecard", "Ver tarjeta", "Voir la carte", "스코어카드 보기", "查看记分卡", "查看計分卡", "Scorekarte anzeigen");
            case "pdf_save": return L("PDF保存", "Save PDF", "Guardar PDF", "Enregistrer PDF", "PDF 저장", "保存PDF", "儲存PDF", "PDF speichern");
            case "no_history": return L("履歴はまだありません", "No history yet", "Aún no hay historial", "Aucun historique", "기록이 없습니다", "暂无历史", "暫無歷史", "Noch kein Verlauf");
            case "no_data": return L("データなし", "No data", "Sin datos", "Aucune donnée", "데이터 없음", "无数据", "無資料", "Keine Daten");
            case "rounds": return L("ラウンド数", "Rounds", "Rondas", "Parties", "라운드 수", "球局数", "球局數", "Runden");
            case "avg_score": return L("平均スコア", "Average score", "Score medio", "Score moyen", "평균 스코어", "平均成绩", "平均成績", "Durchschnittsscore");
            case "avg_pt": return L("平均パット", "Average putts", "Putts medios", "Putts moyens", "평균 퍼트", "平均推杆", "平均推桿", "Durchschnittliche Putts");
            case "latest_round": return L("最新ラウンド", "Latest round", "Última ronda", "Dernière partie", "최근 라운드", "最新球局", "最新球局", "Letzte Runde");
            case "csv_export": return L("CSVエクスポート", "CSV export", "Exportar CSV", "Export CSV", "CSV 내보내기", "导出CSV", "匯出CSV", "CSV exportieren");
            case "csv_note": return L("日付範囲を指定し、全プレイヤーのスコアを保存します。", "Export all players by selected date range.", "Exporta todos los jugadores por fechas.", "Exporte tous les joueurs par période.", "날짜 범위별 모든 플레이어 점수를 저장합니다.", "按日期范围导出所有球员成绩。", "按日期範圍匯出所有球員成績。", "Exportiert alle Spieler im Datumsbereich.");
            case "from_date": return L("開始日", "Start date", "Fecha inicial", "Date de début", "시작일", "开始日期", "開始日期", "Startdatum");
            case "to_date": return L("終了日", "End date", "Fecha final", "Date de fin", "종료일", "结束日期", "結束日期", "Enddatum");
            case "csv_save": return L("CSVを保存", "Save CSV", "Guardar CSV", "Enregistrer CSV", "CSV 저장", "保存CSV", "儲存CSV", "CSV speichern");
            case "csv_saved": return L("CSVを保存しました", "CSV saved", "CSV guardado", "CSV enregistré", "CSV 저장 완료", "CSV已保存", "CSV已儲存", "CSV gespeichert");
            case "csv_failed": return L("CSV保存に失敗しました", "CSV save failed", "Error al guardar CSV", "Échec de l'enregistrement CSV", "CSV 저장 실패", "CSV保存失败", "CSV儲存失敗", "CSV konnte nicht gespeichert werden");
            case "license_paid_title": return "Lifetime License";
            case "license_paid_text": return L("開発を支援いただきありがとうございます。全機能を利用できます。", "Thank you for supporting development. All features are unlocked.", "Gracias por apoyar el desarrollo. Todas las funciones están disponibles.", "Merci de soutenir le développement. Toutes les fonctions sont disponibles.", "개발을 지원해 주셔서 감사합니다. 모든 기능을 사용할 수 있습니다.", "感谢支持开发。全部功能已解锁。", "感謝支持開發。全部功能已解鎖。", "Danke für die Unterstützung. Alle Funktionen sind freigeschaltet.");
            case "license_free_title": return L("無料体験版", "Free Trial", "Prueba gratuita", "Essai gratuit", "무료 체험판", "免费试用", "免費試用", "Kostenlose Testversion");
            case "license_free_text": return L("履歴は5ラウンドまで。分析・PDF・CSV・バックアップはPro機能です。", "History is limited to five rounds. Analysis, PDF, CSV, and backup are Pro features.", "El historial se limita a cinco rondas. Análisis, PDF, CSV y copia son Pro.", "L'historique est limité à cinq parties. Analyse, PDF, CSV et sauvegarde sont Pro.", "기록은 5라운드까지입니다. 분석, PDF, CSV, 백업은 Pro 기능입니다.", "历史最多5局。分析、PDF、CSV和备份为Pro功能。", "歷史最多5局。分析、PDF、CSV和備份為Pro功能。", "Der Verlauf ist auf fünf Runden begrenzt. Analyse, PDF, CSV und Backup sind Pro-Funktionen.");
            case "saved_rounds": return L("保存ラウンド", "Saved rounds", "Rondas guardadas", "Parties enregistrées", "저장 라운드", "已保存球局", "已儲存球局", "Gespeicherte Runden");
            case "upgrade": return L("Lifetime Licenseにアップグレード", "Upgrade to Lifetime License", "Actualizar a licencia permanente", "Passer à la licence à vie", "평생 라이선스로 업그레이드", "升级永久许可", "升級永久授權", "Auf Dauerlizenz upgraden");
            case "test_pro": return L("テストではPro APKをインストールしてください", "Install the Pro APK for testing", "Instala el APK Pro para probar", "Installez l'APK Pro pour tester", "테스트용 Pro APK를 설치하세요", "请安装Pro APK进行测试", "請安裝Pro APK進行測試", "Installieren Sie zum Testen die Pro-APK");
            case "free_limit_reached": return L("無料版の保存上限5件に達しました", "The free five-round limit has been reached", "Se alcanzó el límite de cinco rondas", "La limite de cinq parties est atteinte", "무료 5라운드 한도에 도달했습니다", "已达到免费版5局上限", "已達到免費版5局上限", "Das Limit von fünf Runden wurde erreicht");
            case "locked": return L("Pro機能です。有料版APKで利用できます。", "This is a Pro feature. Use the Pro APK.", "Esta es una función Pro.", "Fonction Pro.", "Pro 기능입니다.", "这是Pro功能。", "這是Pro功能。", "Dies ist eine Pro-Funktion.");
            case "pro_feature": return L("Pro機能", "Pro feature", "Función Pro", "Fonction Pro", "Pro 기능", "Pro功能", "Pro功能", "Pro-Funktion");
            case "support": return L("サポート", "Support", "Soporte", "Assistance", "지원", "支持", "支援", "Support");
            case "review": return L("レビューを書く", "Write a review", "Escribir reseña", "Donner un avis", "리뷰 작성", "撰写评价", "撰寫評論", "Bewertung schreiben");
            case "contact": return L("お問い合わせ", "Contact", "Contacto", "Contact", "문의", "联系", "聯絡", "Kontakt");
            case "idea": return L("新機能を提案する", "Suggest a feature", "Sugerir función", "Proposer une fonction", "기능 제안", "建议新功能", "建議新功能", "Funktion vorschlagen");
            case "mail_subject_contact": return "Golf Scorecard Offline - " + t("contact");
            case "mail_subject_idea": return "Golf Scorecard Offline - " + t("idea");
            case "backup": return L("バックアップ / 復元", "Backup / Restore", "Copia / Restaurar", "Sauvegarde / Restauration", "백업 / 복원", "备份 / 恢复", "備份 / 復原", "Backup / Wiederherstellen");
            case "backup_note": return L("Google Driveなど任意の保存先へバックアップできます。", "Save a backup to Google Drive or any folder.", "Guarda una copia en Google Drive o cualquier carpeta.", "Sauvegardez sur Google Drive ou dans un dossier.", "Google Drive 또는 원하는 폴더에 백업합니다.", "可备份到Google Drive或任意文件夹。", "可備份到Google Drive或任意資料夾。", "Backup in Google Drive oder einem beliebigen Ordner speichern.");
            case "backup_locked": return L("バックアップと復元はPro機能です。", "Backup and restore are Pro features.", "Copia y restauración son Pro.", "Sauvegarde et restauration sont Pro.", "백업과 복원은 Pro 기능입니다.", "备份和恢复为Pro功能。", "備份和復原為Pro功能。", "Backup und Wiederherstellung sind Pro-Funktionen.");
            case "backup_save": return L("バックアップ保存", "Save backup", "Guardar copia", "Enregistrer la sauvegarde", "백업 저장", "保存备份", "儲存備份", "Backup speichern");
            case "restore_backup": return L("バックアップから復元", "Restore backup", "Restaurar copia", "Restaurer la sauvegarde", "백업 복원", "恢复备份", "復原備份", "Backup wiederherstellen");
            case "backup_saved": return L("バックアップを保存しました", "Backup saved", "Copia guardada", "Sauvegarde enregistrée", "백업 저장 완료", "备份已保存", "備份已儲存", "Backup gespeichert");
            case "backup_failed": return L("バックアップ保存に失敗しました", "Backup save failed", "Error al guardar copia", "Échec de la sauvegarde", "백업 저장 실패", "备份保存失败", "備份儲存失敗", "Backup konnte nicht gespeichert werden");
            case "restore_done": return L("復元しました", "Restored", "Restaurado", "Restauré", "복원 완료", "已恢复", "已復原", "Wiederhergestellt");
            case "restore_failed": return L("復元に失敗しました", "Restore failed", "Error al restaurar", "Échec de la restauration", "복원 실패", "恢复失败", "復原失敗", "Wiederherstellung fehlgeschlagen");
            case "pdf_saved": return L("PDFを保存しました", "PDF saved", "PDF guardado", "PDF enregistré", "PDF 저장 완료", "PDF已保存", "PDF已儲存", "PDF gespeichert");
            case "pdf_failed": return L("PDF保存に失敗しました", "PDF save failed", "Error al guardar PDF", "Échec de l'enregistrement PDF", "PDF 저장 실패", "PDF保存失败", "PDF儲存失敗", "PDF konnte nicht gespeichert werden");
            case "scorecard_title": return L("ゴルフスコアカード", "Golf Scorecard", "Tarjeta de golf", "Carte de score golf", "골프 스코어카드", "高尔夫记分卡", "高爾夫計分卡", "Golf-Scorekarte");
            case "store_unavailable": return L("ストアを開けませんでした", "Store unavailable", "Tienda no disponible", "Boutique indisponible", "스토어를 열 수 없습니다", "无法打开商店", "無法開啟商店", "Store nicht verfügbar");
            case "mail_unavailable": return L("メールアプリを開けませんでした", "Mail app unavailable", "Correo no disponible", "Application e-mail indisponible", "메일 앱을 열 수 없습니다", "无法打开邮件应用", "無法開啟郵件應用程式", "Mail-App nicht verfügbar");
            default: return key;
        }
    }

    private void writeText(Uri uri, String text, boolean bom, String success, String failure) { try { OutputStream out = getContentResolver().openOutputStream(uri); if (out == null) throw new Exception("cannot open output"); if (bom) out.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF}); out.write(text.getBytes(StandardCharsets.UTF_8)); out.close(); toast(success); } catch (Exception e) { toast(failure); } }
    private String read(Uri uri) throws Exception { InputStream in = getContentResolver().openInputStream(uri); if (in == null) throw new Exception("cannot open input"); ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] bytes = new byte[4096]; int count; while ((count = in.read(bytes)) > 0) buffer.write(bytes, 0, count); in.close(); return new String(buffer.toByteArray(), StandardCharsets.UTF_8); }
    private void createDocument(int request, String type, String name) { Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType(type); i.putExtra(Intent.EXTRA_TITLE, name); startActivityForResult(i, request); }
    private void openDocument() { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/*"); startActivityForResult(i, REQ_RESTORE); }
    private void openMarket() { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName()))); } catch (Exception e) { toast(t("store_unavailable")); } }
    private void mail(String subject) { Intent i = new Intent(Intent.ACTION_SENDTO); i.setData(Uri.parse("mailto:support@nk-ts.co.jp")); i.putExtra(Intent.EXTRA_SUBJECT, subject); try { startActivity(i); } catch (Exception e) { toast(t("mail_unavailable")); } }
    private boolean validDate(String value) { if (TextUtils.isEmpty(value)) return false; SimpleDateFormat f = new SimpleDateFormat("yyyy/MM/dd", Locale.US); f.setLenient(false); try { f.parse(value); return true; } catch (ParseException e) { return false; } }
    private String today() { return new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date()); }
    private String todayFile() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private int dateNumber(String value, int fallback) { try { String normalized = value == null ? "" : value.replace("/", "").replace("-", "").replace(".", "").trim(); if (normalized.length() >= 8) return Integer.parseInt(normalized.substring(0, 8)); } catch (Exception ignored) {} return fallback; }
    private String fileDate(String value, String fallback) { int n = dateNumber(value, -1); return n < 0 ? fallback : String.valueOf(n); }
    private int number(String value, int fallback) { try { return Integer.parseInt(value); } catch (Exception e) { return fallback; } }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private String one(double value) { return String.format(Locale.US, "%.1f", value); }
    private String safe(String value, String fallback) { return TextUtils.isEmpty(value) ? fallback : value; }
    private String shorten(String value, int max) { if (value == null) return ""; return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…"; }
    private String csv(String value) { return "\"" + (value == null ? "" : value.replace("\"", "\"\"").replace("\n", " ")) + "\""; }
    private String enc(String value) { return Base64.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP); }
    private String dec(String value) { try { return new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8); } catch (Exception e) { return ""; } }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private void top() { scroll.post(() -> scroll.fullScroll(View.FOCUS_UP)); }
    private void restoreScroll(int y) { scroll.post(() -> scroll.scrollTo(0, y)); }

    private String serializeInts(int[] values) { ArrayList<String> list = new ArrayList<>(); for (int value : values) list.add(String.valueOf(value)); return TextUtils.join(",", list); }
    private int[] deserializeInts(String raw, int length, int fallback) { int[] out = new int[length]; for (int i = 0; i < length; i++) out[i] = fallback; if (TextUtils.isEmpty(raw)) return out; String[] parts = raw.split(",", -1); for (int i = 0; i < length && i < parts.length; i++) out[i] = number(parts[i], fallback); return out; }
    private void restoreInts(String raw, int[] target, int[] defaults, int min, int max) { if (defaults != null) System.arraycopy(defaults, 0, target, 0, Math.min(defaults.length, target.length)); if (TextUtils.isEmpty(raw)) return; String[] parts = raw.split(",", -1); for (int i = 0; i < target.length && i < parts.length; i++) target[i] = clamp(number(parts[i], target[i]), min, max); }
    private String serializeStrings(String[] values) { ArrayList<String> list = new ArrayList<>(); for (String value : values) list.add(enc(value)); return TextUtils.join(",", list); }
    private String[] deserializeStrings(String raw, int length, String fallback) { String[] out = new String[length]; for (int i = 0; i < length; i++) out[i] = fallback; if (TextUtils.isEmpty(raw)) return out; String[] parts = raw.split(",", -1); for (int i = 0; i < length && i < parts.length; i++) out[i] = dec(parts[i]); return out; }
    private void restoreStrings(String raw, String[] target) { String[] values = deserializeStrings(raw, target.length, ""); System.arraycopy(values, 0, target, 0, target.length); }

    private static class Record {
        String date = "", course = "", tee = "", memo = "", names = "", pars = "", scores0 = "", scores1 = "", scores2 = "", scores3 = "", putts = "", teeResults = "";
        int activePlayers = 1;
        String toLine() { return encStatic(date) + "|" + encStatic(course) + "|" + encStatic(tee) + "|" + encStatic(memo) + "|" + activePlayers + "|" + encStatic(names) + "|" + encStatic(pars) + "|" + encStatic(scores0) + "|" + encStatic(scores1) + "|" + encStatic(scores2) + "|" + encStatic(scores3) + "|" + encStatic(putts) + "|" + encStatic(teeResults); }
        static Record from(String line) { try { if (TextUtils.isEmpty(line)) return null; String[] p = line.split("\\|", -1); if (p.length < 13) return null; Record r = new Record(); r.date = decStatic(p[0]); r.course = decStatic(p[1]); r.tee = decStatic(p[2]); r.memo = decStatic(p[3]); r.activePlayers = Math.max(1, Math.min(4, Integer.parseInt(p[4]))); r.names = decStatic(p[5]); r.pars = decStatic(p[6]); r.scores0 = decStatic(p[7]); r.scores1 = decStatic(p[8]); r.scores2 = decStatic(p[9]); r.scores3 = decStatic(p[10]); r.putts = decStatic(p[11]); r.teeResults = decStatic(p[12]); return r; } catch (Exception e) { return null; } }
        String[] getNames() { return deserializeStringsStatic(names, PLAYERS, ""); }
        int[] getPars() { return deserializeIntsStatic(pars, HOLES, 0); }
        int[] getPutts() { return deserializeIntsStatic(putts, HOLES, 0); }
        int[] getTeeResults() { return deserializeIntsStatic(teeResults, HOLES, 0); }
        int[] getScores(int player) { if (player == 0) return deserializeIntsStatic(scores0, HOLES, 0); if (player == 1) return deserializeIntsStatic(scores1, HOLES, 0); if (player == 2) return deserializeIntsStatic(scores2, HOLES, 0); return deserializeIntsStatic(scores3, HOLES, 0); }
    }

    private static class LegacyRecord {
        String date = "", course = "", tee = "", memo = "", pars = "", scores = "", puttData = "", teeData = "";
        static LegacyRecord from(String line) { try { if (TextUtils.isEmpty(line)) return null; String[] p = line.split("\\|", -1); if (p.length < 15) return null; LegacyRecord r = new LegacyRecord(); r.date = decStatic(p[0]); r.course = decStatic(p[1]); r.tee = decStatic(p[2]); r.memo = decStatic(p[3]); r.pars = decStatic(p[9]); r.scores = decStatic(p[10]); r.puttData = decStatic(p[11]); r.teeData = decStatic(p[12]); return r; } catch (Exception e) { return null; } }
    }

    private static String encStatic(String value) { return Base64.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP); }
    private static String decStatic(String value) { try { return new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8); } catch (Exception e) { return ""; } }
    private static int[] deserializeIntsStatic(String raw, int length, int fallback) { int[] out = new int[length]; for (int i = 0; i < length; i++) out[i] = fallback; if (TextUtils.isEmpty(raw)) return out; String[] parts = raw.split(",", -1); for (int i = 0; i < length && i < parts.length; i++) { try { out[i] = Integer.parseInt(parts[i]); } catch (Exception ignored) { out[i] = fallback; } } return out; }
    private static String[] deserializeStringsStatic(String raw, int length, String fallback) { String[] out = new String[length]; for (int i = 0; i < length; i++) out[i] = fallback; if (TextUtils.isEmpty(raw)) return out; String[] parts = raw.split(",", -1); for (int i = 0; i < length && i < parts.length; i++) out[i] = decStatic(parts[i]); return out; }
}
