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
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int HOLES = 18;
    private static final int PLAYERS = 4;
    private static final int MAX_SCORE = 15;
    private static final int FREE_LIMIT = 5;
    private static final int REQ_CSV = 401;
    private static final int REQ_BACKUP = 402;
    private static final int REQ_RESTORE = 403;
    private static final int REQ_PDF = 404;
    private static final String PREF = "gso_v212";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_LANG = "lang";

    private static final int C_BG = 0xFFF7FAFC;
    private static final int C_CARD = 0xFFFFFFFF;
    private static final int C_TEXT = 0xFF0F172A;
    private static final int C_MUTED = 0xFF64748B;
    private static final int C_BORDER = 0xFFDDE5EF;
    private static final int C_GREEN = 0xFF166534;
    private static final int C_GREEN_D = 0xFF14532D;
    private static final int C_SOFT = 0xFFE9F8EF;
    private static final int C_LOCK = 0xFFFFF7ED;

    private final String[] langCodes = {"ja", "en", "es", "fr", "ko", "zh", "tw", "de"};
    private final String[] langNames = {"日本語", "English", "Español", "Français", "한국어", "简体中文", "繁體中文", "Deutsch"};
    private final String[] langLabels = {"JP", "EN", "ES", "FR", "KO", "简", "繁", "DE"};
    private final int[] defaultPars = {4,4,3,5,4,4,5,3,4,4,5,4,3,4,4,5,3,4};

    private ScrollView scroll;
    private LinearLayout root;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveRun = () -> saveDraft(false);

    private String lang = "";
    private String roundDate = "";
    private String course = "";
    private String tee = "";
    private String startMemo = "";
    private String csvFrom = "";
    private String csvTo = "";
    private String selectedPdfText = "";
    private int screen = 0;
    private int currentHole = 0;
    private int activePlayers = 1;
    private int tensPlayer = -1;
    private boolean inRound = false;
    private boolean savePending = false;
    private boolean confirmCancel = false;

    private final int[] pars = new int[HOLES];
    private final int[][] scores = new int[PLAYERS][HOLES];
    private final int[] putts = new int[HOLES];
    private final int[] teeResult = new int[HOLES];
    private final String[] names = {"Player 1", "Player 2", "Player 3", "Player 4"};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        initPars();
        loadDraft();
        setContentView(base());
        if (TextUtils.isEmpty(lang)) renderLanguageSelect();
        else renderHome();
    }

    @Override protected void onPause() { flushSave(); super.onPause(); }
    @Override protected void onStop() { flushSave(); super.onStop(); }
    @Override protected void onDestroy() { flushSave(); super.onDestroy(); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_CSV) writeText(data.getData(), buildCsv(), true, t("csv_saved"), t("csv_failed"));
        else if (requestCode == REQ_BACKUP) writeText(data.getData(), buildBackup(), false, t("backup_saved"), t("backup_failed"));
        else if (requestCode == REQ_RESTORE) restoreBackup(data.getData());
        else if (requestCode == REQ_PDF) writePdf(data.getData());
    }

    private boolean paid() { return BuildConfig.PAID_EDITION; }
    private SharedPreferences prefs() { return getSharedPreferences(PREF, MODE_PRIVATE); }

    private View base() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(C_BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(20));
        scroll.addView(root);
        return scroll;
    }

    private void renderLanguageSelect() {
        screen = 5;
        root.removeAllViews();
        root.addView(hero("Golf Scorecard Offline", "Language / 言語"));
        LinearLayout c = card();
        c.addView(section("Language"));
        c.addView(info("Please choose your language. You can change it later at the bottom of the app.", false));
        for (int i = 0; i < langCodes.length; i++) {
            final String code = langCodes[i];
            Button b = primary(langNames[i]);
            b.setOnClickListener(v -> {
                lang = code;
                prefs().edit().putString(KEY_LANG, lang).apply();
                renderHome();
            });
            c.addView(b, full());
        }
        root.addView(c);
        top();
    }

    private void renderHome() {
        screen = 0;
        inRound = false;
        saveDraft(false);
        root.removeAllViews();
        root.addView(hero(t("app_title"), paid() ? t("edition_paid") : t("edition_free")));
        root.addView(licenseCard());
        LinearLayout c = card();
        c.addView(section(t("round")));
        Button start = primary(t("start_round"));
        start.setOnClickListener(v -> { newRound(); renderRound(false); });
        c.addView(start, full());
        if (hasDraft()) {
            Button resume = secondary(t("resume_round"));
            resume.setOnClickListener(v -> renderRound(false));
            c.addView(resume, full());
        }
        Button history = secondary(t("history")); history.setOnClickListener(v -> renderHistory()); c.addView(history, full());
        Button analysis = secondary(t("analysis")); analysis.setOnClickListener(v -> renderAnalysis()); c.addView(analysis, full());
        Button settings = secondary(t("settings")); settings.setOnClickListener(v -> renderSettings()); c.addView(settings, full());
        root.addView(c);
        root.addView(csvCard());
        root.addView(statsCard());
        nav();
        top();
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
            Button up = primary(t("upgrade"));
            up.setOnClickListener(v -> toast(t("test_pro")));
            c.addView(up, full());
        }
        return c;
    }

    private View csvCard() {
        if (!paid()) return locked(t("csv_export"));
        LinearLayout c = card();
        c.addView(section(t("csv_export")));
        c.addView(info(t("csv_note"), false));
        EditText from = input("YYYY/MM/DD  " + t("from_date"));
        from.setText(csvFrom);
        from.addTextChangedListener(w(s -> { csvFrom = s; prefs().edit().putString("csvFrom", csvFrom).apply(); }));
        c.addView(from);
        EditText to = input("YYYY/MM/DD  " + t("to_date"));
        to.setText(csvTo);
        to.addTextChangedListener(w(s -> { csvTo = s; prefs().edit().putString("csvTo", csvTo).apply(); }));
        c.addView(to);
        Button save = primary(t("csv_save"));
        save.setOnClickListener(v -> createDoc(REQ_CSV, "text/csv", "GolfScore_" + fileDate(csvFrom, "from") + "_" + fileDate(csvTo, "to") + ".csv"));
        c.addView(save, full());
        return c;
    }

    private View statsCard() {
        LinearLayout c = card();
        c.addView(section(t("recent")));
        c.addView(info(stats(loadHistory()), true));
        return c;
    }

    private View locked(String title) {
        LinearLayout c = card();
        c.addView(section(title));
        c.addView(info(t("locked"), false));
        Button b = primary(t("upgrade"));
        b.setOnClickListener(v -> toast(t("test_pro")));
        c.addView(b, full());
        return c;
    }

    private void renderRound(boolean keep) {
        screen = 1;
        inRound = true;
        int y = keep ? scroll.getScrollY() : 0;
        root.removeAllViews();
        LinearLayout head = card();
        TextView title = text((currentHole + 1) + "H  PAR" + pars[currentHole], 38, C_TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title);
        head.addView(text(roundDate + "  " + safe(course, t("course_empty")), 17, C_MUTED, false));
        root.addView(head);
        root.addView(roundSettings());
        root.addView(holeChooser());
        root.addView(scoreInput());
        root.addView(finishCard());
        langFooter();
        if (keep) restore(y); else top();
    }

    private View roundSettings() {
        LinearLayout c = card();
        c.addView(section(t("round_settings")));
        EditText d = input("YYYY/MM/DD  " + t("round_date"));
        d.setText(roundDate);
        d.addTextChangedListener(w(s -> { roundDate = s; reqSave(); }));
        c.addView(d);
        EditText co = input(t("course")); co.setText(course); co.addTextChangedListener(w(s -> { course = s; reqSave(); })); c.addView(co);
        EditText te = input(t("tee")); te.setText(tee); te.addTextChangedListener(w(s -> { tee = s; reqSave(); })); c.addView(te);
        EditText st = input(t("start_memo")); st.setText(startMemo); st.addTextChangedListener(w(s -> { startMemo = s; reqSave(); })); c.addView(st);
        c.addView(text(t("players"), 18, C_TEXT, true));
        LinearLayout r = row();
        for (int i = 1; i <= 4; i++) {
            final int n = i;
            Button b = choice(String.valueOf(i), activePlayers == i);
            b.setOnClickListener(v -> { activePlayers = n; reqSave(); renderRound(true); });
            r.addView(b, weight());
        }
        c.addView(r);
        for (int p = 0; p < activePlayers; p++) {
            final int idx = p;
            EditText nm = input(p == 0 ? t("player") + " 1" : t("player") + " " + (p + 1));
            nm.setText(names[p]);
            nm.addTextChangedListener(w(s -> { names[idx] = s; reqSave(); }));
            c.addView(nm);
        }
        return c;
    }

    private View holeChooser() {
        LinearLayout c = card();
        c.addView(section(t("progress")));
        c.addView(text(t("player") + "1 " + entered(0) + "/18  /  " + t("missing") + " " + missing(), 19, C_TEXT, true));
        for (int r = 0; r < 3; r++) {
            LinearLayout line = row();
            for (int col = 0; col < 6; col++) {
                int h = r * 6 + col;
                Button b = choice(String.valueOf(h + 1), h == currentHole);
                b.setTextSize(20);
                b.setMinHeight(dp(56));
                final int target = h;
                b.setOnClickListener(v -> { currentHole = target; tensPlayer = -1; reqSave(); renderRound(false); });
                line.addView(b, weight());
            }
            c.addView(line);
        }
        return c;
    }

    private View scoreInput() {
        LinearLayout c = card();
        c.addView(section(t("score_input")));
        LinearLayout par = row();
        for (int x = 3; x <= 6; x++) {
            final int value = x;
            Button b = choice("PAR " + x, pars[currentHole] == x);
            b.setTextSize(20);
            b.setOnClickListener(v -> { pars[currentHole] = value; reqSave(); renderRound(true); });
            par.addView(b, weight());
        }
        c.addView(par);
        for (int p = 0; p < activePlayers; p++) c.addView(playerScore(p));
        LinearLayout nav = row();
        Button prev = secondary(t("prev"));
        prev.setEnabled(currentHole > 0);
        prev.setOnClickListener(v -> { currentHole--; reqSave(); renderRound(false); });
        Button next = primary(t("next"));
        next.setEnabled(currentHole < HOLES - 1);
        next.setOnClickListener(v -> { currentHole++; reqSave(); renderRound(false); });
        nav.addView(prev, weight()); nav.addView(next, weight()); c.addView(nav);
        return c;
    }

    private View playerScore(int p) {
        LinearLayout c = lite();
        String label = safe(names[p], t("player") + " " + (p + 1)) + "  SCORE " + (scores[p][currentHole] == 0 ? "-" : scores[p][currentHole]) + (tensPlayer == p ? "  10+" : "");
        c.addView(text(label, 28, C_TEXT, true));
        String[][] keys = {{"1","2","3"},{"4","5","6"},{"7","8","9"},{"1+","0",t("clear")}};
        for (String[] ks : keys) {
            LinearLayout r = row();
            for (String k : ks) {
                Button b = choice(k, false);
                b.setTextSize(28);
                b.setMinHeight(dp(72));
                b.setOnClickListener(v -> scoreKey(p, k));
                r.addView(b, weight());
            }
            c.addView(r);
        }
        if (p == 0) {
            c.addView(text("PAT", 16, C_MUTED, true));
            LinearLayout pat = row();
            for (int i = 1; i <= 4; i++) {
                final int v = i;
                Button b = choice(i == 4 ? "4+" : String.valueOf(i), putts[currentHole] == i);
                b.setTextSize(24);
                b.setOnClickListener(x -> { putts[currentHole] = v; reqSave(); renderRound(true); });
                pat.addView(b, weight());
            }
            c.addView(pat);
            c.addView(text(t("tee_result"), 16, C_MUTED, true));
            LinearLayout tr = row();
            String[] labs = {"-", "FW", t("left_short"), t("right_short"), "L-OB", "R-OB"};
            for (int i = 0; i < labs.length; i++) {
                final int v = i;
                Button b = choice(labs[i], teeResult[currentHole] == i);
                b.setOnClickListener(x -> { teeResult[currentHole] = v; reqSave(); renderRound(true); });
                tr.addView(b, weight());
            }
            c.addView(tr);
        }
        return c;
    }

    private void scoreKey(int p, String key) {
        if (key.equals(t("clear"))) { scores[p][currentHole] = 0; tensPlayer = -1; reqSave(); renderRound(true); return; }
        if ("1+".equals(key)) { tensPlayer = p; renderRound(true); return; }
        int d = num(key, -1); if (d < 0) return;
        if (tensPlayer == p) {
            if (d <= 5) { scores[p][currentHole] = 10 + d; tensPlayer = -1; }
            else toast(t("ten_error"));
        } else scores[p][currentHole] = d == 0 ? 0 : d;
        reqSave();
        renderRound(true);
    }

    private View finishCard() {
        LinearLayout c = card();
        c.addView(section(t("finish")));
        Button save = primary(t("save_round"));
        save.setOnClickListener(v -> finishRound());
        c.addView(save, full());
        Button cancel = secondary(t("cancel"));
        cancel.setOnClickListener(v -> { confirmCancel = true; renderRound(true); });
        c.addView(cancel, full());
        if (confirmCancel) {
            c.addView(info(t("cancel_confirm"), false));
            LinearLayout r = row();
            Button back = primary(t("back_input"));
            back.setOnClickListener(v -> { confirmCancel = false; renderRound(true); });
            Button home = secondary(t("back_home"));
            home.setOnClickListener(v -> { inRound = false; saveDraft(false); renderHome(); });
            r.addView(back, weight()); r.addView(home, weight()); c.addView(r);
        }
        return c;
    }

    private void finishRound() {
        flushSave();
        if (TextUtils.isEmpty(roundDate)) roundDate = today();
        if (missing() > 0) { toast(t("missing") + ": " + missing()); return; }
        ArrayList<Record> list = loadHistory();
        if (!paid() && list.size() >= FREE_LIMIT) { toast(t("free_limit_reached")); renderHome(); return; }
        Record r = new Record();
        r.date = roundDate;
        r.course = safe(course, t("course_empty"));
        r.tee = tee;
        r.memo = startMemo;
        r.total = total(0);
        r.putts = sum(putts);
        r.fw = count(teeResult, 1);
        r.teeShots = countTeeShots();
        r.ob = count(teeResult, 4) + count(teeResult, 5);
        r.pars = ser(pars);
        r.scores = ser(scores[0]);
        r.puttData = ser(putts);
        r.teeData = ser(teeResult);
        r.card = scorecard();
        r.analysis = analysisText(r);
        list.add(0, r);
        saveHistory(list);
        inRound = false;
        saveDraft(false);
        selectedPdfText = r.card + (paid() ? "\n\n" + r.analysis : "\n\n" + t("analysis_locked"));
        renderHistory();
    }

    private void renderHistory() {
        screen = 2;
        inRound = false;
        saveDraft(false);
        root.removeAllViews();
        root.addView(hero(t("history"), t("history_sub")));
        LinearLayout c = card();
        ArrayList<Record> list = loadHistory();
        if (list.isEmpty()) c.addView(info(t("no_history"), false));
        for (Record r : list) {
            LinearLayout item = lite();
            item.addView(text(r.date + "  " + r.course + "  SCORE " + r.total + "  PAT " + r.putts, 18, C_TEXT, true));
            LinearLayout buttons = row();
            Button detail = secondary(t("detail"));
            detail.setOnClickListener(v -> { selectedPdfText = r.card + (paid() ? "\n\n" + r.analysis : "\n\n" + t("analysis_locked")); renderHistory(); });
            Button pdf = primary(t("pdf_save"));
            pdf.setOnClickListener(v -> { selectedPdfText = r.card + (paid() ? "\n\n" + r.analysis : ""); createDoc(REQ_PDF, "application/pdf", "GolfScore_" + fileDate(r.date, "date") + ".pdf"); });
            buttons.addView(detail, weight()); buttons.addView(pdf, weight()); item.addView(buttons); c.addView(item);
        }
        if (!TextUtils.isEmpty(selectedPdfText)) c.addView(info(selectedPdfText, false));
        root.addView(c);
        nav();
        top();
    }

    private void renderAnalysis() {
        screen = 3;
        root.removeAllViews();
        root.addView(hero(t("analysis"), paid() ? t("analysis_sub") : t("pro_feature")));
        if (!paid()) { root.addView(locked(t("analysis"))); nav(); top(); return; }
        LinearLayout c = card();
        c.addView(info(stats(loadHistory()) + "\n\n" + trend(loadHistory()), true));
        root.addView(c);
        nav();
        top();
    }

    private void renderSettings() {
        screen = 4;
        root.removeAllViews();
        root.addView(hero(t("settings"), paid() ? t("edition_paid") : t("edition_free")));
        LinearLayout support = card();
        support.addView(section(t("support")));
        Button review = secondary(t("review")); review.setOnClickListener(v -> openMarket()); support.addView(review, full());
        Button contact = secondary(t("contact")); contact.setOnClickListener(v -> mail(t("mail_subject_contact"))); support.addView(contact, full());
        Button idea = secondary(t("idea")); idea.setOnClickListener(v -> mail(t("mail_subject_idea"))); support.addView(idea, full());
        root.addView(support);
        LinearLayout data = card();
        data.addView(section(t("backup")));
        if (!paid()) data.addView(info(t("backup_locked"), false));
        else {
            data.addView(info(t("backup_note"), false));
            Button b = secondary(t("backup_save")); b.setOnClickListener(v -> createDoc(REQ_BACKUP, "text/plain", "GolfScore_Backup_" + todayFile() + ".txt")); data.addView(b, full());
            Button r = primary(t("restore_backup")); r.setOnClickListener(v -> openDoc()); data.addView(r, full());
        }
        root.addView(data);
        nav();
        top();
    }

    private String scorecard() {
        StringBuilder b = new StringBuilder();
        b.append(t("scorecard_title")).append("\n");
        b.append(t("date")).append(": ").append(roundDate).append("\n");
        b.append(t("course")).append(": ").append(safe(course, t("course_empty"))).append("\n");
        b.append(t("tee")).append(": ").append(tee).append("\n");
        b.append(t("memo")).append(": ").append(startMemo).append("\n\n");
        b.append("HOLE,1,2,3,4,5,6,7,8,9,OUT,10,11,12,13,14,15,16,17,18,IN,TOTAL\n");
        b.append("PAR,").append(rowCsv(pars, false)).append("\n");
        for (int p = 0; p < activePlayers; p++) b.append(safe(names[p], t("player") + " " + (p + 1))).append(",").append(rowCsv(scores[p], true)).append("\n");
        b.append("\nPAT,").append(sum(putts)).append("\nFW,").append(count(teeResult, 1)).append("/").append(countTeeShots()).append("\nOB,").append(count(teeResult, 4) + count(teeResult, 5)).append("\n\nScored with Golf Scorecard Offline");
        return b.toString();
    }

    private String analysisText(Record r) {
        return t("today_analysis") + "\nSCORE: " + r.total + "\nPAT: " + r.putts + "\nFW: " + r.fw + "/" + r.teeShots + "\nOB: " + r.ob + "\n" + (r.ob >= 2 ? t("ob_advice") : t("good_record"));
    }

    private String buildCsv() {
        StringBuilder b = new StringBuilder("Date,Course,Tee,Memo,Total,Putts,FW,TeeShots,OB");
        for (int i = 1; i <= HOLES; i++) b.append(",H").append(i);
        for (int i = 1; i <= HOLES; i++) b.append(",Par").append(i);
        b.append("\n");
        int from = dateNum(csvFrom, 0), to = dateNum(csvTo, 99999999);
        for (Record r : loadHistory()) {
            int d = dateNum(r.date, 0);
            if (d < from || d > to) continue;
            int[] sc = deserInt(r.scores, HOLES, 0);
            int[] pa = deserInt(r.pars, HOLES, 0);
            b.append(csv(r.date)).append(',').append(csv(r.course)).append(',').append(csv(r.tee)).append(',').append(csv(r.memo)).append(',').append(r.total).append(',').append(r.putts).append(',').append(r.fw).append(',').append(r.teeShots).append(',').append(r.ob);
            for (int v : sc) b.append(',').append(v);
            for (int v : pa) b.append(',').append(v);
            b.append("\n");
        }
        return b.toString();
    }

    private String buildBackup() { return "GSO_BACKUP_V212\n" + enc(prefs().getString(KEY_HISTORY, "")) + "\n"; }
    private void restoreBackup(Uri uri) { try { String raw = read(uri); if (!raw.startsWith("GSO_BACKUP_")) throw new Exception(); String[] lines = raw.split("\n", 2); prefs().edit().putString(KEY_HISTORY, lines.length > 1 ? dec(lines[1].trim()) : "").apply(); toast(t("restore_done")); renderHome(); } catch (Exception e) { toast(t("restore_failed")); } }

    private void writePdf(Uri uri) {
        try {
            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new Exception();
            PdfDocument pdf = new PdfDocument();
            Paint p = new Paint(); p.setAntiAlias(true); p.setTextSize(14);
            Paint title = new Paint(); title.setAntiAlias(true); title.setTypeface(Typeface.DEFAULT_BOLD); title.setTextSize(22);
            int w = 842, h = 595, m = 28, y = 30, pageNo = 1;
            PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(w, h, pageNo).create());
            Canvas c = page.getCanvas(); c.drawText("Golf Scorecard Offline", m, y, title); y += 32;
            for (String line : selectedPdfText.split("\n", -1)) {
                if (y > h - m) { pdf.finishPage(page); page = pdf.startPage(new PdfDocument.PageInfo.Builder(w, h, ++pageNo).create()); c = page.getCanvas(); y = 30; }
                c.drawText(line, m, y, p); y += 18;
            }
            pdf.finishPage(page); pdf.writeTo(out); pdf.close(); out.close(); toast(t("pdf_saved"));
        } catch (Exception e) { toast(t("pdf_failed")); }
    }

    private void writeText(Uri uri, String text, boolean bom, String ok, String ng) {
        try { OutputStream out = getContentResolver().openOutputStream(uri); if (out == null) throw new Exception(); if (bom) out.write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF}); out.write(text.getBytes(StandardCharsets.UTF_8)); out.close(); toast(ok); }
        catch (Exception e) { toast(ng); }
    }
    private String read(Uri uri) throws Exception { InputStream in = getContentResolver().openInputStream(uri); if (in == null) throw new Exception(); ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n; while ((n = in.read(buf)) > 0) b.write(buf, 0, n); in.close(); return new String(b.toByteArray(), StandardCharsets.UTF_8); }
    private void createDoc(int req, String type, String name) { Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType(type); i.putExtra(Intent.EXTRA_TITLE, name); startActivityForResult(i, req); }
    private void openDoc() { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/*"); startActivityForResult(i, REQ_RESTORE); }
    private void openMarket() { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName()))); } catch (Exception e) { toast(t("store_unavailable")); } }
    private void mail(String subject) { Intent i = new Intent(Intent.ACTION_SENDTO); i.setData(Uri.parse("mailto:support@nk-ts.co.jp")); i.putExtra(Intent.EXTRA_SUBJECT, subject); try { startActivity(i); } catch (Exception e) { toast(t("mail_unavailable")); } }

    private void newRound() {
        inRound = true; roundDate = today(); course = ""; tee = ""; startMemo = ""; currentHole = 0; activePlayers = 1; tensPlayer = -1; confirmCancel = false;
        for (int p = 0; p < PLAYERS; p++) { names[p] = t("player") + " " + (p + 1); for (int h = 0; h < HOLES; h++) scores[p][h] = 0; }
        initPars();
        for (int h = 0; h < HOLES; h++) { putts[h] = 0; teeResult[h] = 0; }
        saveDraft(false);
    }

    private void initPars() { System.arraycopy(defaultPars, 0, pars, 0, HOLES); }
    private void loadDraft() {
        SharedPreferences p = prefs();
        lang = p.getString(KEY_LANG, "");
        csvFrom = p.getString("csvFrom", ""); csvTo = p.getString("csvTo", "");
        inRound = p.getBoolean("inRound", false); roundDate = p.getString("roundDate", today()); course = p.getString("course", ""); tee = p.getString("tee", ""); startMemo = p.getString("memo", "");
        currentHole = clamp(p.getInt("hole", 0), 0, HOLES - 1); activePlayers = clamp(p.getInt("players", 1), 1, PLAYERS);
        restoreInt(p.getString("pars", ""), pars, defaultPars, 3, 6); restoreInt(p.getString("putts", ""), putts, null, 0, 9); restoreInt(p.getString("teeResult", ""), teeResult, null, 0, 5); restoreStr(p.getString("names", ""), names);
        for (int i = 0; i < PLAYERS; i++) restoreInt(p.getString("scores" + i, ""), scores[i], null, 0, MAX_SCORE);
    }
    private void saveDraft(boolean show) {
        savePending = false;
        SharedPreferences.Editor e = prefs().edit();
        e.putString(KEY_LANG, lang).putBoolean("inRound", inRound).putString("roundDate", roundDate).putString("course", course).putString("tee", tee).putString("memo", startMemo).putInt("hole", currentHole).putInt("players", activePlayers).putString("pars", ser(pars)).putString("putts", ser(putts)).putString("teeResult", ser(teeResult)).putString("names", ser(names));
        for (int i = 0; i < PLAYERS; i++) e.putString("scores" + i, ser(scores[i]));
        e.apply();
        if (show) toast(t("saved"));
    }
    private void reqSave() { savePending = true; handler.removeCallbacks(saveRun); handler.postDelayed(saveRun, 500); }
    private void flushSave() { handler.removeCallbacks(saveRun); if (savePending) saveDraft(false); }

    private ArrayList<Record> loadHistory() { ArrayList<Record> list = new ArrayList<>(); String raw = prefs().getString(KEY_HISTORY, ""); if (TextUtils.isEmpty(raw)) return list; for (String line : raw.split("\n", -1)) { Record r = Record.from(line); if (r != null) list.add(r); } return list; }
    private void saveHistory(ArrayList<Record> list) { ArrayList<String> out = new ArrayList<>(); int max = paid() ? 400 : FREE_LIMIT; for (int i = 0; i < list.size() && i < max; i++) out.add(list.get(i).line()); prefs().edit().putString(KEY_HISTORY, TextUtils.join("\n", out)).apply(); }

    private String stats(ArrayList<Record> list) { if (list.isEmpty()) return t("no_data"); int total = 0, put = 0, fw = 0, ts = 0; for (Record r : list) { total += r.total; put += r.putts; fw += r.fw; ts += r.teeShots; } return t("rounds") + ": " + list.size() + "\n" + t("avg_score") + ": " + one(total * 1.0 / list.size()) + "\n" + t("avg_pat") + ": " + one(put * 1.0 / list.size()) + "\nFW: " + (ts == 0 ? "-" : one(fw * 100.0 / ts) + "%"); }
    private String trend(ArrayList<Record> list) { if (list.isEmpty()) return t("no_data"); Record r = list.get(0); return t("latest_round") + "\n" + r.date + "  " + r.course + "\nSCORE " + r.total + " / PAT " + r.putts + " / OB " + r.ob; }
    private int entered(int p) { int n = 0; for (int v : scores[p]) if (v > 0) n++; return n; }
    private int missing() { int m = 0; for (int p = 0; p < activePlayers; p++) for (int h = 0; h < HOLES; h++) if (scores[p][h] == 0) m++; return m; }
    private int total(int p) { int s = 0; for (int v : scores[p]) s += v; return s; }
    private int sum(int[] a) { int s = 0; for (int v : a) s += v; return s; }
    private int count(int[] a, int x) { int n = 0; for (int v : a) if (v == x) n++; return n; }
    private int countTeeShots() { int n = 0; for (int v : teeResult) if (v > 0) n++; return n; }
    private boolean hasDraft() { if (!inRound) return false; for (int p = 0; p < PLAYERS; p++) for (int h = 0; h < HOLES; h++) if (scores[p][h] > 0) return true; return !TextUtils.isEmpty(course) || !TextUtils.isEmpty(tee); }
    private String rowCsv(int[] values, boolean blankZero) { StringBuilder b = new StringBuilder(); int out = 0, in = 0, total = 0; for (int i = 0; i < HOLES; i++) { if (i > 0) b.append(','); int v = values[i]; b.append(blankZero && v == 0 ? "" : String.valueOf(v)); if (i < 9) out += v; else in += v; total += v; if (i == 8) b.append(',').append(out); } return b.append(',').append(in).append(',').append(total).toString(); }

    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(14), dp(14), dp(14), dp(14)); v.setBackground(bg(C_CARD, C_BORDER, 18)); LinearLayout.LayoutParams p = full(); p.setMargins(0, dp(6), 0, dp(6)); v.setLayoutParams(p); return v; }
    private LinearLayout lite() { LinearLayout v = card(); v.setBackground(bg(0xFFF9FBFD, C_BORDER, 16)); return v; }
    private View hero(String title, String sub) { LinearLayout c = card(); TextView t = text(title, 26, C_TEXT, true); t.setGravity(Gravity.CENTER); c.addView(t); TextView s = text(sub, 16, C_MUTED, false); s.setGravity(Gravity.CENTER); c.addView(s); return c; }
    private TextView section(String s) { TextView v = text(s, 20, C_TEXT, true); v.setPadding(0, 0, 0, dp(8)); return v; }
    private TextView info(String s, boolean strong) { TextView v = text(s, strong ? 18 : 16, C_TEXT, strong); v.setPadding(dp(12), dp(12), dp(12), dp(12)); v.setBackground(bg(strong ? C_SOFT : C_LOCK, C_BORDER, 14)); return v; }
    private TextView text(String s, int sz, int col, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sz); v.setTextColor(col); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private EditText input(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setTextSize(18); e.setSingleLine(true); e.setTextColor(C_TEXT); e.setPadding(dp(8), dp(8), dp(8), dp(8)); return e; }
    private Button primary(String s) { Button b = btn(s); b.setBackground(bg(C_GREEN, C_GREEN_D, 14)); b.setTextColor(0xFFFFFFFF); return b; }
    private Button secondary(String s) { Button b = btn(s); b.setBackground(bg(C_CARD, C_BORDER, 14)); b.setTextColor(C_TEXT); return b; }
    private Button choice(String s, boolean on) { Button b = btn(s); b.setBackground(bg(on ? C_GREEN : C_CARD, on ? C_GREEN_D : C_BORDER, 14)); b.setTextColor(on ? 0xFFFFFFFF : C_TEXT); return b; }
    private Button btn(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(17); b.setMinHeight(dp(54)); return b; }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1); p.setMargins(dp(2), dp(2), dp(2), dp(2)); return p; }
    private GradientDrawable bg(int fill, int stroke, int rad) { GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(rad)); g.setStroke(dp(1), stroke); return g; }
    private void nav() { LinearLayout n = row(); Button h = secondary(t("home")); h.setOnClickListener(v -> renderHome()); Button hi = secondary(t("history")); hi.setOnClickListener(v -> renderHistory()); Button a = secondary(t("analysis")); a.setOnClickListener(v -> renderAnalysis()); Button s = secondary(t("settings")); s.setOnClickListener(v -> renderSettings()); n.addView(h, weight()); n.addView(hi, weight()); n.addView(a, weight()); n.addView(s, weight()); root.addView(n); langFooter(); }
    private void langFooter() { LinearLayout c = card(); c.addView(section(t("language"))); LinearLayout r = row(); for (int i = 0; i < langCodes.length; i++) { final String code = langCodes[i]; Button b = choice(langLabels[i], code.equals(lang)); b.setTextSize(10); b.setMinHeight(dp(42)); b.setOnClickListener(v -> { lang = code; prefs().edit().putString(KEY_LANG, lang).apply(); if (screen == 1) renderRound(true); else if (screen == 2) renderHistory(); else if (screen == 3) renderAnalysis(); else if (screen == 4) renderSettings(); else renderHome(); }); r.addView(b, weight()); } c.addView(r); root.addView(c); }

    private TextWatcher w(Sink s) { return new TextWatcher() { public void beforeTextChanged(CharSequence a, int b, int c, int d) {} public void onTextChanged(CharSequence a, int b, int c, int d) { s.set(a == null ? "" : a.toString()); } public void afterTextChanged(Editable e) {} }; }
    private interface Sink { void set(String s); }

    private int idx() { for (int i = 0; i < langCodes.length; i++) if (langCodes[i].equals(lang)) return i; return 1; }
    private String L(String ja, String en, String es, String fr, String ko, String zh, String tw, String de) { String[] a = {ja, en, es, fr, ko, zh, tw, de}; return a[idx()]; }
    private String t(String k) {
        switch (k) {
            case "app_title": return L("ゴルフスコアカード オフライン", "Golf Scorecard Offline", "Tarjeta de golf offline", "Carte de score golf hors ligne", "오프라인 골프 스코어카드", "离线高尔夫记分卡", "離線高爾夫計分卡", "Golf-Scorekarte offline");
            case "language": return L("言語", "Language", "Idioma", "Langue", "언어", "语言", "語言", "Sprache");
            case "edition_paid": return L("Pro / 永久ライセンス", "Pro / Lifetime License", "Pro / Licencia de por vida", "Pro / Licence à vie", "Pro / 평생 라이선스", "专业版 / 终身许可", "專業版 / 終身授權", "Pro / Lebenslange Lizenz");
            case "edition_free": return L("無料体験版", "Free Trial", "Versión gratuita", "Version gratuite", "무료 체험판", "免费体验版", "免費體驗版", "Kostenlose Testversion");
            case "license_paid_title": return L("永久ライセンス", "Lifetime License", "Licencia de por vida", "Licence à vie", "평생 라이선스", "终身许可", "終身授權", "Lebenslange Lizenz");
            case "license_paid_text": return L("開発を応援いただきありがとうございます。すべての機能を利用できます。", "Thank you for supporting development. All features are unlocked.", "Gracias por apoyar el desarrollo. Todas las funciones están desbloqueadas.", "Merci de soutenir le développement. Toutes les fonctions sont débloquées.", "개발을 지원해 주셔서 감사합니다. 모든 기능을 사용할 수 있습니다.", "感谢支持开发。所有功能均已解锁。", "感謝支持開發。所有功能皆已解鎖。", "Danke für die Unterstützung der Entwicklung. Alle Funktionen sind freigeschaltet.");
            case "license_free_title": return L("無料版", "Free Version", "Versión gratuita", "Version gratuite", "무료 버전", "免费版", "免費版", "Kostenlose Version");
            case "license_free_text": return L("Proでは履歴無制限、分析、CSV、PDF保存、バックアップ/復元が使えます。", "Pro unlocks unlimited history, analysis, CSV, PDF save, backup and restore.", "Pro desbloquea historial ilimitado, análisis, CSV, PDF, copia de seguridad y restauración.", "Pro débloque l’historique illimité, l’analyse, CSV, PDF, sauvegarde et restauration.", "Pro에서는 무제한 기록, 분석, CSV, PDF 저장, 백업 및 복원이 가능합니다.", "专业版可解锁无限历史、分析、CSV、PDF保存、备份和恢复。", "專業版可解鎖無限歷史、分析、CSV、PDF儲存、備份與還原。", "Pro schaltet unbegrenzten Verlauf, Analyse, CSV, PDF, Backup und Wiederherstellung frei.");
            case "saved_rounds": return L("保存済みラウンド", "Saved rounds", "Rondas guardadas", "Parties enregistrées", "저장된 라운드", "已保存轮次", "已儲存回合", "Gespeicherte Runden");
            case "upgrade": return L("永久ライセンスにアップグレード", "Upgrade to Lifetime License", "Actualizar a licencia de por vida", "Passer à la licence à vie", "평생 라이선스로 업그레이드", "升级到终身许可", "升級至終身授權", "Auf lebenslange Lizenz upgraden");
            case "test_pro": return L("テスト版：有料版APKをインストールしてください。", "Test build: install the Pro APK.", "Versión de prueba: instala el APK Pro.", "Version test : installez l’APK Pro.", "테스트 빌드: Pro APK를 설치하세요.", "测试版：请安装专业版 APK。", "測試版：請安裝專業版 APK。", "Testversion: Installieren Sie die Pro-APK.");
            case "round": return L("ラウンド", "Round", "Ronda", "Partie", "라운드", "轮次", "回合", "Runde");
            case "start_round": return L("ラウンド開始", "Start Round", "Iniciar ronda", "Démarrer la partie", "라운드 시작", "开始轮次", "開始回合", "Runde starten");
            case "resume_round": return L("入力中に戻る", "Resume Round", "Continuar ronda", "Reprendre la partie", "라운드 계속", "继续轮次", "繼續回合", "Runde fortsetzen");
            case "history": return L("履歴", "History", "Historial", "Historique", "기록", "历史", "歷史", "Verlauf");
            case "analysis": return L("分析", "Analysis", "Análisis", "Analyse", "분석", "分析", "分析", "Analyse");
            case "settings": return L("設定", "Settings", "Ajustes", "Paramètres", "설정", "设置", "設定", "Einstellungen");
            case "home": return L("ホーム", "Home", "Inicio", "Accueil", "홈", "主页", "首頁", "Start");
            case "recent": return L("最近の成績", "Recent Stats", "Estadísticas recientes", "Stats récentes", "최근 성적", "最近统计", "近期統計", "Letzte Statistiken");
            case "csv_export": return L("CSVエクスポート", "CSV Export", "Exportar CSV", "Exporter CSV", "CSV 내보내기", "导出 CSV", "匯出 CSV", "CSV exportieren");
            case "csv_note": return L("ラウンド日付を基準に履歴CSVを出力します。", "Export score history by round date.", "Exporta el historial por fecha de ronda.", "Exporter l’historique par date de partie.", "라운드 날짜 기준으로 기록을 CSV로 내보냅니다.", "按轮次日期导出历史 CSV。", "依回合日期匯出歷史 CSV。", "Scoreverlauf nach Rundendatum exportieren.");
            case "from_date": return L("開始日", "Start date", "Fecha inicial", "Date de début", "시작일", "开始日期", "開始日期", "Startdatum");
            case "to_date": return L("終了日", "End date", "Fecha final", "Date de fin", "종료일", "结束日期", "結束日期", "Enddatum");
            case "csv_save": return L("CSVを保存", "Save CSV", "Guardar CSV", "Enregistrer CSV", "CSV 저장", "保存 CSV", "儲存 CSV", "CSV speichern");
            case "csv_saved": return L("CSV保存しました", "CSV saved", "CSV guardado", "CSV enregistré", "CSV가 저장되었습니다", "CSV已保存", "CSV已儲存", "CSV gespeichert");
            case "csv_failed": return L("CSV保存失敗", "CSV failed", "Error al guardar CSV", "Échec CSV", "CSV 저장 실패", "CSV保存失败", "CSV儲存失敗", "CSV fehlgeschlagen");
            case "locked": return L("Pro機能です。有料版APKでテストできます。", "This is a Pro feature. Install the Pro APK to test it.", "Es una función Pro. Instala el APK Pro para probarla.", "Fonction Pro. Installez l’APK Pro pour la tester.", "Pro 기능입니다. Pro APK로 테스트하세요.", "这是专业版功能。请安装专业版 APK 测试。", "這是專業版功能。請安裝專業版 APK 測試。", "Dies ist eine Pro-Funktion. Installieren Sie die Pro-APK zum Testen.");
            case "round_settings": return L("ラウンド設定", "Round Settings", "Ajustes de ronda", "Paramètres de partie", "라운드 설정", "轮次设置", "回合設定", "Rundeneinstellungen");
            case "round_date": return L("ラウンド日付", "Round date", "Fecha de ronda", "Date de partie", "라운드 날짜", "轮次日期", "回合日期", "Rundendatum");
            case "course": return L("コース名", "Course", "Campo", "Parcours", "코스", "球场", "球場", "Platz");
            case "tee": return L("ティー", "Tee", "Tee", "Tee", "티", "发球台", "發球台", "Tee");
            case "start_memo": return L("開始時間 / メモ", "Start time / memo", "Hora de inicio / nota", "Heure de départ / mémo", "시작 시간 / 메모", "开始时间 / 备注", "開始時間 / 備註", "Startzeit / Notiz");
            case "players": return L("人数", "Players", "Jugadores", "Joueurs", "인원", "人数", "人數", "Spieler");
            case "player": return L("プレイヤー", "Player", "Jugador", "Joueur", "플레이어", "球员", "球員", "Spieler");
            case "progress": return L("進捗", "Progress", "Progreso", "Progression", "진행", "进度", "進度", "Fortschritt");
            case "missing": return L("未入力", "Missing", "Faltan", "Manquant", "미입력", "未输入", "未輸入", "Fehlt");
            case "score_input": return L("スコア入力", "Score Input", "Entrada de score", "Saisie du score", "스코어 입력", "输入成绩", "輸入成績", "Score-Eingabe");
            case "clear": return L("消去", "Clear", "Borrar", "Effacer", "삭제", "清除", "清除", "Löschen");
            case "left_short": return L("左", "L", "Izq", "G", "좌", "左", "左", "L");
            case "right_short": return L("右", "R", "Der", "D", "우", "右", "右", "R");
            case "prev": return L("前へ", "Prev", "Anterior", "Précédent", "이전", "上一页", "上一個", "Zurück");
            case "next": return L("次へ", "Next", "Siguiente", "Suivant", "다음", "下一页", "下一個", "Weiter");
            case "finish": return L("終了", "Finish", "Finalizar", "Terminer", "종료", "结束", "結束", "Beenden");
            case "save_round": return L("ラウンド保存", "Save Round", "Guardar ronda", "Enregistrer la partie", "라운드 저장", "保存轮次", "儲存回合", "Runde speichern");
            case "cancel": return L("キャンセル終了", "Cancel / End", "Cancelar / salir", "Annuler / quitter", "취소 / 종료", "取消 / 结束", "取消 / 結束", "Abbrechen / Ende");
            case "cancel_confirm": return L("スコア登録を終了しますか？", "End score entry?", "¿Finalizar entrada de score?", "Terminer la saisie du score ?", "스코어 입력을 종료할까요?", "结束成绩输入？", "結束成績輸入？", "Score-Eingabe beenden?");
            case "back_input": return L("入力へ戻る", "Back to Input", "Volver a entrada", "Retour à la saisie", "입력으로 돌아가기", "返回输入", "返回輸入", "Zur Eingabe zurück");
            case "back_home": return L("ホームへ戻る", "Back Home", "Volver al inicio", "Retour accueil", "홈으로 돌아가기", "返回主页", "返回首頁", "Zur Startseite");
            case "history_sub": return L("ラウンド日付別スコアカード", "Scorecards by round date", "Tarjetas por fecha", "Cartes par date", "날짜별 스코어카드", "按日期的记分卡", "依日期的計分卡", "Scorekarten nach Datum");
            case "no_history": return L("履歴はまだありません。", "No history yet.", "Aún no hay historial.", "Aucun historique.", "아직 기록이 없습니다.", "还没有历史记录。", "尚無歷史記錄。", "Noch kein Verlauf.");
            case "detail": return L("詳細", "Detail", "Detalle", "Détail", "상세", "详情", "詳細", "Details");
            case "analysis_sub": return L("傾向分析", "Trend analysis", "Análisis de tendencia", "Analyse de tendance", "추세 분석", "趋势分析", "趨勢分析", "Trend-Analyse");
            case "analysis_locked": return L("分析はPro機能です。", "Analysis is a Pro feature.", "El análisis es una función Pro.", "L’analyse est une fonction Pro.", "분석은 Pro 기능입니다.", "分析是专业版功能。", "分析是專業版功能。", "Analyse ist eine Pro-Funktion.");
            case "pro_feature": return L("Pro機能", "Pro Feature", "Función Pro", "Fonction Pro", "Pro 기능", "专业版功能", "專業版功能", "Pro-Funktion");
            case "support": return L("サポート", "Support", "Soporte", "Assistance", "지원", "支持", "支援", "Support");
            case "review": return L("レビューを書く", "Review this app", "Escribir reseña", "Écrire un avis", "리뷰 작성", "撰写评价", "撰寫評論", "App bewerten");
            case "contact": return L("お問い合わせ", "Contact", "Contacto", "Contact", "문의", "联系", "聯絡", "Kontakt");
            case "idea": return L("新機能を提案する", "Suggest a feature", "Sugerir función", "Suggérer une fonction", "새 기능 제안", "建议新功能", "建議新功能", "Funktion vorschlagen");
            case "backup": return L("バックアップ / 復元", "Backup / Restore", "Copia / restaurar", "Sauvegarde / restauration", "백업 / 복원", "备份 / 恢复", "備份 / 還原", "Backup / Wiederherstellung");
            case "backup_locked": return L("バックアップと復元はPro機能です。", "Backup and restore are Pro features.", "Copia y restauración son funciones Pro.", "Sauvegarde et restauration sont des fonctions Pro.", "백업과 복원은 Pro 기능입니다.", "备份和恢复是专业版功能。", "備份與還原是專業版功能。", "Backup und Wiederherstellung sind Pro-Funktionen.");
            case "backup_note": return L("バックアップファイルを任意の場所に保存し、復元できます。", "Save a backup file anywhere and restore it later.", "Guarda una copia donde quieras y restáurala después.", "Enregistrez une sauvegarde et restaurez-la plus tard.", "백업 파일을 저장하고 나중에 복원할 수 있습니다.", "可将备份文件保存到任意位置并稍后恢复。", "可將備份檔儲存至任意位置並稍後還原。", "Backup-Datei speichern und später wiederherstellen.");
            case "backup_save": return L("バックアップ保存", "Save Backup", "Guardar copia", "Enregistrer sauvegarde", "백업 저장", "保存备份", "儲存備份", "Backup speichern");
            case "restore_backup": return L("バックアップから復元", "Restore Backup", "Restaurar copia", "Restaurer sauvegarde", "백업 복원", "恢复备份", "還原備份", "Backup wiederherstellen");
            case "backup_saved": return L("バックアップ保存しました", "Backup saved", "Copia guardada", "Sauvegarde enregistrée", "백업 저장 완료", "备份已保存", "備份已儲存", "Backup gespeichert");
            case "backup_failed": return L("バックアップ失敗", "Backup failed", "Error de copia", "Échec sauvegarde", "백업 실패", "备份失败", "備份失敗", "Backup fehlgeschlagen");
            case "restore_done": return L("復元しました", "Restored", "Restaurado", "Restauré", "복원 완료", "已恢复", "已還原", "Wiederhergestellt");
            case "restore_failed": return L("復元失敗", "Restore failed", "Error al restaurar", "Échec restauration", "복원 실패", "恢复失败", "還原失敗", "Wiederherstellung fehlgeschlagen");
            case "scorecard_title": return L("ゴルフスコアカード", "Golf Scorecard", "Tarjeta de golf", "Carte de score golf", "골프 스코어카드", "高尔夫记分卡", "高爾夫計分卡", "Golf-Scorekarte");
            case "date": return L("日付", "Date", "Fecha", "Date", "날짜", "日期", "日期", "Datum");
            case "memo": return L("メモ", "Memo", "Nota", "Mémo", "메모", "备注", "備註", "Notiz");
            case "pdf_save": return L("PDF保存", "Save PDF", "Guardar PDF", "Enregistrer PDF", "PDF 저장", "保存 PDF", "儲存 PDF", "PDF speichern");
            case "pdf_saved": return L("PDF保存しました", "PDF saved", "PDF guardado", "PDF enregistré", "PDF 저장 완료", "PDF已保存", "PDF已儲存", "PDF gespeichert");
            case "pdf_failed": return L("PDF保存失敗", "PDF failed", "Error de PDF", "Échec PDF", "PDF 실패", "PDF保存失败", "PDF儲存失敗", "PDF fehlgeschlagen");
            case "course_empty": return L("未入力コース", "No course", "Sin campo", "Aucun parcours", "코스 없음", "无球场", "無球場", "Kein Platz");
            case "tee_result": return L("ティーショット結果", "Tee result", "Resultado del tee", "Résultat du départ", "티샷 결과", "发球结果", "開球結果", "Tee-Ergebnis");
            case "ten_error": return L("10+は10〜15のみです。", "10+ supports 10-15.", "10+ admite 10-15.", "10+ accepte 10-15.", "10+는 10~15만 지원합니다.", "10+ 仅支持10-15。", "10+ 僅支援10-15。", "10+ unterstützt 10-15.");
            case "today_analysis": return L("ラウンド分析", "Round Analysis", "Análisis de ronda", "Analyse de partie", "라운드 분석", "轮次分析", "回合分析", "Rundenanalyse");
            case "ob_advice": return L("OBが複数あります。狭いホールは安全な番手選択が有効です。", "Multiple OBs. Use safer club selection on tight holes.", "Varios OB. Elige un palo más seguro en hoyos estrechos.", "Plusieurs OB. Choisissez un club plus sûr sur les trous étroits.", "OB가 여러 번 있습니다. 좁은 홀에서는 안전한 클럽 선택이 좋습니다.", "多次OB。狭窄球洞建议选择更稳妥的球杆。", "多次OB。狹窄球洞建議選擇更穩妥的球桿。", "Mehrere OB. Auf engen Löchern sicherere Schlägerwahl nutzen.");
            case "good_record": return L("良い記録です。継続してラウンドを記録しましょう。", "Good record. Keep tracking rounds.", "Buen registro. Sigue guardando rondas.", "Bon relevé. Continuez à suivre vos parties.", "좋은 기록입니다. 라운드를 계속 기록하세요.", "记录良好。继续记录轮次。", "記錄良好。請持續記錄回合。", "Guter Eintrag. Runden weiter verfolgen.");
            case "no_data": return L("データなし", "No data", "Sin datos", "Aucune donnée", "데이터 없음", "无数据", "無資料", "Keine Daten");
            case "rounds": return L("ラウンド数", "Rounds", "Rondas", "Parties", "라운드 수", "轮次数", "回合數", "Runden");
            case "avg_score": return L("平均スコア", "AVG Score", "Score medio", "Score moyen", "평균 스코어", "平均成绩", "平均成績", "Ø Score");
            case "avg_pat": return L("平均PAT", "AVG PAT", "PAT medio", "PAT moyen", "평균 PAT", "平均PAT", "平均PAT", "Ø PAT");
            case "latest_round": return L("最新ラウンド", "Latest Round", "Última ronda", "Dernière partie", "최근 라운드", "最新轮次", "最新回合", "Letzte Runde");
            case "free_limit_reached": return L("無料版の保存上限です。有料版APKでテストしてください。", "Free limit reached. Install the Pro APK to test unlimited history.", "Límite gratuito alcanzado. Instala el APK Pro.", "Limite gratuite atteinte. Installez l’APK Pro.", "무료 저장 한도에 도달했습니다. Pro APK를 설치하세요.", "已达到免费版保存上限。请安装专业版 APK。", "已達免費版儲存上限。請安裝專業版 APK。", "Kostenloses Limit erreicht. Installieren Sie die Pro-APK.");
            case "saved": return L("保存しました", "Saved", "Guardado", "Enregistré", "저장됨", "已保存", "已儲存", "Gespeichert");
            case "store_unavailable": return L("このテスト環境ではストアを開けません。", "Store is not available in this test build.", "La tienda no está disponible en esta prueba.", "La boutique n’est pas disponible dans ce test.", "이 테스트 빌드에서는 스토어를 열 수 없습니다.", "此测试版无法打开商店。", "此測試版無法開啟商店。", "Store ist in dieser Testversion nicht verfügbar.");
            case "mail_unavailable": return L("メールアプリを開けません。", "Mail app is not available.", "La app de correo no está disponible.", "L’application mail n’est pas disponible.", "메일 앱을 열 수 없습니다.", "无法打开邮件应用。", "無法開啟郵件應用。", "Mail-App ist nicht verfügbar.");
            case "mail_subject_contact": return L("Golf Scorecard Offline お問い合わせ", "Golf Scorecard Offline Contact", "Contacto Golf Scorecard Offline", "Contact Golf Scorecard Offline", "Golf Scorecard Offline 문의", "Golf Scorecard Offline 联系", "Golf Scorecard Offline 聯絡", "Golf Scorecard Offline Kontakt");
            case "mail_subject_idea": return L("Golf Scorecard Offline 新機能提案", "Golf Scorecard Offline Feature Idea", "Idea para Golf Scorecard Offline", "Idée pour Golf Scorecard Offline", "Golf Scorecard Offline 기능 제안", "Golf Scorecard Offline 功能建议", "Golf Scorecard Offline 功能建議", "Golf Scorecard Offline Funktionsidee");
        }
        return k;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private void top() { scroll.post(() -> scroll.fullScroll(View.FOCUS_UP)); }
    private void restore(int y) { scroll.post(() -> scroll.scrollTo(0, y)); }
    private String today() { return new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date()); }
    private String todayFile() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private String fileDate(String s, String fb) { int d = dateNum(s, -1); return d < 0 ? fb : String.valueOf(d); }
    private int dateNum(String s, int fb) { try { String x = s == null ? "" : s.replace("/", "").replace("-", "").replace(".", "").trim(); if (x.length() >= 8) return Integer.parseInt(x.substring(0, 8)); } catch (Exception e) {} return fb; }
    private int num(String s, int fb) { try { return Integer.parseInt(s); } catch (Exception e) { return fb; } }
    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private String one(double d) { return String.format(Locale.US, "%.1f", d); }
    private String safe(String s, String fb) { return TextUtils.isEmpty(s) ? fb : s; }
    private String csv(String s) { return "\"" + (s == null ? "" : s.replace("\"", "\"\"").replace("\n", " ")) + "\""; }
    private String enc(String s) { return Base64.encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP); }
    private String dec(String s) { try { return new String(Base64.decode(s, Base64.NO_WRAP), StandardCharsets.UTF_8); } catch (Exception e) { return ""; } }
    private String ser(int[] a) { ArrayList<String> l = new ArrayList<>(); for (int v : a) l.add(String.valueOf(v)); return TextUtils.join(",", l); }
    private String ser(String[] a) { ArrayList<String> l = new ArrayList<>(); for (String v : a) l.add(enc(v)); return TextUtils.join(",", l); }
    private int[] deserInt(String raw, int len, int fb) { int[] a = new int[len]; for (int i = 0; i < len; i++) a[i] = fb; if (TextUtils.isEmpty(raw)) return a; String[] p = raw.split(",", -1); for (int i = 0; i < len && i < p.length; i++) a[i] = num(p[i], fb); return a; }
    private void restoreInt(String raw, int[] target, int[] fb, int min, int max) { if (fb != null) System.arraycopy(fb, 0, target, 0, Math.min(fb.length, target.length)); if (TextUtils.isEmpty(raw)) return; String[] p = raw.split(",", -1); for (int i = 0; i < target.length && i < p.length; i++) target[i] = clamp(num(p[i], target[i]), min, max); }
    private void restoreStr(String raw, String[] target) { if (TextUtils.isEmpty(raw)) return; String[] p = raw.split(",", -1); for (int i = 0; i < target.length && i < p.length; i++) target[i] = dec(p[i]); }

    private static class Record {
        String date = "", course = "", tee = "", memo = "", pars = "", scores = "", puttData = "", teeData = "", card = "", analysis = "";
        int total, putts, fw, teeShots, ob;
        String line() { return enc(date) + "|" + enc(course) + "|" + enc(tee) + "|" + enc(memo) + "|" + total + "|" + putts + "|" + fw + "|" + teeShots + "|" + ob + "|" + enc(pars) + "|" + enc(scores) + "|" + enc(puttData) + "|" + enc(teeData) + "|" + enc(card) + "|" + enc(analysis); }
        static Record from(String line) { try { if (TextUtils.isEmpty(line)) return null; String[] p = line.split("\\|", -1); if (p.length < 15) return null; Record r = new Record(); r.date = dec(p[0]); r.course = dec(p[1]); r.tee = dec(p[2]); r.memo = dec(p[3]); r.total = Integer.parseInt(p[4]); r.putts = Integer.parseInt(p[5]); r.fw = Integer.parseInt(p[6]); r.teeShots = Integer.parseInt(p[7]); r.ob = Integer.parseInt(p[8]); r.pars = dec(p[9]); r.scores = dec(p[10]); r.puttData = dec(p[11]); r.teeData = dec(p[12]); r.card = dec(p[13]); r.analysis = dec(p[14]); return r; } catch (Exception e) { return null; } }
        private static String enc(String s) { return Base64.encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP); }
        private static String dec(String s) { try { return new String(Base64.decode(s, Base64.NO_WRAP), StandardCharsets.UTF_8); } catch (Exception e) { return ""; } }
    }
}
