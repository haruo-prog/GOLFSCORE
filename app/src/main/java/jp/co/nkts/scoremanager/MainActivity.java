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
import java.io.FileInputStream;
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
    private static final long AUTO_SAVE_INTERVAL_MS = 1000L;

    private static final String PREF_NAME = "nk_score_manager_v2";
    private static final String KEY_PREFIX = "v2_";
    private static final String KEY_HISTORY = "v2_history";
    private static final String KEY_COURSES = "v2_courses";
    private static final String KEY_CLUBS = "v2_clubs";
    private static final String KEY_PDF_TREE_URI = "v2_pdf_tree_uri";
    private static final String KEY_LAST_CLUB_PHOTO = "v2_last_club_photo";

    private static final int REQ_PDF_FOLDER = 801;
    private static final int REQ_BACKUP_CREATE = 802;
    private static final int REQ_RESTORE_OPEN = 803;
    private static final int REQ_CLUB_CAMERA = 804;

    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_ROUND = 1;
    private static final int SCREEN_HISTORY = 2;
    private static final int SCREEN_ANALYSIS = 3;
    private static final int SCREEN_SETTINGS = 4;

    private static final int COLOR_BG = 0xFFF8FAFC;
    private static final int COLOR_CARD = 0xFFFFFFFF;
    private static final int COLOR_PRIMARY = 0xFF166534;
    private static final int COLOR_PRIMARY_DARK = 0xFF14532D;
    private static final int COLOR_PRIMARY_SOFT = 0xFFDCFCE7;
    private static final int COLOR_TEXT = 0xFF0F172A;
    private static final int COLOR_MUTED = 0xFF64748B;
    private static final int COLOR_BORDER = 0xFFE2E8F0;
    private static final int COLOR_PANEL = 0xFFEFF6FF;
    private static final int COLOR_DANGER_SOFT = 0xFFFEE2E2;

    private final int[] defaultPars = {4, 4, 3, 5, 4, 4, 5, 3, 4, 4, 5, 4, 3, 4, 4, 5, 3, 4};
    private final String[] playerCountOptions = {"1名", "2名", "3名", "4名"};
    private final String[] teeResultLabels = {"未選択", "FW", "左ラフ", "右ラフ", "左OB", "右OB"};

    private LinearLayout root;
    private ScrollView scrollView;
    private TextView saveStatusText;

    private int screen = SCREEN_HOME;
    private boolean binding = false;
    private boolean registrationMode = false;
    private int activePlayers = 1;
    private int currentHole = 0;

    private String dateText = "";
    private String courseText = "";
    private String teeText = "";
    private String startTimeText = "";
    private String clubPhotoPath = "";
    private String selectedHistoryText = "";

    private final int[] pars = new int[HOLES];
    private final String[] playerNames = {"Player 1", "Player 2", "Player 3", "Player 4"};
    private final int[][] scores = new int[PLAYERS][HOLES];
    private final int[] player1Putts = new int[HOLES];
    private final int[] teeResults = new int[HOLES];
    private final String[] teeClubs = new String[HOLES];

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
        if (registrationMode) renderRound(false); else renderHome();
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

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_PDF_FOLDER) {
            Uri uri = data.getData();
            if (uri == null) return;
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) {}
            prefs().edit().putString(KEY_PDF_TREE_URI, uri.toString()).commit();
            toast("PDF保存先を記憶しました。");
            renderSettings();
            return;
        }
        if (requestCode == REQ_BACKUP_CREATE) {
            Uri uri = data.getData();
            if (uri != null) writeBackupToUri(uri);
            return;
        }
        if (requestCode == REQ_RESTORE_OPEN) {
            Uri uri = data.getData();
            if (uri != null) restoreBackupFromUri(uri);
            return;
        }
        if (requestCode == REQ_CLUB_CAMERA) {
            Bitmap bitmap = null;
            Bundle extras = data.getExtras();
            if (extras != null && extras.get("data") instanceof Bitmap) bitmap = (Bitmap) extras.get("data");
            if (bitmap == null) {
                toast("写真を取得できませんでした。");
                return;
            }
            saveClubPhotoWebp(bitmap);
        }
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
        dateText = nowDate();
        System.arraycopy(defaultPars, 0, pars, 0, HOLES);
        for (int p = 0; p < PLAYERS; p++) playerNames[p] = "Player " + (p + 1);
        String firstClub = getClubList()[0];
        for (int h = 0; h < HOLES; h++) teeClubs[h] = firstClub;
    }

    private void renderHome() {
        screen = SCREEN_HOME;
        registrationMode = false;
        saveDraft(false);
        root.removeAllViews();
        root.addView(hero("NK Score Manager", "商品化UI V2 / 1画面入力・分析・設定整理"));
        root.addView(homeActions());
        root.addView(recentStatsCard());
        addBottomNav();
        scrollTop();
    }

    private View homeActions() {
        LinearLayout card = card();
        card.addView(section("ラウンド"));
        Button start = button("ラウンド開始", true);
        start.setOnClickListener(v -> {
            resetRound(true);
            renderRound(false);
        });
        card.addView(start, fullWidth());
        if (hasAnyDraft()) {
            Button resume = button("入力中のラウンドに戻る", false);
            resume.setOnClickListener(v -> renderRound(false));
            card.addView(resume, fullWidth());
        }
        Button history = button("履歴を見る", false);
        history.setOnClickListener(v -> renderHistory());
        card.addView(history, fullWidth());
        Button analysis = button("分析を見る", false);
        analysis.setOnClickListener(v -> renderAnalysis());
        card.addView(analysis, fullWidth());
        Button settings = button("設定・バックアップ", false);
        settings.setOnClickListener(v -> renderSettings());
        card.addView(settings, fullWidth());
        return card;
    }

    private View recentStatsCard() {
        LinearLayout card = card();
        card.addView(section("最近の成績"));
        card.addView(panel(buildStatsText(loadHistoryRecords()), true));
        return card;
    }

    private void renderRound(boolean keepScroll) {
        final int y = keepScroll && scrollView != null ? scrollView.getScrollY() : 0;
        screen = SCREEN_ROUND;
        registrationMode = true;
        root.removeAllViews();
        root.addView(roundHeader());
        root.addView(roundSetupCard());
        root.addView(progressCard());
        root.addView(oneScreenInputCard());
        root.addView(roundActionCard());
        saveStatusText = text("自動保存中", 12, COLOR_MUTED, false);
        saveStatusText.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(saveStatusText);
        if (keepScroll) restoreScroll(y); else scrollTop();
    }

    private View roundHeader() {
        LinearLayout card = card();
        TextView title = text((currentHole + 1) + "H  PAR" + pars[currentHole], 30, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(title);
        TextView sub = text(courseTextOrDefault() + " / " + teeText + " / " + startTimeText, 13, COLOR_MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        sub.setPadding(0, dp(4), 0, 0);
        card.addView(sub);
        return card;
    }

    private View roundSetupCard() {
        LinearLayout card = card();
        card.addView(section("ラウンド開始前設定"));
        EditText course = input("コース名");
        course.setText(courseText);
        watch(course, v -> courseText = v);
        EditText tee = input("ティー 例: ブルー");
        tee.setText(teeText);
        watch(tee, v -> teeText = v);
        EditText start = input("スタート時間 / OUT / IN");
        start.setText(startTimeText);
        watch(start, v -> startTimeText = v);
        card.addView(course);
        card.addView(tee);
        card.addView(start);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button saveCourse = button("コース保存", false);
        saveCourse.setOnClickListener(v -> saveCourseTemplate());
        Button loadCourse = button("保存コース読込", false);
        loadCourse.setOnClickListener(v -> loadLatestCourseTemplate());
        row.addView(saveCourse, weighted(1));
        row.addView(loadCourse, weighted(1));
        card.addView(row);

        LinearLayout countRow = new LinearLayout(this);
        countRow.setOrientation(LinearLayout.HORIZONTAL);
        countRow.setGravity(Gravity.CENTER_VERTICAL);
        countRow.addView(text("人数", 14, COLOR_TEXT, true), weighted(1));
        Spinner sp = spinner(playerCountOptions);
        binding = true;
        sp.setSelection(activePlayers - 1);
        binding = false;
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (binding) return;
                int n = position + 1;
                if (n != activePlayers) {
                    activePlayers = n;
                    saveDraft(false);
                    renderRound(true);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        countRow.addView(sp, new LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(countRow);
        for (int p = 0; p < activePlayers; p++) {
            final int idx = p;
            EditText name = input(p == 0 ? "Player1 名前" : "同伴者" + (p + 1));
            name.setText(playerNames[p]);
            watch(name, v -> playerNames[idx] = v);
            card.addView(name);
        }
        return card;
    }

    private View progressCard() {
        LinearLayout card = card();
        card.addView(section("進捗"));
        card.addView(panel("Player1 " + entered(0) + "/18 / 全体未入力 " + missingScores(), true));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < 3; r++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 6; c++) {
                int h = r * 6 + c;
                Button b = new Button(this);
                b.setText(String.valueOf(h + 1));
                b.setAllCaps(false);
                b.setTextSize(12);
                b.setTextColor(h == currentHole ? 0xFFFFFFFF : COLOR_TEXT);
                int fill = h == currentHole ? COLOR_PRIMARY : (holeComplete(h) ? COLOR_PRIMARY_SOFT : COLOR_DANGER_SOFT);
                b.setBackground(rounded(fill, COLOR_BORDER, 12));
                final int target = h;
                b.setOnClickListener(v -> {
                    currentHole = target;
                    saveDraft(false);
                    renderRound(false);
                });
                line.addView(b, weighted(1));
            }
            grid.addView(line);
        }
        card.addView(grid);
        return card;
    }

    private View oneScreenInputCard() {
        LinearLayout card = card();
        card.addView(section("入力"));
        card.addView(parPicker());
        card.addView(player1Input());
        if (activePlayers > 1) card.addView(otherPlayersInput());
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button prev = button("前のH", false);
        prev.setEnabled(currentHole > 0);
        prev.setOnClickListener(v -> moveHole(-1));
        Button next = button("次のH", true);
        next.setEnabled(currentHole < HOLES - 1);
        next.setOnClickListener(v -> moveHole(1));
        nav.addView(prev, weighted(1));
        nav.addView(next, weighted(1));
        card.addView(nav);
        return card;
    }

    private View parPicker() {
        LinearLayout wrap = lite();
        wrap.addView(text("PAR", 12, COLOR_MUTED, true));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int par = 3; par <= 6; par++) {
            final int value = par;
            Button b = choice(String.valueOf(par), pars[currentHole] == par);
            b.setOnClickListener(v -> {
                pars[currentHole] = value;
                saveDraft(false);
                renderRound(true);
            });
            row.addView(b, weighted(1));
        }
        wrap.addView(row);
        return wrap;
    }

    private View player1Input() {
        LinearLayout wrap = lite();
        wrap.addView(text(displayName(0), 18, COLOR_TEXT, true));
        wrap.addView(scoreButtons(0));
        wrap.addView(puttButtons());
        wrap.addView(clubAndResultButtons());
        return wrap;
    }

    private View otherPlayersInput() {
        LinearLayout wrap = lite();
        wrap.addView(text("同伴者スコア", 16, COLOR_TEXT, true));
        for (int p = 1; p < activePlayers; p++) {
            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(0, dp(6), 0, dp(6));
            block.addView(text(displayName(p), 15, COLOR_TEXT, true));
            block.addView(scoreButtons(p));
            wrap.addView(block);
        }
        return wrap;
    }

    private View scoreButtons(int player) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("SCORE / PAR基準", 12, COLOR_MUTED, true);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        group.addView(label);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int par = pars[currentHole];
        int[] values = uniqueScoreValues(new int[]{Math.max(1, par - 1), par, par + 1, par + 2, par + 3, Math.min(SCORE_MAX, par + 4)});
        for (int value : values) {
            final int score = value;
            Button b = choice(String.valueOf(value), scores[player][currentHole] == value);
            b.setMinHeight(dp(54));
            b.setOnClickListener(v -> {
                scores[player][currentHole] = score;
                saveDraft(false);
                renderRound(true);
            });
            row.addView(b, weighted(1));
        }
        group.addView(row);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = button("未入力", false);
        clear.setOnClickListener(v -> {
            scores[player][currentHole] = 0;
            saveDraft(false);
            renderRound(true);
        });
        Button big = button("大叩き 9-15", false);
        big.setOnClickListener(v -> {
            int current = scores[player][currentHole];
            scores[player][currentHole] = current < 9 ? 9 : (current >= 15 ? 9 : current + 1);
            saveDraft(false);
            renderRound(true);
        });
        row2.addView(clear, weighted(1));
        row2.addView(big, weighted(1));
        group.addView(row2);
        return group;
    }

    private View puttButtons() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("PAT", 12, COLOR_MUTED, true);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        group.addView(label);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int v = 1; v <= 4; v++) {
            final int putt = v;
            String labelText = v == 4 ? "4+" : String.valueOf(v);
            Button b = choice(labelText, player1Putts[currentHole] == v);
            b.setOnClickListener(x -> {
                player1Putts[currentHole] = putt;
                saveDraft(false);
                renderRound(true);
            });
            row.addView(b, weighted(1));
        }
        group.addView(row);
        return group;
    }

    private View clubAndResultButtons() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("Tee / 使用クラブと結果", 12, COLOR_MUTED, true);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        group.addView(label);
        Spinner clubSpinner = spinner(getClubList());
        String[] clubs = getClubList();
        int selected = 0;
        for (int i = 0; i < clubs.length; i++) if (clubs[i].equals(teeClubs[currentHole])) selected = i;
        binding = true;
        clubSpinner.setSelection(selected);
        binding = false;
        clubSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (binding) return;
                teeClubs[currentHole] = getClubList()[position];
                saveDraft(false);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        group.addView(clubSpinner);
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        addTeeButton(row1, "FW", 1);
        addTeeButton(row1, "左ラフ", 2);
        addTeeButton(row1, "右ラフ", 3);
        group.addView(row1);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        addTeeButton(row2, "左OB", 4);
        addTeeButton(row2, "右OB", 5);
        Button clear = button("未選択", false);
        clear.setOnClickListener(v -> {
            teeResults[currentHole] = 0;
            saveDraft(false);
            renderRound(true);
        });
        row2.addView(clear, weighted(1));
        group.addView(row2);
        return group;
    }

    private void addTeeButton(LinearLayout row, String label, int value) {
        Button b = choice(label, teeResults[currentHole] == value);
        b.setOnClickListener(v -> {
            teeResults[currentHole] = value;
            saveDraft(false);
            renderRound(true);
        });
        row.addView(b, weighted(1));
    }

    private View roundActionCard() {
        LinearLayout card = card();
        card.addView(section("ラウンド後"));
        Button photo = button("当日クラブを撮影してWebP保存", false);
        photo.setOnClickListener(v -> captureClubPhoto());
        card.addView(photo, fullWidth());
        if (!TextUtils.isEmpty(clubPhotoPath)) card.addView(panel("クラブ写真: " + clubPhotoPath, false));
        Button finish = button("保存して分析を見る", true);
        finish.setOnClickListener(v -> finishRound());
        card.addView(finish, fullWidth());
        return card;
    }

    private void renderHistory() {
        screen = SCREEN_HISTORY;
        registrationMode = false;
        saveDraft(false);
        root.removeAllViews();
        root.addView(hero("履歴", "過去ラウンドとスコアカード"));
        LinearLayout card = card();
        ArrayList<RoundRecord> records = loadHistoryRecords();
        if (records.isEmpty()) {
            card.addView(panel("履歴はまだありません。", false));
        } else {
            for (RoundRecord r : records) {
                LinearLayout row = lite();
                row.addView(text(r.date + " " + r.course + " / " + r.total + " / PAT " + r.putts, 14, COLOR_TEXT, true));
                LinearLayout buttons = new LinearLayout(this);
                buttons.setOrientation(LinearLayout.HORIZONTAL);
                Button detail = button("詳細", false);
                detail.setOnClickListener(v -> {
                    selectedHistoryText = r.scoreCard + "\n\n" + r.analysis;
                    renderHistory();
                });
                Button pdf = button("PDF", true);
                pdf.setOnClickListener(v -> exportPdf(r.scoreCard, r.analysis));
                buttons.addView(detail, weighted(1));
                buttons.addView(pdf, weighted(1));
                row.addView(buttons);
                card.addView(row);
            }
        }
        if (!TextUtils.isEmpty(selectedHistoryText)) card.addView(panel(selectedHistoryText, false));
        root.addView(card);
        addBottomNav();
        scrollTop();
    }

    private void renderAnalysis() {
        screen = SCREEN_ANALYSIS;
        registrationMode = false;
        saveDraft(false);
        root.removeAllViews();
        root.addView(hero("分析", "コース別・ホール別・クラブ別"));
        LinearLayout card = card();
        ArrayList<RoundRecord> records = loadHistoryRecords();
        card.addView(panel(buildStatsText(records) + "\n\n" + buildCourseAnalysis(records) + "\n" + buildClubAnalysis(records) + "\n" + buildHoleAnalysis(records), true));
        root.addView(card);
        addBottomNav();
        scrollTop();
    }

    private void renderSettings() {
        screen = SCREEN_SETTINGS;
        registrationMode = false;
        saveDraft(false);
        root.removeAllViews();
        root.addView(hero("設定", "PDF保存先・クラブセット・バックアップ"));
        LinearLayout card = card();
        card.addView(section("PDF保存先"));
        String tree = prefs().getString(KEY_PDF_TREE_URI, "");
        card.addView(panel(TextUtils.isEmpty(tree) ? "未設定：内部Documentsへ保存" : "設定済み：選択フォルダを記憶中", false));
        Button pdf = button("PDF保存先を指定・変更", true);
        pdf.setOnClickListener(v -> choosePdfFolder());
        card.addView(pdf, fullWidth());
        card.addView(section("クラブセット"));
        EditText clubs = input("クラブをカンマ区切りで入力");
        clubs.setText(TextUtils.join(",", getClubList()));
        card.addView(clubs);
        Button saveClubs = button("クラブセット保存", true);
        saveClubs.setOnClickListener(v -> {
            prefs().edit().putString(KEY_CLUBS, clubs.getText().toString()).commit();
            toast("クラブセットを保存しました。");
        });
        card.addView(saveClubs, fullWidth());
        card.addView(section("バックアップ"));
        Button backup = button("Google Driveへバックアップ", false);
        backup.setOnClickListener(v -> createBackupDocument());
        Button restore = button("Google Driveからリストア", false);
        restore.setOnClickListener(v -> openBackupDocument());
        card.addView(backup, fullWidth());
        card.addView(restore, fullWidth());
        card.addView(section("コーステンプレート"));
        card.addView(panel(courseTemplatesSummary(), false));
        root.addView(card);
        addBottomNav();
        scrollTop();
    }

    private void addBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button home = button("ホーム", screen == SCREEN_HOME);
        home.setOnClickListener(v -> renderHome());
        Button hist = button("履歴", screen == SCREEN_HISTORY);
        hist.setOnClickListener(v -> renderHistory());
        Button ana = button("分析", screen == SCREEN_ANALYSIS);
        ana.setOnClickListener(v -> renderAnalysis());
        Button set = button("設定", screen == SCREEN_SETTINGS);
        set.setOnClickListener(v -> renderSettings());
        nav.addView(home, weighted(1));
        nav.addView(hist, weighted(1));
        nav.addView(ana, weighted(1));
        nav.addView(set, weighted(1));
        root.addView(nav);
    }

    private void finishRound() {
        int missing = missingScores();
        if (missing > 0) {
            toast("未入力スコアがあります: " + missing + "件");
            renderRound(true);
            return;
        }
        RoundRecord record = buildRecord();
        ArrayList<RoundRecord> records = loadHistoryRecords();
        records.add(0, record);
        saveHistoryRecords(records);
        selectedHistoryText = record.scoreCard + "\n\n" + record.analysis;
        resetRound(false);
        toast("保存しました。分析を表示します。");
        renderHistory();
    }

    private RoundRecord buildRecord() {
        RoundRecord r = new RoundRecord();
        r.timestamp = parseDate(dateText);
        r.date = dateText;
        r.course = courseTextOrDefault();
        r.tee = teeText;
        r.pars = serializeIntArray(pars);
        r.p1Scores = serializeIntArray(scores[0]);
        r.putts = sum(player1Putts);
        r.puttArray = serializeIntArray(player1Putts);
        r.teeResults = serializeIntArray(teeResults);
        r.teeClubs = serializeStringArray(teeClubs);
        r.total = totalScore(0);
        r.bestDiff = totalScore(0) - sum(pars);
        r.fw = countTee(1);
        r.teeShots = countTeeTargets();
        r.clubPhoto = clubPhotoPath;
        r.scoreCard = buildScoreCard(r);
        r.analysis = buildRoundAdvice();
        return r;
    }

    private String buildScoreCard(RoundRecord r) {
        StringBuilder b = new StringBuilder();
        b.append("NK SCORE CARD\n");
        b.append(r.date).append("  ").append(r.course).append("  ").append(r.tee).append("\n");
        b.append("HOLE  1  2  3  4  5  6  7  8  9 |OUT|10 11 12 13 14 15 16 17 18 |IN |TOTAL\n");
        b.append("PAR  ").append(rowValues(pars, false)).append("\n");
        for (int p = 0; p < activePlayers; p++) b.append(shortName(displayName(p))).append("   ").append(rowValues(scores[p], true)).append("\n");
        b.append("\nPlayer1 PAT: ").append(sum(player1Putts));
        b.append(" / FW: ").append(countTee(1)).append("/").append(countTeeTargets()).append("\n");
        if (!TextUtils.isEmpty(clubPhotoPath)) b.append("Club Photo: ").append(clubPhotoPath).append("\n");
        return b.toString();
    }

    private String buildRoundAdvice() {
        int total = totalScore(0);
        int puttTotal = sum(player1Putts);
        int left = countTee(2) + countTee(4);
        int right = countTee(3) + countTee(5);
        int ob = countTee(4) + countTee(5);
        int par3Diff = diffByPar(3);
        int par4Diff = diffByPar(4);
        int par5Diff = diffByPar(5);
        StringBuilder b = new StringBuilder();
        b.append("今日の自動分析\n");
        b.append("スコア: ").append(total).append(" / PAT: ").append(puttTotal).append(" / OB系: ").append(ob).append("\n");
        if (puttTotal >= 36) b.append("・PAT数が多めです。3PAT以上のホールを減らすと即スコア改善につながります。\n");
        if (right > left) b.append("・右方向のミスが多い傾向です。ティーショットの向きとフェース管理を確認。\n");
        else if (left > right) b.append("・左方向のミスが多い傾向です。引っかけ/巻き込みを確認。\n");
        if (ob >= 2) b.append("・OBが複数回あります。狭いホールは番手を落とす戦略が有効です。\n");
        if (par5Diff > par4Diff && par5Diff > par3Diff) b.append("・PAR5で落としています。2打目以降のクラブ選択を見直す価値があります。\n");
        b.append("PAR3差分: ").append(par3Diff).append(" / PAR4差分: ").append(par4Diff).append(" / PAR5差分: ").append(par5Diff).append("\n");
        b.append("\nクラブ別傾向\n").append(currentRoundClubAdvice());
        return b.toString();
    }

    private String currentRoundClubAdvice() {
        LinkedHashMap<String, int[]> map = new LinkedHashMap<>();
        for (int h = 0; h < HOLES; h++) {
            String club = TextUtils.isEmpty(teeClubs[h]) ? "未選択" : teeClubs[h];
            int[] a = map.get(club);
            if (a == null) a = new int[4];
            a[0]++;
            if (teeResults[h] == 1) a[1]++;
            if (teeResults[h] == 4 || teeResults[h] == 5) a[2]++;
            if (teeResults[h] == 2 || teeResults[h] == 4) a[3]++;
            map.put(club, a);
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, int[]> e : map.entrySet()) {
            int[] a = e.getValue();
            if (a[0] == 0) continue;
            b.append(e.getKey()).append("：使用").append(a[0]).append(" / FW").append(a[1]).append(" / OB").append(a[2]).append("\n");
        }
        return b.toString();
    }

    private String buildCourseAnalysis(ArrayList<RoundRecord> records) {
        if (records.isEmpty()) return "コース別: データなし\n";
        LinkedHashMap<String, int[]> map = new LinkedHashMap<>();
        for (RoundRecord r : records) {
            int[] a = map.get(r.course);
            if (a == null) a = new int[]{0, 0, 999};
            a[0]++;
            a[1] += r.total;
            if (r.total < a[2]) a[2] = r.total;
            map.put(r.course, a);
        }
        StringBuilder b = new StringBuilder("コース別\n");
        for (Map.Entry<String, int[]> e : map.entrySet()) {
            int[] a = e.getValue();
            b.append(e.getKey()).append("：平均").append(format1(a[1] * 1.0 / a[0])).append(" / ベスト").append(a[2]).append(" / ").append(a[0]).append("回\n");
        }
        return b.toString();
    }

    private String buildClubAnalysis(ArrayList<RoundRecord> records) {
        LinkedHashMap<String, int[]> map = new LinkedHashMap<>();
        for (RoundRecord r : records) {
            String[] clubs = deserializeStringArray(r.teeClubs, HOLES, "-");
            int[] results = deserializeIntArray(r.teeResults, HOLES, 0);
            for (int h = 0; h < HOLES; h++) {
                String club = clubs[h];
                if (TextUtils.isEmpty(club) || "-".equals(club)) continue;
                int[] a = map.get(club);
                if (a == null) a = new int[4];
                a[0]++;
                if (results[h] == 1) a[1]++;
                if (results[h] == 4 || results[h] == 5) a[2]++;
                if (results[h] == 3 || results[h] == 5) a[3]++;
                map.put(club, a);
            }
        }
        if (map.isEmpty()) return "クラブ別: データなし\n";
        StringBuilder b = new StringBuilder("クラブ別\n");
        for (Map.Entry<String, int[]> e : map.entrySet()) {
            int[] a = e.getValue();
            b.append(e.getKey()).append("：使用").append(a[0]).append(" / FW率").append(percent(a[1], a[0])).append(" / OB").append(a[2]).append(" / 右ミス").append(a[3]).append("\n");
        }
        return b.toString();
    }

    private String buildHoleAnalysis(ArrayList<RoundRecord> records) {
        if (records.isEmpty()) return "ホール別: データなし\n";
        int[] totalDiff = new int[HOLES];
        int[] count = new int[HOLES];
        for (RoundRecord r : records) {
            int[] s = deserializeIntArray(r.p1Scores, HOLES, 0);
            int[] p = deserializeIntArray(r.pars, HOLES, 4);
            for (int h = 0; h < HOLES; h++) if (s[h] > 0) {
                totalDiff[h] += s[h] - p[h];
                count[h]++;
            }
        }
        int worst = -1;
        double worstAvg = -99;
        for (int h = 0; h < HOLES; h++) if (count[h] > 0) {
            double avg = totalDiff[h] * 1.0 / count[h];
            if (avg > worstAvg) { worstAvg = avg; worst = h; }
        }
        if (worst < 0) return "ホール別: データなし\n";
        return "苦手ホール\n" + (worst + 1) + "H 平均 " + (worstAvg >= 0 ? "+" : "") + format1(worstAvg) + "\n";
    }

    private String buildStatsText(ArrayList<RoundRecord> records) {
        long now = System.currentTimeMillis();
        return averageText(records, now - 90L * 24L * 60L * 60L * 1000L, "過去3カ月") + "\n" + averageText(records, now - 365L * 24L * 60L * 60L * 1000L, "過去1年");
    }

    private String averageText(ArrayList<RoundRecord> records, long since, String label) {
        int c = 0, total = 0, putt = 0, fw = 0, tee = 0;
        for (RoundRecord r : records) if (r.timestamp >= since) {
            c++;
            total += r.total;
            putt += r.putts;
            fw += r.fw;
            tee += r.teeShots;
        }
        if (c == 0) return label + ": データなし";
        return label + ": " + c + "回 / 平均" + format1(total * 1.0 / c) + " / PAT" + format1(putt * 1.0 / c) + " / FW" + percent(fw, tee);
    }

    private void moveHole(int delta) {
        currentHole = bound(currentHole + delta, 0, HOLES - 1);
        saveDraft(false);
        renderRound(false);
    }

    private void saveCourseTemplate() {
        if (TextUtils.isEmpty(courseText.trim())) {
            toast("コース名を入力してください。");
            return;
        }
        String line = encode(courseText.trim()) + "|" + encode(teeText.trim()) + "|" + serializeIntArray(pars);
        String old = prefs().getString(KEY_COURSES, "");
        prefs().edit().putString(KEY_COURSES, line + (TextUtils.isEmpty(old) ? "" : "\n" + old)).commit();
        toast("コーステンプレートを保存しました。");
    }

    private void loadLatestCourseTemplate() {
        String raw = prefs().getString(KEY_COURSES, "");
        if (TextUtils.isEmpty(raw)) {
            toast("保存済みコースがありません。");
            return;
        }
        String[] lines = raw.split("\n", -1);
        String[] parts = lines[0].split("\\|", -1);
        if (parts.length >= 3) {
            courseText = decode(parts[0]);
            teeText = decode(parts[1]);
            int[] restored = deserializeIntArray(parts[2], HOLES, 4);
            System.arraycopy(restored, 0, pars, 0, HOLES);
            saveDraft(false);
            toast("最新の保存コースを読み込みました。");
            renderRound(false);
        }
    }

    private String courseTemplatesSummary() {
        String raw = prefs().getString(KEY_COURSES, "");
        if (TextUtils.isEmpty(raw)) return "保存済みコースはありません。";
        StringBuilder b = new StringBuilder();
        String[] lines = raw.split("\n", -1);
        for (int i = 0; i < lines.length && i < 10; i++) {
            String[] parts = lines[i].split("\\|", -1);
            if (parts.length >= 2) b.append(i + 1).append(". ").append(decode(parts[0])).append(" ").append(decode(parts[1])).append("\n");
        }
        return b.toString().trim();
    }

    private void choosePdfFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PDF_FOLDER);
    }

    private void createBackupDocument() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "NKScore_Backup_" + nowFile() + ".txt");
        startActivityForResult(intent, REQ_BACKUP_CREATE);
    }

    private void openBackupDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQ_RESTORE_OPEN);
    }

    private void captureClubPhoto() {
        try {
            startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), REQ_CLUB_CAMERA);
        } catch (Exception e) {
            toast("カメラを起動できませんでした。");
        }
    }

    private void saveClubPhotoWebp(Bitmap bitmap) {
        try {
            File dir = new File(getFilesDir(), "club_photos");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "club_" + nowFile() + ".webp");
            FileOutputStream out = new FileOutputStream(file);
            if (Build.VERSION.SDK_INT >= 30) bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 70, out);
            else bitmap.compress(Bitmap.CompressFormat.WEBP, 70, out);
            out.close();
            clubPhotoPath = file.getAbsolutePath();
            prefs().edit().putString(KEY_LAST_CLUB_PHOTO, clubPhotoPath).commit();
            saveDraft(false);
            toast("クラブ写真をWebPで保存しました。");
            renderRound(true);
        } catch (Exception e) {
            toast("クラブ写真の保存に失敗しました。");
        }
    }

    private void exportPdf(String scoreCard, String analysis) {
        String content = scoreCard + "\n\n" + analysis;
        String fileName = "NKScore_" + nowFile() + ".pdf";
        String tree = prefs().getString(KEY_PDF_TREE_URI, "");
        if (!TextUtils.isEmpty(tree)) {
            try {
                Uri treeUri = Uri.parse(tree);
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
                Uri fileUri = DocumentsContract.createDocument(getContentResolver(), parent, "application/pdf", fileName);
                if (fileUri == null) throw new Exception("null");
                OutputStream out = getContentResolver().openOutputStream(fileUri);
                if (out == null) throw new Exception("null");
                writePdf(content, out);
                out.close();
                copyText("PDF URI", fileUri.toString());
                toast("PDFを指定保存先へ保存しました。");
                return;
            } catch (Exception e) {
                toast("指定保存先に保存できません。内部保存します。");
            }
        }
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fileName);
            FileOutputStream out = new FileOutputStream(file);
            writePdf(content, out);
            out.close();
            copyText("PDF Path", file.getAbsolutePath());
            toast("PDFを内部保存しました。パスをコピーしました。");
        } catch (Exception e) {
            toast("PDF保存に失敗しました。");
        }
    }

    private void writePdf(String content, OutputStream out) throws Exception {
        PdfDocument pdf = new PdfDocument();
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setTextSize(10f);
        Paint title = new Paint();
        title.setAntiAlias(true);
        title.setTextSize(18f);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        int w = 842, h = 595, margin = 28, y = margin, pageNo = 1;
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(w, h, pageNo).create());
        Canvas c = page.getCanvas();
        c.drawText("NK Score Manager", margin, y, title);
        y += 24;
        for (String line : content.split("\n", -1)) {
            for (String part : wrap(line, 115)) {
                if (y > h - margin) {
                    pdf.finishPage(page);
                    page = pdf.startPage(new PdfDocument.PageInfo.Builder(w, h, ++pageNo).create());
                    c = page.getCanvas();
                    y = margin;
                }
                c.drawText(part, margin, y, p);
                y += 14;
            }
        }
        pdf.finishPage(page);
        pdf.writeTo(out);
        pdf.close();
    }

    private void writeBackupToUri(Uri uri) {
        try {
            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new Exception("open failed");
            out.write(buildBackup().getBytes(StandardCharsets.UTF_8));
            out.close();
            toast("バックアップを保存しました。");
        } catch (Exception e) {
            toast("バックアップ保存に失敗しました。");
        }
    }

    private void restoreBackupFromUri(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new Exception("open failed");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int n;
            while ((n = in.read(data)) > 0) buffer.write(data, 0, n);
            in.close();
            applyBackup(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            initDefaults();
            restoreDraft();
            toast("リストアしました。");
            renderHome();
        } catch (Exception e) {
            toast("リストアに失敗しました。");
        }
    }

    private String buildBackup() {
        StringBuilder b = new StringBuilder("NKSM_BACKUP_V2\n");
        Map<String, ?> all = prefs().getAll();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            Object v = e.getValue();
            String key = encode(e.getKey());
            if (v instanceof String) b.append("S|").append(key).append("|").append(encode((String) v)).append("\n");
            else if (v instanceof Integer) b.append("I|").append(key).append("|").append(v).append("\n");
            else if (v instanceof Boolean) b.append("B|").append(key).append("|").append(v).append("\n");
            else if (v instanceof Long) b.append("L|").append(key).append("|").append(v).append("\n");
        }
        return b.toString();
    }

    private void applyBackup(String raw) throws Exception {
        if (TextUtils.isEmpty(raw) || !raw.startsWith("NKSM_BACKUP_V2")) throw new Exception("invalid");
        SharedPreferences.Editor editor = prefs().edit();
        editor.clear();
        for (String line : raw.split("\n", -1)) {
            if (TextUtils.isEmpty(line) || line.equals("NKSM_BACKUP_V2")) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length < 3) continue;
            String key = decode(parts[1]);
            if ("S".equals(parts[0])) editor.putString(key, decode(parts[2]));
            else if ("I".equals(parts[0])) editor.putInt(key, parseInt(parts[2], 0));
            else if ("B".equals(parts[0])) editor.putBoolean(key, Boolean.parseBoolean(parts[2]));
            else if ("L".equals(parts[0])) editor.putLong(key, parseLong(parts[2], 0));
        }
        editor.commit();
    }

    private void saveDraft(boolean showToast) {
        SharedPreferences.Editor e = prefs().edit();
        e.putBoolean(KEY_PREFIX + "registration", registrationMode);
        e.putString(KEY_PREFIX + "date", dateText);
        e.putString(KEY_PREFIX + "course", courseText);
        e.putString(KEY_PREFIX + "tee", teeText);
        e.putString(KEY_PREFIX + "start", startTimeText);
        e.putString(KEY_LAST_CLUB_PHOTO, clubPhotoPath);
        e.putInt(KEY_PREFIX + "players", activePlayers);
        e.putInt(KEY_PREFIX + "hole", currentHole);
        e.putString(KEY_PREFIX + "pars", serializeIntArray(pars));
        e.putString(KEY_PREFIX + "names", serializeStringArray(playerNames));
        e.putString(KEY_PREFIX + "putts", serializeIntArray(player1Putts));
        e.putString(KEY_PREFIX + "teeResults", serializeIntArray(teeResults));
        e.putString(KEY_PREFIX + "teeClubs", serializeStringArray(teeClubs));
        for (int p = 0; p < PLAYERS; p++) e.putString(KEY_PREFIX + "scores" + p, serializeIntArray(scores[p]));
        boolean ok = e.commit();
        if (saveStatusText != null) saveStatusText.setText(ok ? "保存済み " + nowFull() : "保存失敗");
        if (showToast) toast(ok ? "保存しました。" : "保存に失敗しました。");
    }

    private void restoreDraft() {
        SharedPreferences p = prefs();
        registrationMode = p.getBoolean(KEY_PREFIX + "registration", false);
        dateText = p.getString(KEY_PREFIX + "date", nowDate());
        courseText = p.getString(KEY_PREFIX + "course", "");
        teeText = p.getString(KEY_PREFIX + "tee", "");
        startTimeText = p.getString(KEY_PREFIX + "start", "");
        clubPhotoPath = p.getString(KEY_LAST_CLUB_PHOTO, "");
        activePlayers = bound(p.getInt(KEY_PREFIX + "players", 1), 1, PLAYERS);
        currentHole = bound(p.getInt(KEY_PREFIX + "hole", 0), 0, HOLES - 1);
        restoreIntArray(p.getString(KEY_PREFIX + "pars", ""), pars, defaultPars, 3, 6);
        restoreStringArray(p.getString(KEY_PREFIX + "names", ""), playerNames);
        restoreIntArray(p.getString(KEY_PREFIX + "putts", ""), player1Putts, null, 0, 8);
        restoreIntArray(p.getString(KEY_PREFIX + "teeResults", ""), teeResults, null, 0, 5);
        restoreStringArray(p.getString(KEY_PREFIX + "teeClubs", ""), teeClubs);
        for (int i = 0; i < HOLES; i++) if (TextUtils.isEmpty(teeClubs[i])) teeClubs[i] = getClubList()[0];
        for (int i = 0; i < PLAYERS; i++) restoreIntArray(p.getString(KEY_PREFIX + "scores" + i, ""), scores[i], null, 0, SCORE_MAX);
    }

    private void resetRound(boolean keepMode) {
        dateText = nowDate();
        courseText = "";
        teeText = "";
        startTimeText = "";
        clubPhotoPath = "";
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
            teeClubs[h] = getClubList()[0];
        }
        registrationMode = keepMode;
    }

    private ArrayList<RoundRecord> loadHistoryRecords() {
        ArrayList<RoundRecord> list = new ArrayList<>();
        String raw = prefs().getString(KEY_HISTORY, "");
        if (TextUtils.isEmpty(raw)) return list;
        for (String line : raw.split("\n", -1)) {
            RoundRecord r = RoundRecord.fromLine(line);
            if (r != null) list.add(r);
        }
        return list;
    }

    private void saveHistoryRecords(ArrayList<RoundRecord> list) {
        ArrayList<String> lines = new ArrayList<>();
        for (int i = 0; i < list.size() && i < 300; i++) lines.add(list.get(i).toLine());
        prefs().edit().putString(KEY_HISTORY, TextUtils.join("\n", lines)).commit();
    }

    private String[] getClubList() {
        String raw = prefs().getString(KEY_CLUBS, "DR,3W,5W,UT,5I,6I,7I,8I,9I,PW,50,56,60,PT");
        String[] parts = raw.split(",");
        ArrayList<String> out = new ArrayList<>();
        for (String s : parts) {
            String v = s.trim();
            if (!TextUtils.isEmpty(v)) out.add(v);
        }
        if (out.isEmpty()) out.add("DR");
        return out.toArray(new String[0]);
    }

    private boolean hasAnyDraft() {
        if (!TextUtils.isEmpty(courseText) || !TextUtils.isEmpty(teeText) || !TextUtils.isEmpty(startTimeText)) return true;
        for (int p = 0; p < PLAYERS; p++) for (int h = 0; h < HOLES; h++) if (scores[p][h] > 0) return true;
        return false;
    }

    private boolean holeComplete(int h) {
        for (int p = 0; p < activePlayers; p++) if (scores[p][h] == 0) return false;
        return true;
    }

    private int missingScores() {
        int m = 0;
        for (int p = 0; p < activePlayers; p++) for (int h = 0; h < HOLES; h++) if (scores[p][h] == 0) m++;
        return m;
    }

    private int entered(int p) {
        int n = 0;
        for (int h = 0; h < HOLES; h++) if (scores[p][h] > 0) n++;
        return n;
    }

    private int countTee(int code) {
        int n = 0;
        for (int v : teeResults) if (v == code) n++;
        return n;
    }

    private int countTeeTargets() {
        int n = 0;
        for (int v : teeResults) if (v > 0) n++;
        return n;
    }

    private int diffByPar(int par) {
        int d = 0;
        for (int h = 0; h < HOLES; h++) if (pars[h] == par && scores[0][h] > 0) d += scores[0][h] - pars[h];
        return d;
    }

    private int totalScore(int player) {
        int total = 0;
        for (int h = 0; h < HOLES; h++) total += scores[player][h];
        return total;
    }

    private int sum(int[] a) {
        int total = 0;
        for (int v : a) total += v;
        return total;
    }

    private String rowValues(int[] values, boolean hideZero) {
        StringBuilder b = new StringBuilder();
        int out = 0, in = 0, total = 0;
        for (int h = 0; h < HOLES; h++) {
            int v = values[h];
            b.append(hideZero && v == 0 ? " - " : pad2(v));
            if (h < 9) out += v; else in += v;
            total += v;
            if (h == 8) b.append("|").append(pad2(out)).append("|");
        }
        b.append("|").append(pad2(in)).append("|").append(pad3(total));
        return b.toString();
    }

    private int[] uniqueScoreValues(int[] input) {
        ArrayList<Integer> out = new ArrayList<>();
        for (int v : input) {
            int b = bound(v, 1, SCORE_MAX);
            if (!out.contains(b)) out.add(b);
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < out.size(); i++) arr[i] = out.get(i);
        return arr;
    }

    private int[] deserializeIntArray(String raw, int length, int fallback) {
        int[] arr = new int[length];
        for (int i = 0; i < length; i++) arr[i] = fallback;
        if (TextUtils.isEmpty(raw)) return arr;
        String[] parts = raw.split(",", -1);
        for (int i = 0; i < length && i < parts.length; i++) arr[i] = parseInt(parts[i], fallback);
        return arr;
    }

    private String[] deserializeStringArray(String raw, int length, String fallback) {
        String[] arr = new String[length];
        for (int i = 0; i < length; i++) arr[i] = fallback;
        if (TextUtils.isEmpty(raw)) return arr;
        String[] parts = raw.split(",", -1);
        for (int i = 0; i < length && i < parts.length; i++) arr[i] = decode(parts[i]);
        return arr;
    }

    private String serializeIntArray(int[] arr) {
        ArrayList<String> list = new ArrayList<>();
        for (int v : arr) list.add(String.valueOf(v));
        return TextUtils.join(",", list);
    }

    private void restoreIntArray(String raw, int[] target, int[] fallback, int min, int max) {
        if (fallback != null) System.arraycopy(fallback, 0, target, 0, Math.min(fallback.length, target.length));
        if (TextUtils.isEmpty(raw)) return;
        String[] parts = raw.split(",", -1);
        for (int i = 0; i < target.length && i < parts.length; i++) target[i] = bound(parseInt(parts[i], target[i]), min, max);
    }

    private String serializeStringArray(String[] arr) {
        ArrayList<String> list = new ArrayList<>();
        for (String s : arr) list.add(encode(s == null ? "" : s));
        return TextUtils.join(",", list);
    }

    private void restoreStringArray(String raw, String[] target) {
        if (TextUtils.isEmpty(raw)) return;
        String[] parts = raw.split(",", -1);
        for (int i = 0; i < target.length && i < parts.length; i++) target[i] = decode(parts[i]);
    }

    private View hero(String title, String sub) {
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("ic_launcher", "drawable", getPackageName()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(68), dp(68));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(logo, lp);
        TextView t = text(title, 25, COLOR_TEXT, true);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(t);
        TextView s = text(sub, 13, COLOR_MUTED, false);
        s.setGravity(Gravity.CENTER_HORIZONTAL);
        s.setPadding(0, dp(4), 0, 0);
        card.addView(s);
        return card;
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(14), dp(14), dp(14), dp(14));
        v.setBackground(rounded(COLOR_CARD, COLOR_BORDER, 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(6), 0, dp(6));
        v.setLayoutParams(p);
        return v;
    }

    private LinearLayout lite() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(10), dp(10), dp(10), dp(10));
        v.setBackground(rounded(0xFFF8FAFC, COLOR_BORDER, 16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(5), 0, dp(5));
        v.setLayoutParams(p);
        return v;
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView section(String s) {
        TextView v = text(s, 16, COLOR_TEXT, true);
        v.setPadding(0, 0, 0, dp(8));
        return v;
    }

    private TextView panel(String s, boolean important) {
        TextView v = text(s, important ? 15 : 13, COLOR_TEXT, important);
        v.setBackground(rounded(important ? COLOR_PRIMARY_SOFT : COLOR_PANEL, important ? COLOR_PRIMARY_SOFT : COLOR_BORDER, 14));
        v.setPadding(dp(12), dp(12), dp(12), dp(12));
        return v;
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(0xFFFFFFFF);
        b.setMinHeight(dp(48));
        b.setBackgroundResource(getResources().getIdentifier(primary ? "button_bg" : "secondary_button_bg", "drawable", getPackageName()));
        return b;
    }

    private Button choice(String label, boolean selected) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(selected ? 0xFFFFFFFF : COLOR_TEXT);
        b.setMinHeight(dp(50));
        b.setBackground(rounded(selected ? COLOR_PRIMARY : 0xFFFFFFFF, selected ? COLOR_PRIMARY_DARK : COLOR_BORDER, 14));
        return b;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setTextColor(COLOR_TEXT);
        e.setHintTextColor(0xFF94A3B8);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setPadding(dp(8), dp(6), dp(8), dp(6));
        return e;
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        return s;
    }

    private void watch(EditText e, TextSink sink) {
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                sink.set(s == null ? "" : s.toString());
                saveDraft(false);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private LinearLayout.LayoutParams fullWidth() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(5), 0, dp(5));
        return p;
    }

    private LinearLayout.LayoutParams weighted(float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        return p;
    }

    private String displayName(int p) { return TextUtils.isEmpty(playerNames[p]) ? "Player " + (p + 1) : playerNames[p].trim(); }
    private String courseTextOrDefault() { return TextUtils.isEmpty(courseText) ? "未入力コース" : courseText; }
    private String shortName(String s) { if (TextUtils.isEmpty(s)) return "P   "; String n = s.length() > 4 ? s.substring(0, 4) : s; while (n.length() < 4) n += " "; return n; }
    private String pad2(int v) { if (v <= 0) return " - "; return v < 10 ? " " + v + " " : v + " "; }
    private String pad3(int v) { if (v < 10) return "  " + v; if (v < 100) return " " + v; return String.valueOf(v); }
    private String percent(int a, int b) { return b == 0 ? "-" : format1(a * 100.0 / b) + "%"; }
    private String format1(double d) { return String.format(Locale.US, "%.1f", d); }
    private int bound(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private int parseInt(String s, int f) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return f; } }
    private long parseLong(String s, long f) { try { return Long.parseLong(s.trim()); } catch (Exception e) { return f; } }
    private long parseDate(String s) { try { Date d = new SimpleDateFormat("yyyy/MM/dd", Locale.US).parse(s); return d == null ? System.currentTimeMillis() : d.getTime(); } catch (Exception e) { return System.currentTimeMillis(); } }
    private String encode(String s) { return Base64.encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP); }
    private String decode(String s) { try { return new String(Base64.decode(s, Base64.NO_WRAP), StandardCharsets.UTF_8); } catch (Exception e) { return ""; } }
    private String nowDate() { return new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date()); }
    private String nowFile() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private String nowFull() { return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(new Date()); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private SharedPreferences prefs() { return getSharedPreferences(PREF_NAME, MODE_PRIVATE); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private void scrollTop() { if (scrollView != null) scrollView.post(() -> scrollView.fullScroll(View.FOCUS_UP)); }
    private void restoreScroll(int y) { if (scrollView != null) scrollView.post(() -> scrollView.scrollTo(0, y)); }
    private void copyText(String label, String value) { ClipboardManager c = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); if (c != null) c.setPrimaryClip(ClipData.newPlainText(label, value)); }
    private ArrayList<String> wrap(String line, int max) { ArrayList<String> r = new ArrayList<>(); if (line == null) line = ""; if (line.length() == 0) { r.add(""); return r; } for (int i = 0; i < line.length(); i += max) r.add(line.substring(i, Math.min(line.length(), i + max))); return r; }
    private void startAutoSaveTimer() { autoSaveHandler.removeCallbacks(autoSaveRunnable); autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_INTERVAL_MS); }

    private interface TextSink { void set(String value); }

    private static class RoundRecord {
        long timestamp;
        String date = "";
        String course = "";
        String tee = "";
        int total;
        int putts;
        int bestDiff;
        int fw;
        int teeShots;
        String pars = "";
        String p1Scores = "";
        String puttArray = "";
        String teeResults = "";
        String teeClubs = "";
        String clubPhoto = "";
        String scoreCard = "";
        String analysis = "";

        String toLine() {
            return timestamp + "|" + enc(date) + "|" + enc(course) + "|" + enc(tee) + "|" + total + "|" + putts + "|" + bestDiff + "|" + fw + "|" + teeShots + "|" + enc(pars) + "|" + enc(p1Scores) + "|" + enc(puttArray) + "|" + enc(teeResults) + "|" + enc(teeClubs) + "|" + enc(clubPhoto) + "|" + enc(scoreCard) + "|" + enc(analysis);
        }

        static RoundRecord fromLine(String line) {
            try {
                if (TextUtils.isEmpty(line)) return null;
                String[] p = line.split("\\|", -1);
                if (p.length < 17) return null;
                RoundRecord r = new RoundRecord();
                r.timestamp = Long.parseLong(p[0]);
                r.date = dec(p[1]);
                r.course = dec(p[2]);
                r.tee = dec(p[3]);
                r.total = Integer.parseInt(p[4]);
                r.putts = Integer.parseInt(p[5]);
                r.bestDiff = Integer.parseInt(p[6]);
                r.fw = Integer.parseInt(p[7]);
                r.teeShots = Integer.parseInt(p[8]);
                r.pars = dec(p[9]);
                r.p1Scores = dec(p[10]);
                r.puttArray = dec(p[11]);
                r.teeResults = dec(p[12]);
                r.teeClubs = dec(p[13]);
                r.clubPhoto = dec(p[14]);
                r.scoreCard = dec(p[15]);
                r.analysis = dec(p[16]);
                return r;
            } catch (Exception e) {
                return null;
            }
        }

        private static String enc(String s) { return Base64.encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP); }
        private static String dec(String s) { try { return new String(Base64.decode(s, Base64.NO_WRAP), StandardCharsets.UTF_8); } catch (Exception e) { return ""; } }
    }
}
