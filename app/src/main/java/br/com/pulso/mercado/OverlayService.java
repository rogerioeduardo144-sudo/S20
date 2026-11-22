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
public class OverlayService extends Service {
 private WindowManager windows;
 private LinearLayout panel;
 private TextView label,details;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final String[] symbols={"BTCUSDT","ETHUSDT","SOLUSDT","BNBUSDT"};
 private int index=0,revision=0;private boolean stopped=false;
 private final Runnable poll=new Runnable(){@Override public void run(){
  if(stopped)return;
  final int rev=revision;final String symbol=symbols[index];
  io.submit(()->{
   String msg;
   try{MarketClient.Quote q=MarketClient.load(symbol);
    msg=String.format(Locale.US,"%,.2f USDT · %s\nRSI %.0f · %s",q.price,q.report.bias,q.report.rsi,new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(q.observedAt)));
   }catch(Exception e){msg="Cotação indisponível · AGUARDAR\nConfira internet e disponibilidade";}
   final String out=msg;
   ui.post(()->{if(!stopped&&revision==rev&&details!=null)details.setText(out);});
  });
  ui.postDelayed(this,20000);
 }};
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 @Override public void onCreate(){super.onCreate();
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  nm.createNotificationChannel(new NotificationChannel("pulso","PULSO em execução",NotificationManager.IMPORTANCE_LOW));
  Intent open=new Intent(this,MainActivity.class);
  PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  Notification n=new Notification.Builder(this,"pulso").setSmallIcon(android.R.drawable.ic_menu_info_details).setContentTitle("PULSO · Janela flutuante")
   .setContentText("Toque na janela para alternar o ativo").setContentIntent(pi).setOngoing(true).build();
  startForeground(240,n);
  try{draw();ui.post(poll);}catch(Exception e){stopSelf();}
 }
 private void draw(){
  if(!Settings.canDrawOverlays(this))throw new IllegalStateException("Permissão de janela necessária");
  windows=(WindowManager)getSystemService(WINDOW_SERVICE);
  panel=new LinearLayout(this);panel.setGravity(Gravity.CENTER_VERTICAL);panel.setPadding(dp(12),dp(7),dp(5),dp(7));
  GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(12,29,48));bg.setCornerRadius(dp(18));bg.setStroke(dp(1),Color.rgb(76,118,140));panel.setBackground(bg);
  LinearLayout texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);
  label=new TextView(this);label.setText("PULSO · BTC");label.setTextColor(Color.rgb(132,241,202));label.setTextSize(15);label.setTypeface(null,1);
  details=new TextView(this);details.setText("Consultando mercado...");details.setTextColor(Color.WHITE);details.setTextSize(12);
  texts.addView(label);texts.addView(details);panel.addView(texts,new LinearLayout.LayoutParams(dp(235),-2));
  TextView close=new TextView(this);close.setText("×");close.setTextSize(29);close.setTextColor(Color.WHITE);close.setGravity(Gravity.CENTER);panel.addView(close,new LinearLayout.LayoutParams(dp(34),dp(46)));
  close.setOnClickListener(v->stopSelf());
  texts.setOnClickListener(v->{index=(index+1)%symbols.length;revision++;label.setText("PULSO · "+symbols[index].replace("USDT",""));details.setText("Consultando...");ui.removeCallbacks(poll);ui.post(poll);});
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
  p.gravity=Gravity.TOP|Gravity.START;p.x=dp(12);p.y=dp(130);
  label.setOnTouchListener(new View.OnTouchListener(){int x,y;float dx,dy;boolean dragged;
   @Override public boolean onTouch(View view,MotionEvent ev){switch(ev.getActionMasked()){
    case MotionEvent.ACTION_DOWN:x=p.x;y=p.y;dx=ev.getRawX();dy=ev.getRawY();dragged=false;return true;
    case MotionEvent.ACTION_MOVE:int xx=Math.round(ev.getRawX()-dx),yy=Math.round(ev.getRawY()-dy);if(Math.abs(xx)+Math.abs(yy)>dp(8))dragged=true;
     if(dragged){p.x=Math.max(0,x+xx);p.y=Math.max(0,y+yy);windows.updateViewLayout(panel,p);}return true;
    case MotionEvent.ACTION_UP:if(!dragged)texts.performClick();return true;}
    return false;
   }
  });
  windows.addView(panel,p);
 }
 @Override public int onStartCommand(Intent i,int flags,int startId){return START_NOT_STICKY;}
 @Override public void onDestroy(){stopped=true;revision++;ui.removeCallbacks(poll);io.shutdownNow();if(windows!=null&&panel!=null){try{windows.removeView(panel);}catch(Exception ignored){}}super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}