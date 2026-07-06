package jp.co.nkts.scoremanager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
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
    private static final int SCORE_MAX = 15;
    private static final String PREF_NAME = "nk_score_manager_v21";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_COURSES = "courses";
    private static final String KEY_CLUBS = "clubs";
    private static final String KEY_PDF_TREE = "pdf_tree";
    private static final String KEY_PHOTO = "club_photo";
    private static final int REQ_PDF_FOLDER = 901;
    private static final int REQ_BACKUP_CREATE = 902;
    private static final int REQ_RESTORE_OPEN = 903;
    private static final int REQ_CAMERA = 904;
    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_ROUND = 1;
    private static final int SCREEN_HISTORY = 2;
    private static final int SCREEN_ANALYSIS = 3;
    private static final int SCREEN_SETTINGS = 4;
    private static final int C_BG = 0xFFF8FAFC;
    private static final int C_CARD = 0xFFFFFFFF;
    private static final int C_TEXT = 0xFF0F172A;
    private static final int C_MUTED = 0xFF64748B;
    private static final int C_BORDER = 0xFFE2E8F0;
    private static final int C_PRIMARY = 0xFF166534;
    private static final int C_PRIMARY_DARK = 0xFF14532D;
    private static final int C_SOFT = 0xFFDCFCE7;
    private static final int C_PANEL = 0xFFEFF6FF;
    private static final int C_DANGER = 0xFFFEE2E2;

    private final int[] defaultPars = {4,4,3,5,4,4,5,3,4,4,5,4,3,4,4,5,3,4};
    private final String[] playerCountOptions = {"1名", "2名", "3名", "4名"};
    private final String[] teeResultLabels = {"未選択", "FW", "左ラフ", "右ラフ", "左OB", "右OB"};
    private LinearLayout root;
    private ScrollView scroll;
    private TextView saveStatus;
    private int screen = SCREEN_HOME;
    private int activePlayers = 1;
    private int currentHole = 0;
    private int tensPendingPlayer = -1;
    private boolean registrationMode = false;
    private boolean cancelConfirm = false;
    private String dateText = "";
    private String courseText = "";
    private String teeText = "";
    private String startText = "";
    private String clubPhotoPath = "";
    private String selectedHistoryText = "";
    private final int[] pars = new int[HOLES];
    private final String[] playerNames = {"Player 1", "Player 2", "Player 3", "Player 4"};
    private final int[][] scores = new int[PLAYERS][HOLES];
    private final int[] putts = new int[HOLES];
    private final int[] teeResults = new int[HOLES];
    private final String[] teeClubs = new String[HOLES];
    private boolean binding = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autosave = new Runnable() {
        @Override public void run() {
            saveDraft(false);
            handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        initDefaults();
        restoreDraft();
        setContentView(baseView());
        if (registrationMode) renderRound(false); else renderHome();
        handler.postDelayed(autosave, 1000L);
    }

    @Override protected void onPause() { saveDraft(false); super.onPause(); }
    @Override protected void onStop() { saveDraft(false); super.onStop(); }
    @Override protected void onDestroy() { handler.removeCallbacks(autosave); saveDraft(false); super.onDestroy(); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_PDF_FOLDER) {
            Uri uri = data.getData();
            if (uri != null) {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) {}
                prefs().edit().putString(KEY_PDF_TREE, uri.toString()).commit();
                toast("PDF保存先を記憶しました。");
                renderSettings();
            }
            return;
        }
        if (requestCode == REQ_BACKUP_CREATE) { if (data.getData() != null) writeBackup(data.getData()); return; }
        if (requestCode == REQ_RESTORE_OPEN) { if (data.getData() != null) restoreBackup(data.getData()); return; }
        if (requestCode == REQ_CAMERA) {
            Bitmap bm = null;
            if (data.getExtras() != null && data.getExtras().get("data") instanceof Bitmap) bm = (Bitmap) data.getExtras().get("data");
            if (bm == null) toast("写真を取得できませんでした。"); else saveClubPhoto(bm);
        }
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
        dateText = nowDate();
        System.arraycopy(defaultPars, 0, pars, 0, HOLES);
        for (int p = 0; p < PLAYERS; p++) playerNames[p] = "Player " + (p + 1);
        String first = clubList()[0];
        for (int h = 0; h < HOLES; h++) teeClubs[h] = first;
    }

    private void renderHome() {
        screen = SCREEN_HOME;
        registrationMode = false;
        cancelConfirm = false;
        saveDraft(false);
        root.removeAllViews();
        root.addView(hero("NK Score Manager", "V2.1 / テンキー入力・キャンセル確認"));
        LinearLayout c = card();
        c.addView(section("ラウンド"));
        Button start = button("ラウンド開始", true);
        start.setOnClickListener(v -> { resetRound(true); renderRound(false); });
        c.addView(start, full());
        if (hasDraft()) {
            Button resume = button("入力中のラウンドに戻る", false);
            resume.setOnClickListener(v -> renderRound(false));
            c.addView(resume, full());
        }
        Button history = button("履歴を見る", false); history.setOnClickListener(v -> renderHistory()); c.addView(history, full());
        Button analysis = button("分析を見る", false); analysis.setOnClickListener(v -> renderAnalysis()); c.addView(analysis, full());
        Button settings = button("設定・バックアップ", false); settings.setOnClickListener(v -> renderSettings()); c.addView(settings, full());
        root.addView(c);
        root.addView(statsCard());
        addNav();
        top();
    }

    private View statsCard() {
        LinearLayout c = card();
        c.addView(section("最近の成績"));
        c.addView(panel(statsText(loadHistory()), true));
        return c;
    }

    private void renderRound(boolean keepScroll) {
        int y = keepScroll && scroll != null ? scroll.getScrollY() : 0;
        screen = SCREEN_ROUND;
        registrationMode = true;
        root.removeAllViews();
        root.addView(roundHeader());
        root.addView(setupCard());
        root.addView(progressCard());
        root.addView(inputCard());
        root.addView(actionCard());
        saveStatus = text("自動保存中", 12, C_MUTED, false);
        saveStatus.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(saveStatus);
        if (keepScroll) restoreScroll(y); else top();
    }

    private View roundHeader() {
        LinearLayout c = card();
        TextView t = text((currentHole + 1) + "H  PAR" + pars[currentHole], 30, C_TEXT, true);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(t);
        TextView s = text(courseOrDefault() + " / " + teeText + " / " + startText, 13, C_MUTED, false);
        s.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(s);
        return c;
    }

    private View setupCard() {
        LinearLayout c = card();
        c.addView(section("ラウンド設定"));
        EditText course = input("コース名"); course.setText(courseText); watch(course, v -> courseText = v); c.addView(course);
        EditText tee = input("ティー"); tee.setText(teeText); watch(tee, v -> teeText = v); c.addView(tee);
        EditText start = input("スタート時間 / OUT / IN"); start.setText(startText); watch(start, v -> startText = v); c.addView(start);
        LinearLayout row = row();
        Button saveCourse = button("コース保存", false); saveCourse.setOnClickListener(v -> saveCourseTemplate());
        Button loadCourse = button("保存コース読込", false); loadCourse.setOnClickListener(v -> loadLatestCourse());
        row.addView(saveCourse, weight(1)); row.addView(loadCourse, weight(1)); c.addView(row);
        LinearLayout pc = row(); pc.setGravity(Gravity.CENTER_VERTICAL);
        pc.addView(text("人数", 14, C_TEXT, true), weight(1));
        Spinner sp = spinner(playerCountOptions);
        binding = true; sp.setSelection(activePlayers - 1); binding = false;
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (binding) return;
                int n = position + 1;
                if (n != activePlayers) { activePlayers = n; saveDraft(false); renderRound(true); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        pc.addView(sp, new LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT)); c.addView(pc);
        for (int p = 0; p < activePlayers; p++) {
            final int idx = p;
            EditText e = input(p == 0 ? "Player1 名前" : "同伴者" + (p + 1));
            e.setText(playerNames[p]); watch(e, v -> playerNames[idx] = v); c.addView(e);
        }
        return c;
    }

    private View progressCard() {
        LinearLayout c = card();
        c.addView(section("進捗"));
        c.addView(panel("Player1 " + entered(0) + "/18 / 全体未入力 " + missing(), true));
        for (int r = 0; r < 3; r++) {
            LinearLayout line = row();
            for (int col = 0; col < 6; col++) {
                int h = r * 6 + col;
                Button b = new Button(this);
                b.setText(String.valueOf(h + 1)); b.setAllCaps(false); b.setTextSize(12);
                b.setTextColor(h == currentHole ? 0xFFFFFFFF : C_TEXT);
                b.setBackground(rounded(h == currentHole ? C_PRIMARY : (holeComplete(h) ? C_SOFT : C_DANGER), C_BORDER, 12));
                final int target = h;
                b.setOnClickListener(v -> { currentHole = target; tensPendingPlayer = -1; saveDraft(false); renderRound(false); });
                line.addView(b, weight(1));
            }
            c.addView(line);
        }
        return c;
    }

    private View inputCard() {
        LinearLayout c = card();
        c.addView(section("スコア入力"));
        c.addView(parPicker());
        c.addView(playerInput(0, true));
        for (int p = 1; p < activePlayers; p++) c.addView(playerInput(p, false));
        LinearLayout nav = row();
        Button prev = button("前のH", false); prev.setEnabled(currentHole > 0); prev.setOnClickListener(v -> moveHole(-1));
        Button next = button("次のH", true); next.setEnabled(currentHole < HOLES - 1); next.setOnClickListener(v -> moveHole(1));
        nav.addView(prev, weight(1)); nav.addView(next, weight(1)); c.addView(nav);
        return c;
    }

    private View parPicker() {
        LinearLayout c = lite(); c.addView(text("PAR", 12, C_MUTED, true));
        LinearLayout r = row();
        for (int par = 3; par <= 6; par++) {
            final int value = par;
            Button b = choice(String.valueOf(par), pars[currentHole] == par);
            b.setOnClickListener(v -> { pars[currentHole] = value; saveDraft(false); renderRound(true); });
            r.addView(b, weight(1));
        }
        c.addView(r);
        return c;
    }

    private View playerInput(int player, boolean detail) {
        LinearLayout c = lite();
        String pending = tensPendingPlayer == player ? "  10台入力中：次に0〜5を押してください" : "";
        c.addView(text(displayName(player) + "  SCORE " + scoreText(scores[player][currentHole]) + pending, 17, C_TEXT, true));
        c.addView(scoreKeypad(player));
        if (detail) {
            c.addView(puttButtons());
            c.addView(teeButtons());
        }
        return c;
    }

    private View scoreKeypad(int player) {
        LinearLayout g = new LinearLayout(this); g.setOrientation(LinearLayout.VERTICAL);
        String[] keys = {"1","2","3","4","5","6","7","8","9","1+","0"};
        int index = 0;
        for (int r = 0; r < 4; r++) {
            LinearLayout line = row();
            int count = r == 3 ? 2 : 3;
            for (int i = 0; i < count && index < keys.length; i++, index++) {
                String label = keys[index];
                Button b = choice(label, false);
                b.setMinHeight(dp(54));
                b.setOnClickListener(v -> handleScoreKey(player, label));
                line.addView(b, weight(1));
            }
            g.addView(line);
        }
        Button clear = button("未入力に戻す", false);
        clear.setOnClickListener(v -> { scores[player][currentHole] = 0; tensPendingPlayer = -1; saveDraft(false); renderRound(true); });
        g.addView(clear, full());
        return g;
    }

    private void handleScoreKey(int player, String key) {
        if ("1+".equals(key)) {
            tensPendingPlayer = player;
            renderRound(true);
            return;
        }
        int digit = parseInt(key, -1);
        if (digit < 0) return;
        if (tensPendingPlayer == player) {
            if (digit <= 5) {
                scores[player][currentHole] = 10 + digit;
                tensPendingPlayer = -1;
                saveDraft(false);
                renderRound(true);
            } else {
                toast("10台は10〜15までです。0〜5を押してください。");
            }
        } else {
            scores[player][currentHole] = digit == 0 ? 0 : digit;
            saveDraft(false);
            renderRound(true);
        }
    }

    private View puttButtons() {
        LinearLayout g = new LinearLayout(this); g.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("PAT", 12, C_MUTED, true); label.setGravity(Gravity.CENTER_HORIZONTAL); g.addView(label);
        LinearLayout r = row();
        for (int p = 1; p <= 4; p++) {
            final int value = p;
            Button b = choice(p == 4 ? "4+" : String.valueOf(p), putts[currentHole] == p);
            b.setOnClickListener(v -> { putts[currentHole] = value; saveDraft(false); renderRound(true); });
            r.addView(b, weight(1));
        }
        g.addView(r);
        return g;
    }

    private View teeButtons() {
        LinearLayout g = new LinearLayout(this); g.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("Tee / クラブと結果", 12, C_MUTED, true); label.setGravity(Gravity.CENTER_HORIZONTAL); g.addView(label);
        Spinner sp = spinner(clubList());
        int sel = 0; String[] clubs = clubList();
        for (int i = 0; i < clubs.length; i++) if (clubs[i].equals(teeClubs[currentHole])) sel = i;
        binding = true; sp.setSelection(sel); binding = false;
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (binding) return;
                teeClubs[currentHole] = clubList()[pos];
                saveDraft(false);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        g.addView(sp);
        LinearLayout r1 = row(); addTee(r1, "FW", 1); addTee(r1, "左ラフ", 2); addTee(r1, "右ラフ", 3); g.addView(r1);
        LinearLayout r2 = row(); addTee(r2, "左OB", 4); addTee(r2, "右OB", 5);
        Button clear = button("未選択", false); clear.setOnClickListener(v -> { teeResults[currentHole] = 0; saveDraft(false); renderRound(true); });
        r2.addView(clear, weight(1)); g.addView(r2);
        return g;
    }

    private void addTee(LinearLayout row, String label, int value) {
        Button b = choice(label, teeResults[currentHole] == value);
        b.setOnClickListener(v -> { teeResults[currentHole] = value; saveDraft(false); renderRound(true); });
        row.addView(b, weight(1));
    }

    private View actionCard() {
        LinearLayout c = card(); c.addView(section("ラウンド後・終了"));
        Button photo = button("当日クラブを撮影してWebP保存", false); photo.setOnClickListener(v -> capturePhoto()); c.addView(photo, full());
        if (!TextUtils.isEmpty(clubPhotoPath)) c.addView(panel("クラブ写真: " + clubPhotoPath, false));
        Button finish = button("保存して分析を見る", true); finish.setOnClickListener(v -> finishRound()); c.addView(finish, full());
        Button cancel = button("キャンセル終了", false); cancel.setOnClickListener(v -> { cancelConfirm = true; renderRound(true); }); c.addView(cancel, full());
        if (cancelConfirm) {
            c.addView(panel("スコア登録を終了しますか？\n誤って押した場合は登録画面へ戻れます。", false));
            LinearLayout r = row();
            Button back = button("登録画面へ戻る", true); back.setOnClickListener(v -> { cancelConfirm = false; renderRound(true); });
            Button home = button("ホームへ戻る", false); home.setOnClickListener(v -> { registrationMode = false; cancelConfirm = false; saveDraft(false); renderHome(); });
            r.addView(back, weight(1)); r.addView(home, weight(1)); c.addView(r);
        }
        return c;
    }

    private void finishRound() {
        int m = missing();
        if (m > 0) { toast("未入力スコアがあります: " + m + "件"); renderRound(true); return; }
        RoundRecord r = buildRecord();
        ArrayList<RoundRecord> list = loadHistory(); list.add(0, r); saveHistory(list);
        selectedHistoryText = r.scoreCard + "\n\n" + r.analysis;
        resetRound(false);
        toast("保存しました。");
        renderHistory();
    }

    private RoundRecord buildRecord() {
        RoundRecord r = new RoundRecord();
        r.timestamp = parseDate(dateText); r.date = dateText; r.course = courseOrDefault(); r.tee = teeText;
        r.total = totalScore(0); r.putts = sum(putts); r.fw = countTee(1); r.teeShots = countTeeTargets();
        r.pars = serializeInt(pars); r.p1Scores = serializeInt(scores[0]); r.puttArray = serializeInt(putts);
        r.teeResults = serializeInt(teeResults); r.teeClubs = serializeStr(teeClubs); r.clubPhoto = clubPhotoPath;
        r.scoreCard = buildScoreCard(); r.analysis = buildAdvice();
        return r;
    }

    private String buildScoreCard() {
        StringBuilder b = new StringBuilder();
        b.append("NK SCORE CARD\n").append(dateText).append("  ").append(courseOrDefault()).append("  ").append(teeText).append("\n");
        b.append("HOLE  1  2  3  4  5  6  7  8  9 |OUT|10 11 12 13 14 15 16 17 18 |IN |TOTAL\n");
        b.append("PAR  ").append(rowValues(pars, false)).append("\n");
        for (int p = 0; p < activePlayers; p++) b.append(shortName(displayName(p))).append("   ").append(rowValues(scores[p], true)).append("\n");
        b.append("\nPlayer1 PAT: ").append(sum(putts)).append(" / FW: ").append(countTee(1)).append("/").append(countTeeTargets()).append("\n");
        if (!TextUtils.isEmpty(clubPhotoPath)) b.append("Club Photo: ").append(clubPhotoPath).append("\n");
        return b.toString();
    }

    private String buildAdvice() {
        int left = countTee(2) + countTee(4), right = countTee(3) + countTee(5), ob = countTee(4) + countTee(5);
        StringBuilder b = new StringBuilder();
        b.append("今日の自動分析\nスコア: ").append(totalScore(0)).append(" / PAT: ").append(sum(putts)).append(" / OB系: ").append(ob).append("\n");
        if (sum(putts) >= 36) b.append("・PAT数が多めです。3PATを減らすと改善が早いです。\n");
        if (right > left) b.append("・右方向ミスが多い傾向です。構えとフェース向きを確認。\n");
        if (left > right) b.append("・左方向ミスが多い傾向です。引っかけを確認。\n");
        if (ob >= 2) b.append("・OBが複数回あります。狭いホールでは番手を落とす判断が有効です。\n");
        b.append("PAR3差分: ").append(diffByPar(3)).append(" / PAR4差分: ").append(diffByPar(4)).append(" / PAR5差分: ").append(diffByPar(5)).append("\n");
        b.append("\nクラブ別\n").append(currentClubStats());
        return b.toString();
    }

    private String currentClubStats() {
        LinkedHashMap<String, int[]> map = new LinkedHashMap<>();
        for (int h = 0; h < HOLES; h++) {
            String club = TextUtils.isEmpty(teeClubs[h]) ? "未選択" : teeClubs[h];
            int[] a = map.get(club); if (a == null) a = new int[3];
            a[0]++; if (teeResults[h] == 1) a[1]++; if (teeResults[h] == 4 || teeResults[h] == 5) a[2]++;
            map.put(club, a);
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String,int[]> e : map.entrySet()) b.append(e.getKey()).append(" 使用").append(e.getValue()[0]).append(" / FW").append(e.getValue()[1]).append(" / OB").append(e.getValue()[2]).append("\n");
        return b.toString();
    }

    private void renderHistory() {
        screen = SCREEN_HISTORY; registrationMode = false; cancelConfirm = false; saveDraft(false); root.removeAllViews();
        root.addView(hero("履歴", "スコアカードとPDF"));
        LinearLayout c = card(); ArrayList<RoundRecord> list = loadHistory();
        if (list.isEmpty()) c.addView(panel("履歴はまだありません。", false));
        for (RoundRecord r : list) {
            LinearLayout item = lite(); item.addView(text(r.date + " " + r.course + " / " + r.total + " / PAT " + r.putts, 14, C_TEXT, true));
            LinearLayout buttons = row();
            Button detail = button("詳細", false); detail.setOnClickListener(v -> { selectedHistoryText = r.scoreCard + "\n\n" + r.analysis; renderHistory(); });
            Button pdf = button("PDF", true); pdf.setOnClickListener(v -> exportPdf(r.scoreCard, r.analysis));
            buttons.addView(detail, weight(1)); buttons.addView(pdf, weight(1)); item.addView(buttons); c.addView(item);
        }
        if (!TextUtils.isEmpty(selectedHistoryText)) c.addView(panel(selectedHistoryText, false));
        root.addView(c); addNav(); top();
    }

    private void renderAnalysis() {
        screen = SCREEN_ANALYSIS; registrationMode = false; saveDraft(false); root.removeAllViews();
        root.addView(hero("分析", "コース別・ホール別・クラブ別"));
        ArrayList<RoundRecord> list = loadHistory();
        LinearLayout c = card(); c.addView(panel(statsText(list) + "\n\n" + courseAnalysis(list) + "\n" + clubAnalysis(list) + "\n" + holeAnalysis(list), true));
        root.addView(c); addNav(); top();
    }

    private void renderSettings() {
        screen = SCREEN_SETTINGS; registrationMode = false; saveDraft(false); root.removeAllViews();
        root.addView(hero("設定", "保存先・クラブセット・バックアップ"));
        LinearLayout c = card();
        c.addView(section("PDF保存先")); c.addView(panel(TextUtils.isEmpty(prefs().getString(KEY_PDF_TREE, "")) ? "未設定：内部保存" : "設定済み", false));
        Button folder = button("PDF保存先を指定・変更", true); folder.setOnClickListener(v -> choosePdfFolder()); c.addView(folder, full());
        c.addView(section("クラブセット")); EditText clubs = input("クラブをカンマ区切り"); clubs.setText(TextUtils.join(",", clubList())); c.addView(clubs);
        Button saveClub = button("クラブセット保存", true); saveClub.setOnClickListener(v -> { prefs().edit().putString(KEY_CLUBS, clubs.getText().toString()).commit(); toast("保存しました。"); }); c.addView(saveClub, full());
        c.addView(section("バックアップ"));
        Button backup = button("Google Driveへバックアップ", false); backup.setOnClickListener(v -> createBackupDocument()); c.addView(backup, full());
        Button restore = button("Google Driveからリストア", false); restore.setOnClickListener(v -> openBackupDocument()); c.addView(restore, full());
        c.addView(section("全世界コース選択")); c.addView(panel("本格実装には商用利用可能なコースDB/APIが必要です。現時点ではコーステンプレート保存を推奨します。", false));
        root.addView(c); addNav(); top();
    }

    private String statsText(ArrayList<RoundRecord> list) {
        long now = System.currentTimeMillis();
        return avgText(list, now - 90L*24L*60L*60L*1000L, "過去3カ月") + "\n" + avgText(list, now - 365L*24L*60L*60L*1000L, "過去1年");
    }

    private String avgText(ArrayList<RoundRecord> list, long since, String label) {
        int c = 0, total = 0, putt = 0, fw = 0, tee = 0;
        for (RoundRecord r : list) if (r.timestamp >= since) { c++; total += r.total; putt += r.putts; fw += r.fw; tee += r.teeShots; }
        if (c == 0) return label + ": データなし";
        return label + ": " + c + "回 / 平均" + f1(total*1.0/c) + " / PAT" + f1(putt*1.0/c) + " / FW" + pct(fw, tee);
    }

    private String courseAnalysis(ArrayList<RoundRecord> list) {
        if (list.isEmpty()) return "コース別: データなし";
        LinkedHashMap<String,int[]> map = new LinkedHashMap<>();
        for (RoundRecord r : list) { int[] a = map.get(r.course); if (a == null) a = new int[]{0,0,999}; a[0]++; a[1]+=r.total; if (r.total<a[2]) a[2]=r.total; map.put(r.course,a); }
        StringBuilder b = new StringBuilder("コース別\n");
        for (Map.Entry<String,int[]> e:map.entrySet()) b.append(e.getKey()).append(" 平均").append(f1(e.getValue()[1]*1.0/e.getValue()[0])).append(" / ベスト").append(e.getValue()[2]).append(" / ").append(e.getValue()[0]).append("回\n");
        return b.toString();
    }

    private String clubAnalysis(ArrayList<RoundRecord> list) {
        LinkedHashMap<String,int[]> map = new LinkedHashMap<>();
        for (RoundRecord r:list) {
            String[] clubs = deserStr(r.teeClubs, HOLES, "-"); int[] results = deserInt(r.teeResults, HOLES, 0);
            for (int h=0;h<HOLES;h++) { String club=clubs[h]; if (TextUtils.isEmpty(club) || "-".equals(club)) continue; int[] a=map.get(club); if(a==null)a=new int[4]; a[0]++; if(results[h]==1)a[1]++; if(results[h]==4||results[h]==5)a[2]++; if(results[h]==3||results[h]==5)a[3]++; map.put(club,a); }
        }
        if (map.isEmpty()) return "クラブ別: データなし";
        StringBuilder b = new StringBuilder("クラブ別\n");
        for (Map.Entry<String,int[]> e:map.entrySet()) b.append(e.getKey()).append(" 使用").append(e.getValue()[0]).append(" / FW率").append(pct(e.getValue()[1], e.getValue()[0])).append(" / OB").append(e.getValue()[2]).append(" / 右ミス").append(e.getValue()[3]).append("\n");
        return b.toString();
    }

    private String holeAnalysis(ArrayList<RoundRecord> list) {
        int[] diff = new int[HOLES]; int[] cnt = new int[HOLES];
        for (RoundRecord r:list) { int[] s=deserInt(r.p1Scores, HOLES, 0); int[] p=deserInt(r.pars, HOLES, 4); for(int h=0;h<HOLES;h++) if(s[h]>0){diff[h]+=s[h]-p[h];cnt[h]++;} }
        int worst=-1; double avg=-99;
        for(int h=0;h<HOLES;h++) if(cnt[h]>0 && diff[h]*1.0/cnt[h]>avg){avg=diff[h]*1.0/cnt[h]; worst=h;}
        if(worst<0) return "ホール別: データなし";
        return "苦手ホール\n" + (worst+1) + "H 平均 " + (avg>=0?"+":"") + f1(avg);
    }

    private void saveCourseTemplate() {
        if (TextUtils.isEmpty(courseText.trim())) { toast("コース名を入力してください。"); return; }
        String line = enc(courseText.trim()) + "|" + enc(teeText.trim()) + "|" + serializeInt(pars);
        String old = prefs().getString(KEY_COURSES, "");
        prefs().edit().putString(KEY_COURSES, line + (TextUtils.isEmpty(old) ? "" : "\n" + old)).commit();
        toast("コースを保存しました。");
    }

    private void loadLatestCourse() {
        String raw = prefs().getString(KEY_COURSES, "");
        if (TextUtils.isEmpty(raw)) { toast("保存済みコースがありません。"); return; }
        String[] parts = raw.split("\n", -1)[0].split("\\|", -1);
        if (parts.length >= 3) { courseText = dec(parts[0]); teeText = dec(parts[1]); int[] p=deserInt(parts[2], HOLES, 4); System.arraycopy(p,0,pars,0,HOLES); saveDraft(false); renderRound(false); }
    }

    private void choosePdfFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_PDF_FOLDER);
    }

    private void createBackupDocument() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TITLE, "NKScore_Backup_" + nowFile() + ".txt"); startActivityForResult(i, REQ_BACKUP_CREATE);
    }

    private void openBackupDocument() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/*"); startActivityForResult(i, REQ_RESTORE_OPEN);
    }

    private void capturePhoto() { try { startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), REQ_CAMERA); } catch(Exception e) { toast("カメラを起動できませんでした。"); } }

    private void saveClubPhoto(Bitmap bm) {
        try {
            File dir = new File(getFilesDir(), "club_photos"); if(!dir.exists()) dir.mkdirs();
            File file = new File(dir, "club_" + nowFile() + ".webp"); FileOutputStream out = new FileOutputStream(file);
            if (Build.VERSION.SDK_INT >= 30) bm.compress(Bitmap.CompressFormat.WEBP_LOSSY, 70, out); else bm.compress(Bitmap.CompressFormat.WEBP, 70, out);
            out.close(); clubPhotoPath = file.getAbsolutePath(); prefs().edit().putString(KEY_PHOTO, clubPhotoPath).commit(); saveDraft(false); toast("WebPで保存しました。"); renderRound(true);
        } catch(Exception e) { toast("写真保存に失敗しました。"); }
    }

    private void exportPdf(String scoreCard, String analysis) {
        String content = scoreCard + "\n\n" + analysis; String name = "NKScore_" + nowFile() + ".pdf"; String tree = prefs().getString(KEY_PDF_TREE, "");
        if (!TextUtils.isEmpty(tree)) {
            try {
                Uri treeUri = Uri.parse(tree); Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)); Uri file = DocumentsContract.createDocument(getContentResolver(), parent, "application/pdf", name);
                if(file==null) throw new Exception(); OutputStream out=getContentResolver().openOutputStream(file); if(out==null) throw new Exception(); writePdf(content,out); out.close(); copy("PDF URI", file.toString()); toast("PDF保存しました。"); return;
            } catch(Exception e) { toast("指定先に保存できません。内部保存します。"); }
        }
        try { File dir=getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS); if(dir==null)dir=getFilesDir(); if(!dir.exists())dir.mkdirs(); File file=new File(dir,name); FileOutputStream out=new FileOutputStream(file); writePdf(content,out); out.close(); copy("PDF Path", file.getAbsolutePath()); toast("PDF保存しました。"); } catch(Exception e) { toast("PDF保存失敗"); }
    }

    private void writePdf(String content, OutputStream out) throws Exception {
        PdfDocument pdf = new PdfDocument(); Paint p = new Paint(); p.setAntiAlias(true); p.setTextSize(10f); Paint title = new Paint(); title.setAntiAlias(true); title.setTextSize(18f); title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        int w=842,h=595,m=28,y=m,pageNo=1; PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(w,h,pageNo).create()); Canvas c = page.getCanvas(); c.drawText("NK Score Manager", m, y, title); y += 24;
        for(String line:content.split("\n",-1)) for(String part:wrap(line,115)) { if(y>h-m){ pdf.finishPage(page); page=pdf.startPage(new PdfDocument.PageInfo.Builder(w,h,++pageNo).create()); c=page.getCanvas(); y=m; } c.drawText(part,m,y,p); y+=14; }
        pdf.finishPage(page); pdf.writeTo(out); pdf.close();
    }

    private void writeBackup(Uri uri) {
        try { OutputStream out=getContentResolver().openOutputStream(uri); if(out==null) throw new Exception(); out.write(buildBackup().getBytes(StandardCharsets.UTF_8)); out.close(); toast("バックアップ保存しました。"); } catch(Exception e) { toast("バックアップ失敗"); }
    }

    private void restoreBackup(Uri uri) {
        try { InputStream in=getContentResolver().openInputStream(uri); if(in==null) throw new Exception(); ByteArrayOutputStream buf=new ByteArrayOutputStream(); byte[] data=new byte[8192]; int n; while((n=in.read(data))>0) buf.write(data,0,n); in.close(); applyBackup(new String(buf.toByteArray(),StandardCharsets.UTF_8)); initDefaults(); restoreDraft(); toast("リストアしました。"); renderHome(); } catch(Exception e) { toast("リストア失敗"); }
    }

    private String buildBackup() {
        StringBuilder b=new StringBuilder("NKSM_BACKUP_V21\n");
        for(Map.Entry<String,?> e:prefs().getAll().entrySet()) { Object v=e.getValue(); String k=enc(e.getKey()); if(v instanceof String)b.append("S|").append(k).append("|").append(enc((String)v)).append("\n"); else if(v instanceof Integer)b.append("I|").append(k).append("|").append(v).append("\n"); else if(v instanceof Boolean)b.append("B|").append(k).append("|").append(v).append("\n"); else if(v instanceof Long)b.append("L|").append(k).append("|").append(v).append("\n"); }
        return b.toString();
    }

    private void applyBackup(String raw) throws Exception {
        if(TextUtils.isEmpty(raw)||!raw.startsWith("NKSM_BACKUP_V21")) throw new Exception(); SharedPreferences.Editor e=prefs().edit(); e.clear();
        for(String line:raw.split("\n",-1)) { if(TextUtils.isEmpty(line)||line.equals("NKSM_BACKUP_V21")) continue; String[] p=line.split("\\|",-1); if(p.length<3) continue; String k=dec(p[1]); if("S".equals(p[0]))e.putString(k,dec(p[2])); else if("I".equals(p[0]))e.putInt(k,num(p[2],0)); else if("B".equals(p[0]))e.putBoolean(k,Boolean.parseBoolean(p[2])); else if("L".equals(p[0]))e.putLong(k,longNum(p[2],0)); }
        e.commit();
    }

    private void saveDraft(boolean show) {
        SharedPreferences.Editor e=prefs().edit(); e.putBoolean("registration",registrationMode); e.putString("date",dateText); e.putString("course",courseText); e.putString("tee",teeText); e.putString("start",startText); e.putString(KEY_PHOTO,clubPhotoPath); e.putInt("players",activePlayers); e.putInt("hole",currentHole); e.putString("pars",serializeInt(pars)); e.putString("names",serializeStr(playerNames)); e.putString("putts",serializeInt(putts)); e.putString("teeResults",serializeInt(teeResults)); e.putString("teeClubs",serializeStr(teeClubs)); for(int p=0;p<PLAYERS;p++) e.putString("scores"+p,serializeInt(scores[p])); boolean ok=e.commit(); if(saveStatus!=null) saveStatus.setText(ok?"保存済み "+nowFull():"保存失敗"); if(show)toast(ok?"保存しました":"保存失敗");
    }

    private void restoreDraft() {
        SharedPreferences p=prefs(); registrationMode=p.getBoolean("registration",false); dateText=p.getString("date",nowDate()); courseText=p.getString("course",""); teeText=p.getString("tee",""); startText=p.getString("start",""); clubPhotoPath=p.getString(KEY_PHOTO,""); activePlayers=bound(p.getInt("players",1),1,PLAYERS); currentHole=bound(p.getInt("hole",0),0,HOLES-1); restoreInt(p.getString("pars",""),pars,defaultPars,3,6); restoreStr(p.getString("names",""),playerNames); restoreInt(p.getString("putts",""),putts,null,0,8); restoreInt(p.getString("teeResults",""),teeResults,null,0,5); restoreStr(p.getString("teeClubs",""),teeClubs); for(int i=0;i<HOLES;i++) if(TextUtils.isEmpty(teeClubs[i])) teeClubs[i]=clubList()[0]; for(int i=0;i<PLAYERS;i++) restoreInt(p.getString("scores"+i,""),scores[i],null,0,SCORE_MAX);
    }

    private void resetRound(boolean keepMode) {
        dateText=nowDate(); courseText=""; teeText=""; startText=""; clubPhotoPath=""; activePlayers=1; currentHole=0; tensPendingPlayer=-1; cancelConfirm=false; System.arraycopy(defaultPars,0,pars,0,HOLES); for(int p=0;p<PLAYERS;p++){playerNames[p]="Player "+(p+1); for(int h=0;h<HOLES;h++)scores[p][h]=0;} for(int h=0;h<HOLES;h++){putts[h]=0;teeResults[h]=0;teeClubs[h]=clubList()[0];} registrationMode=keepMode;
    }

    private ArrayList<RoundRecord> loadHistory(){ ArrayList<RoundRecord> list=new ArrayList<>(); String raw=prefs().getString(KEY_HISTORY,""); if(TextUtils.isEmpty(raw)) return list; for(String line:raw.split("\n",-1)){ RoundRecord r=RoundRecord.fromLine(line); if(r!=null)list.add(r);} return list; }
    private void saveHistory(ArrayList<RoundRecord> list){ ArrayList<String> lines=new ArrayList<>(); for(int i=0;i<list.size()&&i<300;i++)lines.add(list.get(i).toLine()); prefs().edit().putString(KEY_HISTORY,TextUtils.join("\n",lines)).commit(); }
    private String[] clubList(){ String raw=prefs().getString(KEY_CLUBS,"DR,3W,5W,UT,5I,6I,7I,8I,9I,PW,50,56,60,PT"); ArrayList<String> out=new ArrayList<>(); for(String s:raw.split(",")){String v=s.trim(); if(!TextUtils.isEmpty(v))out.add(v);} if(out.isEmpty())out.add("DR"); return out.toArray(new String[0]); }
    private boolean hasDraft(){ if(!TextUtils.isEmpty(courseText)||!TextUtils.isEmpty(teeText)||!TextUtils.isEmpty(startText))return true; for(int p=0;p<PLAYERS;p++)for(int h=0;h<HOLES;h++)if(scores[p][h]>0)return true; return false; }
    private boolean holeComplete(int h){ for(int p=0;p<activePlayers;p++)if(scores[p][h]==0)return false; return true; }
    private int missing(){ int m=0; for(int p=0;p<activePlayers;p++)for(int h=0;h<HOLES;h++)if(scores[p][h]==0)m++; return m; }
    private int entered(int p){ int n=0; for(int h=0;h<HOLES;h++)if(scores[p][h]>0)n++; return n; }
    private int countTee(int code){ int n=0; for(int v:teeResults)if(v==code)n++; return n; }
    private int countTeeTargets(){ int n=0; for(int v:teeResults)if(v>0)n++; return n; }
    private int diffByPar(int par){ int d=0; for(int h=0;h<HOLES;h++)if(pars[h]==par&&scores[0][h]>0)d+=scores[0][h]-pars[h]; return d; }
    private int totalScore(int p){ int t=0; for(int h=0;h<HOLES;h++)t+=scores[p][h]; return t; }
    private int sum(int[] a){ int t=0; for(int v:a)t+=v; return t; }
    private String rowValues(int[] values, boolean hideZero){ StringBuilder b=new StringBuilder(); int out=0,in=0,total=0; for(int h=0;h<HOLES;h++){int v=values[h]; b.append(hideZero&&v==0?" - ":pad2(v)); if(h<9)out+=v;else in+=v; total+=v; if(h==8)b.append("|").append(pad2(out)).append("|");} b.append("|").append(pad2(in)).append("|").append(pad3(total)); return b.toString(); }
    private int[] deserInt(String raw,int len,int fb){ int[] a=new int[len]; for(int i=0;i<len;i++)a[i]=fb; if(TextUtils.isEmpty(raw))return a; String[] p=raw.split(",",-1); for(int i=0;i<len&&i<p.length;i++)a[i]=num(p[i],fb); return a; }
    private String[] deserStr(String raw,int len,String fb){ String[] a=new String[len]; for(int i=0;i<len;i++)a[i]=fb; if(TextUtils.isEmpty(raw))return a; String[] p=raw.split(",",-1); for(int i=0;i<len&&i<p.length;i++)a[i]=dec(p[i]); return a; }
    private String serializeInt(int[] a){ ArrayList<String> l=new ArrayList<>(); for(int v:a)l.add(String.valueOf(v)); return TextUtils.join(",",l); }
    private String serializeStr(String[] a){ ArrayList<String> l=new ArrayList<>(); for(String s:a)l.add(enc(s==null?"":s)); return TextUtils.join(",",l); }
    private void restoreInt(String raw,int[] target,int[] fb,int min,int max){ if(fb!=null)System.arraycopy(fb,0,target,0,Math.min(fb.length,target.length)); if(TextUtils.isEmpty(raw))return; String[] p=raw.split(",",-1); for(int i=0;i<target.length&&i<p.length;i++)target[i]=bound(num(p[i],target[i]),min,max); }
    private void restoreStr(String raw,String[] target){ if(TextUtils.isEmpty(raw))return; String[] p=raw.split(",",-1); for(int i=0;i<target.length&&i<p.length;i++)target[i]=dec(p[i]); }

    private void addNav(){ LinearLayout n=row(); Button h=button("ホーム",screen==SCREEN_HOME);h.setOnClickListener(v->renderHome()); Button hi=button("履歴",screen==SCREEN_HISTORY);hi.setOnClickListener(v->renderHistory()); Button a=button("分析",screen==SCREEN_ANALYSIS);a.setOnClickListener(v->renderAnalysis()); Button s=button("設定",screen==SCREEN_SETTINGS);s.setOnClickListener(v->renderSettings()); n.addView(h,weight(1)); n.addView(hi,weight(1)); n.addView(a,weight(1)); n.addView(s,weight(1)); root.addView(n); }
    private View hero(String title,String sub){ LinearLayout c=card(); c.setGravity(Gravity.CENTER_HORIZONTAL); ImageView logo=new ImageView(this); logo.setImageResource(getResources().getIdentifier("ic_launcher","drawable",getPackageName())); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(68),dp(68)); lp.gravity=Gravity.CENTER_HORIZONTAL; c.addView(logo,lp); TextView t=text(title,25,C_TEXT,true);t.setGravity(Gravity.CENTER_HORIZONTAL);c.addView(t);TextView st=text(sub,13,C_MUTED,false);st.setGravity(Gravity.CENTER_HORIZONTAL);c.addView(st); return c; }
    private LinearLayout card(){ LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(14),dp(14),dp(14),dp(14)); v.setBackground(rounded(C_CARD,C_BORDER,18)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0,dp(6),0,dp(6)); v.setLayoutParams(p); return v; }
    private LinearLayout lite(){ LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(10),dp(10),dp(10),dp(10)); v.setBackground(rounded(0xFFF8FAFC,C_BORDER,16)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0,dp(5),0,dp(5)); v.setLayoutParams(p); return v; }
    private LinearLayout row(){ LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r; }
    private GradientDrawable rounded(int fill,int stroke,int radius){ GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(1),stroke); return g; }
    private TextView section(String s){ TextView v=text(s,16,C_TEXT,true); v.setPadding(0,0,0,dp(8)); return v; }
    private TextView panel(String s,boolean imp){ TextView v=text(s,imp?15:13,C_TEXT,imp); v.setBackground(rounded(imp?C_SOFT:C_PANEL,imp?C_SOFT:C_BORDER,14)); v.setPadding(dp(12),dp(12),dp(12),dp(12)); return v; }
    private TextView text(String s,int size,int color,boolean bold){ TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); if(bold)v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private Button button(String label,boolean primary){ Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(0xFFFFFFFF); b.setMinHeight(dp(48)); b.setBackgroundResource(getResources().getIdentifier(primary?"button_bg":"secondary_button_bg","drawable",getPackageName())); return b; }
    private Button choice(String label,boolean selected){ Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setTextColor(selected?0xFFFFFFFF:C_TEXT); b.setMinHeight(dp(50)); b.setBackground(rounded(selected?C_PRIMARY:0xFFFFFFFF,selected?C_PRIMARY_DARK:C_BORDER,14)); return b; }
    private EditText input(String hint){ EditText e=new EditText(this); e.setHint(hint); e.setTextSize(14); e.setSingleLine(true); e.setTextColor(C_TEXT); e.setHintTextColor(0xFF94A3B8); e.setInputType(InputType.TYPE_CLASS_TEXT); e.setPadding(dp(8),dp(6),dp(8),dp(6)); return e; }
    private Spinner spinner(String[] values){ Spinner s=new Spinner(this); ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values); a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(a); return s; }
    private void watch(EditText e,TextSink sink){ e.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ sink.set(s==null?"":s.toString()); saveDraft(false); } public void afterTextChanged(Editable e){} }); }
    private LinearLayout.LayoutParams full(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0,dp(5),0,dp(5)); return p; }
    private LinearLayout.LayoutParams weight(float w){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,w); p.setMargins(dp(2),dp(2),dp(2),dp(2)); return p; }

    private void moveHole(int d){ currentHole=bound(currentHole+d,0,HOLES-1); tensPendingPlayer=-1; saveDraft(false); renderRound(false); }
    private String displayName(int p){ return TextUtils.isEmpty(playerNames[p])?"Player "+(p+1):playerNames[p].trim(); }
    private String courseOrDefault(){ return TextUtils.isEmpty(courseText)?"未入力コース":courseText; }
    private String scoreText(int v){ return v<=0?"-":String.valueOf(v); }
    private String shortName(String s){ if(TextUtils.isEmpty(s))return"P   "; String n=s.length()>4?s.substring(0,4):s; while(n.length()<4)n+=" "; return n; }
    private String pad2(int v){ if(v<=0)return" - "; return v<10?" "+v+" ":v+" "; }
    private String pad3(int v){ if(v<10)return"  "+v; if(v<100)return" "+v; return String.valueOf(v); }
    private String pct(int a,int b){ return b==0?"-":f1(a*100.0/b)+"%"; }
    private String f1(double d){ return String.format(Locale.US,"%.1f",d); }
    private int bound(int v,int min,int max){ return Math.max(min,Math.min(max,v)); }
    private int num(String s,int f){ try{return Integer.parseInt(s.trim());}catch(Exception e){return f;} }
    private long longNum(String s,long f){ try{return Long.parseLong(s.trim());}catch(Exception e){return f;} }
    private long parseDate(String s){ try{ Date d=new SimpleDateFormat("yyyy/MM/dd",Locale.US).parse(s); return d==null?System.currentTimeMillis():d.getTime(); }catch(Exception e){return System.currentTimeMillis();} }
    private String enc(String s){ return Base64.encodeToString((s==null?"":s).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP); }
    private String dec(String s){ try{return new String(Base64.decode(s,Base64.NO_WRAP),StandardCharsets.UTF_8);}catch(Exception e){return"";} }
    private String nowDate(){ return new SimpleDateFormat("yyyy/MM/dd",Locale.US).format(new Date()); }
    private String nowFile(){ return new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date()); }
    private String nowFull(){ return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss",Locale.US).format(new Date()); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private SharedPreferences prefs(){ return getSharedPreferences(PREF_NAME,MODE_PRIVATE); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    private void top(){ if(scroll!=null)scroll.post(()->scroll.fullScroll(View.FOCUS_UP)); }
    private void restoreScroll(int y){ if(scroll!=null)scroll.post(()->scroll.scrollTo(0,y)); }
    private void copy(String label,String value){ ClipboardManager c=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE); if(c!=null)c.setPrimaryClip(ClipData.newPlainText(label,value)); }
    private ArrayList<String> wrap(String line,int max){ ArrayList<String> r=new ArrayList<>(); if(line==null)line=""; if(line.length()==0){r.add("");return r;} for(int i=0;i<line.length();i+=max)r.add(line.substring(i,Math.min(line.length(),i+max))); return r; }
    private interface TextSink{ void set(String value); }

    private static class RoundRecord {
        long timestamp; String date=""; String course=""; String tee=""; int total; int putts; int fw; int teeShots; String pars=""; String p1Scores=""; String puttArray=""; String teeResults=""; String teeClubs=""; String clubPhoto=""; String scoreCard=""; String analysis="";
        String toLine(){ return timestamp+"|"+enc(date)+"|"+enc(course)+"|"+enc(tee)+"|"+total+"|"+putts+"|"+fw+"|"+teeShots+"|"+enc(pars)+"|"+enc(p1Scores)+"|"+enc(puttArray)+"|"+enc(teeResults)+"|"+enc(teeClubs)+"|"+enc(clubPhoto)+"|"+enc(scoreCard)+"|"+enc(analysis); }
        static RoundRecord fromLine(String line){ try{ if(TextUtils.isEmpty(line))return null; String[] p=line.split("\\|",-1); if(p.length<16)return null; RoundRecord r=new RoundRecord(); r.timestamp=Long.parseLong(p[0]); r.date=dec(p[1]); r.course=dec(p[2]); r.tee=dec(p[3]); r.total=Integer.parseInt(p[4]); r.putts=Integer.parseInt(p[5]); r.fw=Integer.parseInt(p[6]); r.teeShots=Integer.parseInt(p[7]); r.pars=dec(p[8]); r.p1Scores=dec(p[9]); r.puttArray=dec(p[10]); r.teeResults=dec(p[11]); r.teeClubs=dec(p[12]); r.clubPhoto=dec(p[13]); r.scoreCard=dec(p[14]); r.analysis=dec(p[15]); return r; }catch(Exception e){return null;} }
        private static String enc(String s){ return Base64.encodeToString((s==null?"":s).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP); }
        private static String dec(String s){ try{return new String(Base64.decode(s,Base64.NO_WRAP),StandardCharsets.UTF_8);}catch(Exception e){return"";} }
    }
}
