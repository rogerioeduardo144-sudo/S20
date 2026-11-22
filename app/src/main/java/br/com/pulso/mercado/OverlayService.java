package br.com.pulso.mercado;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Janela deslocável sobre aplicativos, com simulação apenas; nunca executa ordens reais. */
public final class OverlayService extends Service {
 private final String[] symbols={"BTCUSDT","ETHUSDT","SOLUSDT","BNBUSDT"};
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final AtomicBoolean pending=new AtomicBoolean(false);
 private WindowManager windows;
 private WindowManager.LayoutParams params;
 private LinearLayout panel;
 private TextView title,details,account,mode;
 private android.content.SharedPreferences prefs,uiPrefs;
 private MarketClient.Quote quote;
 private double brlRate=Double.NaN;
 private int index=0,revision=0;
 private boolean five=false,stopped=false;
 private final Runnable poll=new Runnable(){
  @Override public void run(){
   if(stopped)return;
   refresh();
   ui.postDelayed(this,12000L);
  }
 };
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView text(String str,int size,int color){
  TextView t=new TextView(this);
  t.setText(str);t.setTextSize(size);t.setTextColor(color);
  t.setGravity(Gravity.CENTER_VERTICAL);
  return t;
 }
 private GradientDrawable bg(int color,int border,int radius){
  GradientDrawable d=new GradientDrawable();
  d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),border);
  return d;
 }
 private TextView touchButton(String str,int color){
  TextView v=text(str,12,color);
  v.setGravity(Gravity.CENTER);
  v.setPadding(dp(5),dp(7),dp(5),dp(7));
  return v;
 }
 private String asset(){return symbols[index];}
 private String interval(){return five?"5m":"1m";}
 private void resetQuote(){
  revision++;quote=null;brlRate=Double.NaN;pending.set(false);
  details.setText("Consultando mercado...");
  updateAccount();
  ui.removeCallbacks(poll);
  ui.post(poll);
 }
 @Override public void onCreate(){
  super.onCreate();
  prefs=getSharedPreferences("paper-v1",MODE_PRIVATE);
  uiPrefs=getSharedPreferences("overlay-ui",MODE_PRIVATE);
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  nm.createNotificationChannel(new NotificationChannel("pulso","PULSO em execução",NotificationManager.IMPORTANCE_LOW));
  Intent open=new Intent(this,MainActivity.class);
  PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  Notification n=new Notification.Builder(this,"pulso")
   .setSmallIcon(android.R.drawable.ic_menu_info_details)
   .setContentTitle("PULSO · Monitor fictício")
   .setContentText("Janela flutuante ativa · nenhuma ordem real")
   .setContentIntent(pi).setOngoing(true).build();
  startForeground(240,n);
  try {draw();ui.post(poll);}
  catch(Exception e){stopSelf();}
 }
 private void draw(){
  if(!Settings.canDrawOverlays(this))throw new IllegalStateException("Permissão de janela ausente");
  windows=(WindowManager)getSystemService(WINDOW_SERVICE);
  panel=new LinearLayout(this);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(dp(9),dp(8),dp(9),dp(8));
  panel.setBackground(bg(Color.rgb(10,28,48),Color.rgb(65,134,148),16));

  LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);
  head.setGravity(Gravity.CENTER_VERTICAL);
  TextView grip=touchButton("⇕  ARRASTE",Color.rgb(132,241,202));
  grip.setBackground(bg(Color.rgb(25,59,76),Color.rgb(60,128,142),9));
  head.addView(grip,new LinearLayout.LayoutParams(dp(113),dp(39)));

  title=touchButton("BTC  ▾",Color.WHITE);title.setTextSize(15);
  head.addView(title,new LinearLayout.LayoutParams(0,dp(39),1));
  TextView close=touchButton("×",Color.WHITE);close.setTextSize(28);
  head.addView(close,new LinearLayout.LayoutParams(dp(34),dp(39)));
  panel.addView(head);
  close.setOnClickListener(v->stopSelf());
  title.setOnClickListener(v->{
   index=(index+1)%symbols.length;
   title.setText(symbols[index].replace("USDT","")+"  ▾");
   resetQuote();
  });

  details=text("Conectando ao mercado...",13,Color.WHITE);
  details.setPadding(dp(5),dp(6),0,dp(6));
  panel.addView(details);
  LinearLayout controls=new LinearLayout(this);
  controls.setOrientation(LinearLayout.VERTICAL);
  controls.setPadding(0,dp(2),0,0);
  mode=touchButton("RÁPIDO · 1 MIN",Color.rgb(242,208,108));
  mode.setBackground(bg(Color.rgb(36,47,65),Color.rgb(70,89,107),9));
  controls.addView(mode,new LinearLayout.LayoutParams(-1,dp(34)));
  LinearLayout actions=new LinearLayout(this);
  actions.setOrientation(LinearLayout.HORIZONTAL);
  TextView buy=touchButton("COMPRA TESTE",Color.rgb(132,241,202));
  buy.setBackground(bg(Color.rgb(21,66,61),Color.rgb(75,135,118),9));
  TextView sell=touchButton("VENDA TESTE",Color.rgb(252,185,176));
  sell.setBackground(bg(Color.rgb(75,38,50),Color.rgb(142,86,96),9));
  LinearLayout.LayoutParams buyLp=new LinearLayout.LayoutParams(0,dp(38),1);
  LinearLayout.LayoutParams sellLp=new LinearLayout.LayoutParams(0,dp(38),1);
  buyLp.setMargins(0,dp(4),dp(3),0);
  sellLp.setMargins(dp(3),dp(4),0,0);
  actions.addView(buy,buyLp);
  actions.addView(sell,sellLp);
  controls.addView(actions);
  panel.addView(controls);
  mode.setOnClickListener(v->{
   five=!five;
   mode.setText(five?"CONFIRMAR · 5 MIN":"RÁPIDO · 1 MIN");
   resetQuote();
  });
  buy.setOnClickListener(v->simulate(true));
  sell.setOnClickListener(v->simulate(false));
  account=text("Carteira fictícia R$ 100,00",11,Color.rgb(182,200,210));
  account.setPadding(dp(5),dp(6),0,0);
  panel.addView(account);
  TextView disclaimer=text("SIMULAÇÃO • NÃO ENVIA ORDENS À BINANCE",9,Color.rgb(175,188,204));
  disclaimer.setPadding(dp(5),dp(5),0,0);
  panel.addView(disclaimer);

  params=new WindowManager.LayoutParams(dp(270),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    PixelFormat.TRANSLUCENT);
  params.gravity=Gravity.TOP|Gravity.START;
  params.x=uiPrefs.getInt("x",dp(10));params.y=uiPrefs.getInt("y",dp(130));
  grip.setOnTouchListener(new View.OnTouchListener(){
   int originalX,originalY;float downX,downY;
   @Override public boolean onTouch(View view,MotionEvent ev){
    switch(ev.getActionMasked()){
     case MotionEvent.ACTION_DOWN:
      originalX=params.x;originalY=params.y;
      downX=ev.getRawX();downY=ev.getRawY();
      return true;
     case MotionEvent.ACTION_MOVE:
      int sx=Math.round(ev.getRawX()-downX),sy=Math.round(ev.getRawY()-downY);
      android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
      int maxX=Math.max(0,dm.widthPixels-panel.getWidth());
      int maxY=Math.max(0,dm.heightPixels-panel.getHeight()-dp(20));
      params.x=Math.max(0,Math.min(maxX,originalX+sx));
      params.y=Math.max(0,Math.min(maxY,originalY+sy));
      try{windows.updateViewLayout(panel,params);
       android.util.Log.i("PULSO_DRAG","Moved x="+params.x+" y="+params.y);
      }catch(Exception e){android.util.Log.w("PULSO_DRAG","Falha ao mover janela",e);}
      return true;
     case MotionEvent.ACTION_UP:
     case MotionEvent.ACTION_CANCEL:
      uiPrefs.edit().putInt("x",params.x).putInt("y",params.y).apply();
      return true;
    }
    return false;
   }
  });
  windows.addView(panel,params);
  updateAccount();
 }
 private void refresh(){
  if(!pending.compareAndSet(false,true))return;
  final int currentRevision=revision;
  final String symbol=asset(),tf=interval();
  io.submit(()->{
   MarketClient.Quote result=null;
   double rate=Double.NaN;
   String error=null;
   try{result=MarketClient.load(symbol,tf);}catch(Exception e){error=e.getMessage();}
   if(result!=null){try{rate=FxClient.brlRate();}catch(Exception ignored){}}
   final MarketClient.Quote q=result;final double fx=rate;final String err=error;
   ui.post(()->{
    pending.set(false);
    if(stopped||revision!=currentRevision)return;
    quote=q;brlRate=fx;
    if(q==null){
     details.setText("SEM COTAÇÃO · AGUARDAR\n"+(err==null?"Sem dados válidos":err));
     details.setTextColor(Color.rgb(250,190,174));
    } else {
     String timestamp=new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(q.observedAt));
     details.setText(String.format(Locale.US,"%,.2f USDT · %s\nRSI %.0f · %s · %s",q.price,q.report.bias,q.report.rsi,tf,timestamp));
     details.setTextColor(q.report.bias.equals("VIÉS COMPRADOR")?Color.rgb(132,241,202):
       q.report.bias.equals("VIÉS VENDEDOR")?Color.rgb(251,187,177):Color.WHITE);
    }
    updateAccount();
   });
  });
 }
 private void updateAccount(){
  if(account!=null)account.setText(PaperWallet.summary(prefs,asset(),quote!=null&&Double.isFinite(brlRate)?quote.price*brlRate:Double.NaN));
 }
 private void simulate(boolean buy){
  String result=PaperWallet.trade(prefs,buy,asset(),
   quote!=null&&Double.isFinite(brlRate)?quote.price*brlRate:Double.NaN,
   quote!=null?quote.observedAt:0L);
  android.widget.Toast.makeText(this,result,android.widget.Toast.LENGTH_LONG).show();
  updateAccount();
 }
 @Override public int onStartCommand(Intent i,int flags,int id){return START_NOT_STICKY;}
 @Override public void onDestroy(){
  stopped=true;revision++;ui.removeCallbacks(poll);io.shutdownNow();
  if(windows!=null&&panel!=null)try{windows.removeView(panel);}catch(Exception ignored){}
  super.onDestroy();
 }
 @Override public IBinder onBind(Intent intent){return null;}
}