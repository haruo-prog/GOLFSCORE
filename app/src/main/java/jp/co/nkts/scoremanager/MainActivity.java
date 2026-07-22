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
    private static final int HOLES = 18, PLAYERS = 4, MAX_SCORE = 15, FREE_LIMIT = 5;
    private static final int REQ_CSV = 301, REQ_BACKUP = 302, REQ_RESTORE = 303, REQ_PDF = 304;
    private static final String PREF = "gso_v211";
    private static final String KEY_HISTORY = "history", KEY_LANG = "lang";
    private static final int C_BG = 0xFFF7FAFC, C_CARD = 0xFFFFFFFF, C_TEXT = 0xFF0F172A, C_MUTED = 0xFF64748B, C_BORDER = 0xFFDDE5EF, C_GREEN = 0xFF166534, C_GREEN_D = 0xFF14532D, C_SOFT = 0xFFE9F8EF, C_LOCK = 0xFFFFF7ED;

    private ScrollView scroll;
    private LinearLayout root;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveRun = () -> saveDraft(false);

    private int screen = 0, currentHole = 0, activePlayers = 1, tensPlayer = -1;
    private String lang = "ja", roundDate = "", course = "", tee = "", startMemo = "", csvFrom = "", csvTo = "", selected = "";
    private boolean inRound = false, savePending = false, confirmCancel = false;
    private final int[] pars = {4,4,3,5,4,4,5,3,4,4,5,4,3,4,4,5,3,4};
    private final int[][] scores = new int[PLAYERS][HOLES];
    private final int[] putts = new int[HOLES];
    private final int[] teeResult = new int[HOLES];
    private final String[] names = {"Player 1", "Player 2", "Player 3", "Player 4"};
    private final String[] langCodes = {"ja","en","es","fr","ko","zh","tw","de"};
    private final String[] langLabels = {"JP","EN","ES","FR","KO","简","繁","DE"};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        loadDraft();
        setContentView(base());
        renderHome();
    }

    @Override protected void onPause() { flushSave(); super.onPause(); }
    @Override protected void onStop() { flushSave(); super.onStop(); }
    @Override protected void onDestroy() { flushSave(); super.onDestroy(); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_CSV) writeText(data.getData(), buildCsv(), true, t("csv_saved"), t("csv_failed"));
        if (requestCode == REQ_BACKUP) writeText(data.getData(), buildBackup(), false, t("backup_saved"), t("backup_failed"));
        if (requestCode == REQ_RESTORE) restoreBackup(data.getData());
        if (requestCode == REQ_PDF) writePdfTo(data.getData());
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

    private void renderHome() {
        screen = 0; inRound = false; saveDraft(false); root.removeAllViews();
        root.addView(hero("Golf Scorecard Offline", paid() ? "Pro / Lifetime License" : "Free Trial"));
        root.addView(licenseCard());
        LinearLayout c = card(); c.addView(section(t("round")));
        Button start = primary(t("start_round")); start.setOnClickListener(v -> { newRound(); renderRound(false); }); c.addView(start, full());
        if (hasDraft()) { Button resume = secondary(t("resume_round")); resume.setOnClickListener(v -> renderRound(false)); c.addView(resume, full()); }
        Button history = secondary(t("history")); history.setOnClickListener(v -> renderHistory()); c.addView(history, full());
        Button analysis = secondary(t("analysis")); analysis.setOnClickListener(v -> renderAnalysis()); c.addView(analysis, full());
        Button settings = secondary(t("settings")); settings.setOnClickListener(v -> renderSettings()); c.addView(settings, full());
        root.addView(c);
        root.addView(csvCard());
        root.addView(statsCard());
        nav(); top();
    }

    private View licenseCard() {
        LinearLayout c = card();
        if (paid()) {
            c.addView(section("Lifetime License"));
            c.addView(info("Thank you for supporting development.\nAll features are unlocked.", true));
        } else {
            int n = loadHistory().size();
            c.addView(section("Free Trial"));
            c.addView(info("Saved rounds: " + Math.min(n, FREE_LIMIT) + "/" + FREE_LIMIT + "\nPro unlocks unlimited history, analysis, CSV, PDF save, backup and restore.", false));
            Button up = primary("Upgrade to Lifetime License"); up.setOnClickListener(v -> toast("Test build: install the Pro APK.")); c.addView(up, full());
        }
        return c;
    }

    private View csvCard() {
        if (!paid()) return locked(t("csv_export"));
        LinearLayout c = card(); c.addView(section(t("csv_export"))); c.addView(info(t("csv_note"), false));
        EditText from = input("YYYY/MM/DD  " + t("from_date")); from.setText(csvFrom); from.addTextChangedListener(w(s -> { csvFrom = s; prefs().edit().putString("csvFrom", csvFrom).apply(); })); c.addView(from);
        EditText to = input("YYYY/MM/DD  " + t("to_date")); to.setText(csvTo); to.addTextChangedListener(w(s -> { csvTo = s; prefs().edit().putString("csvTo", csvTo).apply(); })); c.addView(to);
        Button save = primary(t("csv_save")); save.setOnClickListener(v -> createDoc(REQ_CSV, "text/csv", "GolfScore_" + fileDate(csvFrom, "from") + "_" + fileDate(csvTo, "to") + ".csv")); c.addView(save, full());
        return c;
    }

    private View statsCard() { LinearLayout c = card(); c.addView(section(t("recent"))); c.addView(info(stats(loadHistory()), true)); return c; }
    private View locked(String title) { LinearLayout c = card(); c.addView(section(title)); c.addView(info(t("locked"), false)); Button b = primary("Upgrade to Lifetime License"); b.setOnClickListener(v -> toast("Test build: install the Pro APK.")); c.addView(b, full()); return c; }

    private void renderRound(boolean keep) {
        screen = 1; inRound = true; int y = keep ? scroll.getScrollY() : 0; root.removeAllViews();
        LinearLayout head = card(); TextView h = text((currentHole + 1) + "H  PAR" + pars[currentHole], 38, C_TEXT, true); h.setGravity(Gravity.CENTER); head.addView(h); head.addView(text(roundDate + "  " + safe(course, t("course_empty")), 17, C_MUTED, false)); root.addView(head);
        root.addView(roundSettings()); root.addView(holeChooser()); root.addView(scoreInput()); root.addView(finishCard()); langFooter(); if (keep) restore(y); else top();
    }

    private View roundSettings() {
        LinearLayout c = card(); c.addView(section(t("round_settings")));
        EditText d = input("YYYY/MM/DD  " + t("round_date")); d.setText(roundDate); d.addTextChangedListener(w(s -> { roundDate = s; reqSave(); })); c.addView(d);
        EditText co = input(t("course")); co.setText(course); co.addTextChangedListener(w(s -> { course = s; reqSave(); })); c.addView(co);
        EditText te = input(t("tee")); te.setText(tee); te.addTextChangedListener(w(s -> { tee = s; reqSave(); })); c.addView(te);
        EditText st = input(t("start_memo")); st.setText(startMemo); st.addTextChangedListener(w(s -> { startMemo = s; reqSave(); })); c.addView(st);
        c.addView(text(t("players"), 18, C_TEXT, true));
        LinearLayout r = row(); for (int i=1;i<=4;i++){ final int n=i; Button b=choice(String.valueOf(i), activePlayers==i); b.setOnClickListener(v->{ activePlayers=n; renderRound(true); reqSave(); }); r.addView(b, weight()); } c.addView(r);
        for(int p=0;p<activePlayers;p++){ final int idx=p; EditText nm=input(p==0?"Player 1":t("player")+" "+(p+1)); nm.setText(names[p]); nm.addTextChangedListener(w(s->{ names[idx]=s; reqSave(); })); c.addView(nm); }
        return c;
    }

    private View holeChooser() {
        LinearLayout c = card(); c.addView(section(t("progress"))); c.addView(text("Player1 " + entered(0) + "/18  /  " + t("missing") + " " + missing(), 19, C_TEXT, true));
        for (int r=0;r<3;r++){ LinearLayout line=row(); for(int col=0;col<6;col++){ int h=r*6+col; Button b=choice(String.valueOf(h+1), h==currentHole); b.setTextSize(20); b.setMinHeight(dp(56)); final int target=h; b.setOnClickListener(v->{ currentHole=target; tensPlayer=-1; renderRound(false); reqSave(); }); line.addView(b, weight()); } c.addView(line); }
        return c;
    }

    private View scoreInput() {
        LinearLayout c = card(); c.addView(section(t("score_input")));
        LinearLayout par = row(); for(int x=3;x<=6;x++){ final int v=x; Button b=choice("PAR " + x, pars[currentHole]==x); b.setTextSize(20); b.setOnClickListener(vw->{ pars[currentHole]=v; renderRound(true); reqSave(); }); par.addView(b, weight()); } c.addView(par);
        for(int p=0;p<activePlayers;p++) c.addView(playerScore(p));
        LinearLayout nav=row(); Button prev=secondary(t("prev")); prev.setEnabled(currentHole>0); prev.setOnClickListener(v->{ currentHole--; renderRound(false); reqSave(); }); Button next=primary(t("next")); next.setEnabled(currentHole<17); next.setOnClickListener(v->{ currentHole++; renderRound(false); reqSave(); }); nav.addView(prev, weight()); nav.addView(next, weight()); c.addView(nav);
        return c;
    }

    private View playerScore(int p) {
        LinearLayout c = lite(); c.addView(text(safe(names[p], "Player "+(p+1)) + "  SCORE " + (scores[p][currentHole]==0?"-":scores[p][currentHole]) + (tensPlayer==p ? "  10+" : ""), 28, C_TEXT, true));
        String[][] keys={{"1","2","3"},{"4","5","6"},{"7","8","9"},{"1+","0","CLR"}};
        for(String[] rowKeys:keys){ LinearLayout r=row(); for(String k:rowKeys){ Button b=choice(k,false); b.setTextSize(28); b.setMinHeight(dp(72)); b.setOnClickListener(v->scoreKey(p,k)); r.addView(b, weight()); } c.addView(r); }
        if(p==0){ c.addView(text("PAT",16,C_MUTED,true)); LinearLayout pat=row(); for(int i=1;i<=4;i++){ final int v=i; Button b=choice(i==4?"4+":String.valueOf(i), putts[currentHole]==i); b.setTextSize(24); b.setOnClickListener(x->{ putts[currentHole]=v; renderRound(true); reqSave(); }); pat.addView(b, weight()); } c.addView(pat);
            c.addView(text(t("tee_result"),16,C_MUTED,true)); LinearLayout tr=row(); String[] labs={"-","FW","L","R","LOB","ROB"}; for(int i=0;i<labs.length;i++){ final int v=i; Button b=choice(labs[i], teeResult[currentHole]==i); b.setOnClickListener(x->{ teeResult[currentHole]=v; renderRound(true); reqSave(); }); tr.addView(b, weight()); } c.addView(tr); }
        return c;
    }

    private void scoreKey(int p, String key) {
        if("CLR".equals(key)){ scores[p][currentHole]=0; tensPlayer=-1; renderRound(true); reqSave(); return; }
        if("1+".equals(key)){ tensPlayer=p; renderRound(true); return; }
        int d = num(key, -1); if(d<0) return;
        if(tensPlayer==p){ if(d<=5){ scores[p][currentHole]=10+d; tensPlayer=-1; } else toast(t("ten_error")); }
        else scores[p][currentHole]=d==0?0:d;
        renderRound(true); reqSave();
    }

    private View finishCard() {
        LinearLayout c = card(); c.addView(section(t("finish")));
        Button save = primary(t("save_analysis")); save.setOnClickListener(v -> finishRound()); c.addView(save, full());
        Button cancel = secondary(t("cancel")); cancel.setOnClickListener(v -> { confirmCancel = true; renderRound(true); }); c.addView(cancel, full());
        if(confirmCancel){ c.addView(info(t("cancel_confirm"), false)); LinearLayout r=row(); Button back=primary(t("back_input")); back.setOnClickListener(v->{ confirmCancel=false; renderRound(true); }); Button home=secondary(t("back_home")); home.setOnClickListener(v->{ inRound=false; saveDraft(false); renderHome(); }); r.addView(back, weight()); r.addView(home, weight()); c.addView(r); }
        return c;
    }

    private void finishRound() {
        flushSave(); if(TextUtils.isEmpty(roundDate)) roundDate=today();
        if(missing()>0){ toast(t("missing")+": "+missing()); return; }
        ArrayList<Record> list=loadHistory(); if(!paid() && list.size()>=FREE_LIMIT){ toast("Free limit reached. Install Pro APK."); renderHome(); return; }
        Record r=new Record(); r.date=roundDate; r.course=safe(course,t("course_empty")); r.tee=tee; r.memo=startMemo; r.total=total(0); r.putts=sum(putts); r.fw=count(teeResult,1); r.teeShots=countTeeShots(); r.ob=count(teeResult,4)+count(teeResult,5); r.pars=ser(pars); r.scores=ser(scores[0]); r.puttData=ser(putts); r.teeData=ser(teeResult); r.card=scorecard(); r.analysis=analysisText(r);
        list.add(0,r); saveHistory(list); inRound=false; saveDraft(false); selected=r.card + (paid()?"\n\n"+r.analysis:"\n\nAnalysis is a Pro feature."); renderHistory();
    }

    private void renderHistory() {
        screen=2; inRound=false; saveDraft(false); root.removeAllViews(); root.addView(hero(t("history"), t("history_sub")));
        LinearLayout c=card(); ArrayList<Record> list=loadHistory(); if(list.isEmpty()) c.addView(info(t("no_history"), false));
        for(Record r:list){ LinearLayout item=lite(); item.addView(text(r.date+"  "+r.course+"  SCORE "+r.total+"  PAT "+r.putts,18,C_TEXT,true)); LinearLayout buttons=row(); Button detail=secondary(t("detail")); detail.setOnClickListener(v->{ selected=r.card+(paid()?"\n\n"+r.analysis:"\n\nAnalysis is a Pro feature."); renderHistory(); }); Button pdf=primary("PDF"); pdf.setOnClickListener(v->{ selected=r.card+(paid()?"\n\n"+r.analysis:""); createDoc(REQ_PDF,"application/pdf","GolfScore_"+fileDate(r.date,"date")+".pdf"); }); buttons.addView(detail,weight()); buttons.addView(pdf,weight()); item.addView(buttons); c.addView(item); }
        if(!TextUtils.isEmpty(selected)) c.addView(info(selected,false)); root.addView(c); nav(); top();
    }

    private void renderAnalysis() {
        screen=3; root.removeAllViews(); root.addView(hero(t("analysis"), paid()?t("analysis_sub"):"Pro Feature")); if(!paid()){ root.addView(locked(t("analysis"))); nav(); top(); return; }
        LinearLayout c=card(); c.addView(info(stats(loadHistory())+"\n\n"+trend(loadHistory()), true)); root.addView(c); nav(); top();
    }

    private void renderSettings() {
        screen=4; root.removeAllViews(); root.addView(hero(t("settings"), paid()?"Lifetime License":"Free Trial"));
        LinearLayout support=card(); support.addView(section("Support")); Button review=secondary("Review this app"); review.setOnClickListener(v->openMarket()); support.addView(review,full()); Button contact=secondary("Contact"); contact.setOnClickListener(v->mail("Golf Scorecard Offline Contact")); support.addView(contact,full()); Button idea=secondary("Suggest a feature"); idea.setOnClickListener(v->mail("Golf Scorecard Offline Feature Idea")); support.addView(idea,full()); root.addView(support);
        LinearLayout data=card(); data.addView(section(t("backup"))); if(!paid()) data.addView(info("Backup and restore are Pro features.", false)); else { Button b=secondary(t("backup_save")); b.setOnClickListener(v->createDoc(REQ_BACKUP,"text/plain","GolfScore_Backup_"+todayFile()+".txt")); data.addView(b,full()); Button r=primary(t("restore_backup")); r.setOnClickListener(v->openDoc()); data.addView(r,full()); } root.addView(data); nav(); top();
    }

    private String scorecard(){ StringBuilder b=new StringBuilder(); b.append("GOLF SCORECARD\nDate: ").append(roundDate).append("\nCourse: ").append(safe(course,t("course_empty"))).append("\nTee: ").append(tee).append("\nMemo: ").append(startMemo).append("\n\n"); b.append("HOLE,1,2,3,4,5,6,7,8,9,OUT,10,11,12,13,14,15,16,17,18,IN,TOTAL\n"); b.append("PAR,").append(rowCsv(pars,false)).append("\n"); for(int p=0;p<activePlayers;p++) b.append(safe(names[p],"Player "+(p+1))).append(",").append(rowCsv(scores[p],true)).append("\n"); b.append("\nPAT,").append(sum(putts)).append("\nFW,").append(count(teeResult,1)).append("/").append(countTeeShots()).append("\nOB,").append(count(teeResult,4)+count(teeResult,5)).append("\n\nScored with Golf Scorecard Offline"); return b.toString(); }
    private String analysisText(Record r){ return t("today_analysis")+"\nScore: "+r.total+"\nPAT: "+r.putts+"\nFW: "+r.fw+"/"+r.teeShots+"\nOB: "+r.ob+"\n"+(r.ob>=2?t("ob_advice"):"Good record. Keep tracking rounds."); }
    private String buildCsv(){ StringBuilder b=new StringBuilder("Date,Course,Tee,Memo,Total,Putts,FW,TeeShots,OB"); for(int i=1;i<=18;i++)b.append(",H").append(i); for(int i=1;i<=18;i++)b.append(",Par").append(i); b.append("\n"); int from=dateNum(csvFrom,0), to=dateNum(csvTo,99999999); for(Record r:loadHistory()){ int d=dateNum(r.date,0); if(d<from||d>to)continue; int[] sc=deserInt(r.scores,18,0), pa=deserInt(r.pars,18,0); b.append(csv(r.date)).append(',').append(csv(r.course)).append(',').append(csv(r.tee)).append(',').append(csv(r.memo)).append(',').append(r.total).append(',').append(r.putts).append(',').append(r.fw).append(',').append(r.teeShots).append(',').append(r.ob); for(int v:sc)b.append(',').append(v); for(int v:pa)b.append(',').append(v); b.append("\n"); } return b.toString(); }
    private String buildBackup(){ StringBuilder b=new StringBuilder("GSO_BACKUP_V211\n"); b.append(enc(prefs().getString(KEY_HISTORY,""))).append("\n"); return b.toString(); }
    private void restoreBackup(Uri uri){ try{ String raw=read(uri); if(!raw.startsWith("GSO_BACKUP_")) throw new Exception(); String[] lines=raw.split("\n",2); prefs().edit().putString(KEY_HISTORY, lines.length>1?dec(lines[1].trim()):"").apply(); toast(t("restore_done")); renderHome(); }catch(Exception e){ toast(t("restore_failed")); } }

    private void writePdfTo(Uri uri){ try{ OutputStream out=getContentResolver().openOutputStream(uri); if(out==null)throw new Exception(); PdfDocument pdf=new PdfDocument(); Paint p=new Paint(); p.setAntiAlias(true); p.setTextSize(14); Paint title=new Paint(); title.setAntiAlias(true); title.setTypeface(Typeface.DEFAULT_BOLD); title.setTextSize(22); int w=842,h=595,m=28,y=30; PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(w,h,1).create()); Canvas c=page.getCanvas(); c.drawText("Golf Scorecard Offline",m,y,title); y+=32; for(String line:selected.split("\n")){ if(y>h-m){ pdf.finishPage(page); page=pdf.startPage(new PdfDocument.PageInfo.Builder(w,h,2).create()); c=page.getCanvas(); y=30; } c.drawText(line,m,y,p); y+=18; } pdf.finishPage(page); pdf.writeTo(out); pdf.close(); out.close(); toast(t("pdf_saved")); }catch(Exception e){ toast(t("pdf_failed")); } }
    private void writeText(Uri uri,String text,boolean bom,String ok,String ng){ try{ OutputStream out=getContentResolver().openOutputStream(uri); if(out==null)throw new Exception(); if(bom)out.write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF}); out.write(text.getBytes(StandardCharsets.UTF_8)); out.close(); toast(ok); }catch(Exception e){ toast(ng); } }
    private String read(Uri uri)throws Exception{ InputStream in=getContentResolver().openInputStream(uri); if(in==null)throw new Exception(); ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=in.read(buf))>0)b.write(buf,0,n); in.close(); return new String(b.toByteArray(),StandardCharsets.UTF_8); }
    private void createDoc(int req,String type,String name){ Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType(type); i.putExtra(Intent.EXTRA_TITLE,name); startActivityForResult(i,req); }
    private void openDoc(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/*"); startActivityForResult(i,REQ_RESTORE); }
    private void openMarket(){ try{ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id="+getPackageName()))); }catch(Exception e){ toast("Store not available in this test build."); } }
    private void mail(String subject){ Intent i=new Intent(Intent.ACTION_SENDTO); i.setData(Uri.parse("mailto:support@nk-ts.co.jp")); i.putExtra(Intent.EXTRA_SUBJECT,subject); try{ startActivity(i); }catch(Exception e){ toast("Mail app not available."); } }

    private void newRound(){ inRound=true; roundDate=today(); course=""; tee=""; startMemo=""; currentHole=0; activePlayers=1; for(int p=0;p<PLAYERS;p++){ names[p]="Player "+(p+1); for(int h=0;h<HOLES;h++) scores[p][h]=0; } int[] def={4,4,3,5,4,4,5,3,4,4,5,4,3,4,4,5,3,4}; System.arraycopy(def,0,pars,0,18); for(int h=0;h<18;h++){ putts[h]=0; teeResult[h]=0; } saveDraft(false); }
    private void loadDraft(){ SharedPreferences p=prefs(); lang=p.getString(KEY_LANG,"ja"); csvFrom=p.getString("csvFrom",""); csvTo=p.getString("csvTo",""); inRound=p.getBoolean("inRound",false); roundDate=p.getString("roundDate",today()); course=p.getString("course",""); tee=p.getString("tee",""); startMemo=p.getString("memo",""); currentHole=p.getInt("hole",0); activePlayers=p.getInt("players",1); restoreInt(p.getString("pars",""),pars,null,0,9); restoreInt(p.getString("putts",""),putts,null,0,9); restoreInt(p.getString("teeResult",""),teeResult,null,0,9); restoreStr(p.getString("names",""),names); for(int i=0;i<PLAYERS;i++) restoreInt(p.getString("scores"+i,""),scores[i],null,0,MAX_SCORE); }
    private void saveDraft(boolean show){ savePending=false; SharedPreferences.Editor e=prefs().edit(); e.putString(KEY_LANG,lang).putBoolean("inRound",inRound).putString("roundDate",roundDate).putString("course",course).putString("tee",tee).putString("memo",startMemo).putInt("hole",currentHole).putInt("players",activePlayers).putString("pars",ser(pars)).putString("putts",ser(putts)).putString("teeResult",ser(teeResult)).putString("names",ser(names)); for(int i=0;i<PLAYERS;i++)e.putString("scores"+i,ser(scores[i])); e.apply(); if(show)toast(t("saved")); }
    private void reqSave(){ savePending=true; handler.removeCallbacks(saveRun); handler.postDelayed(saveRun,500); }
    private void flushSave(){ handler.removeCallbacks(saveRun); if(savePending)saveDraft(false); }

    private ArrayList<Record> loadHistory(){ ArrayList<Record> list=new ArrayList<>(); String raw=prefs().getString(KEY_HISTORY,""); if(TextUtils.isEmpty(raw))return list; for(String line:raw.split("\n",-1)){ Record r=Record.from(line); if(r!=null)list.add(r); } return list; }
    private void saveHistory(ArrayList<Record> list){ ArrayList<String> out=new ArrayList<>(); int max=paid()?400:FREE_LIMIT; for(int i=0;i<list.size()&&i<max;i++)out.add(list.get(i).line()); prefs().edit().putString(KEY_HISTORY,TextUtils.join("\n",out)).apply(); }

    private String stats(ArrayList<Record> list){ if(list.isEmpty())return t("no_data"); int total=0,put=0,fw=0,ts=0; for(Record r:list){ total+=r.total; put+=r.putts; fw+=r.fw; ts+=r.teeShots; } return "Rounds: "+list.size()+"\nAVG Score: "+one(total*1.0/list.size())+"\nAVG PAT: "+one(put*1.0/list.size())+"\nFW: "+(ts==0?"-":one(fw*100.0/ts)+"%"); }
    private String trend(ArrayList<Record> list){ if(list.isEmpty())return t("no_data"); Record r=list.get(0); return "Latest Round\n"+r.date+"  "+r.course+"\nScore "+r.total+" / PAT "+r.putts+" / OB "+r.ob; }
    private int entered(int p){ int n=0; for(int v:scores[p]) if(v>0)n++; return n; }
    private int missing(){ int m=0; for(int p=0;p<activePlayers;p++)for(int h=0;h<18;h++)if(scores[p][h]==0)m++; return m; }
    private int total(int p){ int s=0; for(int v:scores[p])s+=v; return s; }
    private int sum(int[] a){ int s=0; for(int v:a)s+=v; return s; }
    private int count(int[] a,int x){ int n=0; for(int v:a)if(v==x)n++; return n; }
    private int countTeeShots(){ int n=0; for(int v:teeResult)if(v>0)n++; return n; }
    private boolean hasDraft(){ if(!inRound)return false; for(int p=0;p<PLAYERS;p++)for(int h=0;h<18;h++)if(scores[p][h]>0)return true; return !TextUtils.isEmpty(course)||!TextUtils.isEmpty(tee); }
    private String rowCsv(int[] values,boolean blankZero){ StringBuilder b=new StringBuilder(); int out=0,in=0,total=0; for(int i=0;i<18;i++){ if(i>0)b.append(','); int v=values[i]; b.append(blankZero&&v==0?"":String.valueOf(v)); if(i<9)out+=v; else in+=v; total+=v; if(i==8)b.append(',').append(out); } return b.append(',').append(in).append(',').append(total).toString(); }

    private LinearLayout card(){ LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(14),dp(14),dp(14),dp(14)); v.setBackground(bg(C_CARD,C_BORDER,18)); LinearLayout.LayoutParams p=full(); p.setMargins(0,dp(6),0,dp(6)); v.setLayoutParams(p); return v; }
    private LinearLayout lite(){ LinearLayout v=card(); v.setBackground(bg(0xFFF9FBFD,C_BORDER,16)); return v; }
    private View hero(String title,String sub){ LinearLayout c=card(); TextView t=text(title,26,C_TEXT,true); t.setGravity(Gravity.CENTER); c.addView(t); TextView s=text(sub,16,C_MUTED,false); s.setGravity(Gravity.CENTER); c.addView(s); return c; }
    private TextView section(String s){ TextView v=text(s,20,C_TEXT,true); v.setPadding(0,0,0,dp(8)); return v; }
    private TextView info(String s,boolean strong){ TextView v=text(s,strong?18:16,C_TEXT,strong); v.setPadding(dp(12),dp(12),dp(12),dp(12)); v.setBackground(bg(strong?C_SOFT:C_LOCK,C_BORDER,14)); return v; }
    private TextView text(String s,int sz,int col,boolean bold){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sz); v.setTextColor(col); if(bold)v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private EditText input(String hint){ EditText e=new EditText(this); e.setHint(hint); e.setTextSize(18); e.setSingleLine(true); e.setTextColor(C_TEXT); e.setPadding(dp(8),dp(8),dp(8),dp(8)); return e; }
    private Button primary(String s){ Button b=btn(s); b.setBackground(bg(C_GREEN,C_GREEN_D,14)); b.setTextColor(0xFFFFFFFF); return b; }
    private Button secondary(String s){ Button b=btn(s); b.setBackground(bg(C_CARD,C_BORDER,14)); b.setTextColor(C_TEXT); return b; }
    private Button choice(String s,boolean on){ Button b=btn(s); b.setBackground(bg(on?C_GREEN:C_CARD,on?C_GREEN_D:C_BORDER,14)); b.setTextColor(on?0xFFFFFFFF:C_TEXT); return b; }
    private Button btn(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(17); b.setMinHeight(dp(54)); return b; }
    private LinearLayout row(){ LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r; }
    private LinearLayout.LayoutParams full(){ return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams weight(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1); p.setMargins(dp(2),dp(2),dp(2),dp(2)); return p; }
    private GradientDrawable bg(int fill,int stroke,int rad){ GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(rad)); g.setStroke(dp(1),stroke); return g; }
    private void nav(){ LinearLayout n=row(); Button h=secondary(t("home")); h.setOnClickListener(v->renderHome()); Button hi=secondary(t("history")); hi.setOnClickListener(v->renderHistory()); Button a=secondary(t("analysis")); a.setOnClickListener(v->renderAnalysis()); Button s=secondary(t("settings")); s.setOnClickListener(v->renderSettings()); n.addView(h,weight()); n.addView(hi,weight()); n.addView(a,weight()); n.addView(s,weight()); root.addView(n); langFooter(); }
    private void langFooter(){ LinearLayout c=card(); LinearLayout r=row(); for(int i=0;i<langCodes.length;i++){ final String code=langCodes[i]; Button b=choice(langLabels[i], code.equals(lang)); b.setTextSize(10); b.setMinHeight(dp(42)); b.setOnClickListener(v->{ lang=code; prefs().edit().putString(KEY_LANG,lang).apply(); if(screen==1)renderRound(true); else if(screen==2)renderHistory(); else if(screen==3)renderAnalysis(); else if(screen==4)renderSettings(); else renderHome(); }); r.addView(b,weight()); } c.addView(r); root.addView(c); }

    private TextWatcher w(Sink s){ return new TextWatcher(){ public void beforeTextChanged(CharSequence a,int b,int c,int d){} public void onTextChanged(CharSequence a,int b,int c,int d){ s.set(a==null?"":a.toString()); } public void afterTextChanged(Editable e){} }; }
    private interface Sink{ void set(String s); }
    private String t(String k){ if("ja".equals(lang))return ja(k); return en(k); }
    private String en(String k){ switch(k){case"round":return"Round";case"start_round":return"Start Round";case"resume_round":return"Resume Round";case"history":return"History";case"analysis":return"Analysis";case"settings":return"Settings";case"home":return"Home";case"recent":return"Recent Stats";case"csv_export":return"CSV Export";case"csv_note":return"Export score history by round date.";case"from_date":return"Start date";case"to_date":return"End date";case"csv_save":return"Save CSV";case"csv_saved":return"CSV saved";case"csv_failed":return"CSV failed";case"round_settings":return"Round Settings";case"round_date":return"Round date";case"course":return"Course";case"tee":return"Tee";case"start_memo":return"Start time / memo";case"players":return"Players";case"player":return"Player";case"progress":return"Progress";case"missing":return"Missing";case"score_input":return"Score Input";case"prev":return"Prev";case"next":return"Next";case"finish":return"Finish";case"save_analysis":return"Save Round";case"cancel":return"Cancel / End";case"cancel_confirm":return"End score entry?";case"back_input":return"Back to Input";case"back_home":return"Back Home";case"history_sub":return"Scorecards by round date";case"analysis_sub":return"Trend analysis";case"no_history":return"No history yet.";case"detail":return"Detail";case"no_data":return"No data";case"backup":return"Backup / Restore";case"backup_save":return"Save Backup";case"restore_backup":return"Restore Backup";case"backup_saved":return"Backup saved";case"backup_failed":return"Backup failed";case"restore_done":return"Restored";case"restore_failed":return"Restore failed";case"pdf_saved":return"PDF saved";case"pdf_failed":return"PDF failed";case"course_empty":return"No course";case"tee_result":return"Tee result";case"ten_error":return"10+ supports 10-15.";case"today_analysis":return"Round Analysis";case"ob_advice":return"Multiple OBs. Use safer club selection on tight holes.";case"locked":return"This is a Pro feature. Install the Pro APK to test it.";case"saved":return"Saved";} return k; }
    private String ja(String k){ switch(k){case"round":return"ラウンド";case"start_round":return"ラウンド開始";case"resume_round":return"入力中に戻る";case"history":return"履歴";case"analysis":return"分析";case"settings":return"設定";case"home":return"ホーム";case"recent":return"最近の成績";case"csv_export":return"CSVエクスポート";case"csv_note":return"ラウンド日付を基準に履歴CSVを出力します。";case"from_date":return"開始日";case"to_date":return"終了日";case"csv_save":return"CSVを保存";case"csv_saved":return"CSV保存しました";case"csv_failed":return"CSV保存失敗";case"round_settings":return"ラウンド設定";case"round_date":return"ラウンド日付";case"course":return"コース名";case"tee":return"ティー";case"start_memo":return"開始時間 / メモ";case"players":return"人数";case"player":return"同伴者";case"progress":return"進捗";case"missing":return"未入力";case"score_input":return"スコア入力";case"prev":return"前へ";case"next":return"次へ";case"finish":return"終了";case"save_analysis":return"ラウンド保存";case"cancel":return"キャンセル終了";case"cancel_confirm":return"スコア登録を終了しますか？";case"back_input":return"入力へ戻る";case"back_home":return"ホームへ戻る";case"history_sub":return"ラウンド日付別スコアカード";case"analysis_sub":return"傾向分析";case"no_history":return"履歴はまだありません。";case"detail":return"詳細";case"no_data":return"データなし";case"backup":return"バックアップ / 復元";case"backup_save":return"バックアップ保存";case"restore_backup":return"バックアップから復元";case"backup_saved":return"バックアップ保存しました";case"backup_failed":return"バックアップ失敗";case"restore_done":return"復元しました";case"restore_failed":return"復元失敗";case"pdf_saved":return"PDF保存しました";case"pdf_failed":return"PDF保存失敗";case"course_empty":return"未入力コース";case"tee_result":return"ティー結果";case"ten_error":return"10+は10〜15のみです。";case"today_analysis":return"ラウンド分析";case"ob_advice":return"OBが複数あります。狭いホールは安全な番手選択が有効です。";case"locked":return"Pro機能です。有料版APKでテストできます。";case"saved":return"保存しました";} return en(k); }

    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    private void top(){ scroll.post(()->scroll.fullScroll(View.FOCUS_UP)); }
    private void restore(int y){ scroll.post(()->scroll.scrollTo(0,y)); }
    private String today(){ return new SimpleDateFormat("yyyy/MM/dd",Locale.US).format(new Date()); }
    private String todayFile(){ return new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date()); }
    private String fileDate(String s,String fb){ int d=dateNum(s,-1); return d<0?fb:String.valueOf(d); }
    private int dateNum(String s,int fb){ try{ String x=s==null?"":s.replace("/","").replace("-","").replace(".","").trim(); if(x.length()>=8)return Integer.parseInt(x.substring(0,8)); }catch(Exception e){} return fb; }
    private int num(String s,int fb){ try{return Integer.parseInt(s);}catch(Exception e){return fb;} }
    private String one(double d){ return String.format(Locale.US,"%.1f",d); }
    private String safe(String s,String fb){ return TextUtils.isEmpty(s)?fb:s; }
    private String csv(String s){ return "\""+(s==null?"":s.replace("\"","\"\"")).replace("\n"," ")+"\""; }
    private String enc(String s){ return Base64.encodeToString((s==null?"":s).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP); }
    private String dec(String s){ try{return new String(Base64.decode(s,Base64.NO_WRAP),StandardCharsets.UTF_8);}catch(Exception e){return"";} }
    private String ser(int[] a){ ArrayList<String> l=new ArrayList<>(); for(int v:a)l.add(String.valueOf(v)); return TextUtils.join(",",l); }
    private String ser(String[] a){ ArrayList<String> l=new ArrayList<>(); for(String v:a)l.add(enc(v)); return TextUtils.join(",",l); }
    private int[] deserInt(String raw,int len,int fb){ int[] a=new int[len]; for(int i=0;i<len;i++)a[i]=fb; if(TextUtils.isEmpty(raw))return a; String[] p=raw.split(",",-1); for(int i=0;i<len&&i<p.length;i++)a[i]=num(p[i],fb); return a; }
    private void restoreInt(String raw,int[] target,int[] fb,int min,int max){ if(fb!=null)System.arraycopy(fb,0,target,0,Math.min(fb.length,target.length)); if(TextUtils.isEmpty(raw))return; String[] p=raw.split(",",-1); for(int i=0;i<target.length&&i<p.length;i++){ int v=num(p[i],target[i]); target[i]=Math.max(min,Math.min(max,v)); } }
    private void restoreStr(String raw,String[] target){ if(TextUtils.isEmpty(raw))return; String[] p=raw.split(",",-1); for(int i=0;i<target.length&&i<p.length;i++)target[i]=dec(p[i]); }

    private static class Record { String date="",course="",tee="",memo="",pars="",scores="",puttData="",teeData="",card="",analysis=""; int total,putts,fw,teeShots,ob; String line(){ return enc(date)+"|"+enc(course)+"|"+enc(tee)+"|"+enc(memo)+"|"+total+"|"+putts+"|"+fw+"|"+teeShots+"|"+ob+"|"+enc(pars)+"|"+enc(scores)+"|"+enc(puttData)+"|"+enc(teeData)+"|"+enc(card)+"|"+enc(analysis); } static Record from(String line){ try{ if(TextUtils.isEmpty(line))return null; String[] p=line.split("\\|",-1); if(p.length<15)return null; Record r=new Record(); r.date=dec(p[0]); r.course=dec(p[1]); r.tee=dec(p[2]); r.memo=dec(p[3]); r.total=Integer.parseInt(p[4]); r.putts=Integer.parseInt(p[5]); r.fw=Integer.parseInt(p[6]); r.teeShots=Integer.parseInt(p[7]); r.ob=Integer.parseInt(p[8]); r.pars=dec(p[9]); r.scores=dec(p[10]); r.puttData=dec(p[11]); r.teeData=dec(p[12]); r.card=dec(p[13]); r.analysis=dec(p[14]); return r; }catch(Exception e){return null;} } private static String enc(String s){ return Base64.encodeToString((s==null?"":s).getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP); } private static String dec(String s){ try{return new String(Base64.decode(s,Base64.NO_WRAP),StandardCharsets.UTF_8);}catch(Exception e){return"";} } }
}
