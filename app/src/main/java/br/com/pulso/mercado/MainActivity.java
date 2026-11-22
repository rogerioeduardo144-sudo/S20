package br.com.pulso.mercado;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
 private static final String[] SYMBOLS={"BTCUSDT","ETHUSDT","SOLUSDT","BNBUSDT"};
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final AtomicBoolean pending=new AtomicBoolean(false);
 private TextView priceView,signalView,statusView,paperView;
 private Spinner picker;
 private MarketClient.Quote quote;
 private double rate=Double.NaN;
 private boolean alive=true;
 private SharedPreferences prefs;
 private final Runnable poll=new Runnable(){@Override public void run(){if(!alive)return;refresh();ui.postDelayed(this,20000);}};
 @Override public void onCreate(Bundle b){
  super.onCreate(b);prefs=getSharedPreferences("paper-v1",MODE_PRIVATE);
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(20),dp(20),dp(20));root.setBackgroundColor(Color.rgb(10,20,35));
  sc.addView(root);setContentView(sc);
  TextView h=label("PULSO 2.6 · Mercado",27,Color.rgb(132,241,202));h.setTypeface(null,1);root.addView(h);
  root.addView(label("Dados públicos de mercado · atualização aproximada de 20 segundos",12,Color.LTGRAY));
  root.addView(label("Ativo observado",15,Color.WHITE));
  picker=new Spinner(this);
  ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Bitcoin · BTC/USDT","Ethereum · ETH/USDT","Solana · SOL/USDT","BNB · BNB/USDT"});
  picker.setAdapter(adapter);root.addView(picker);
  priceView=label("Aguardando cotação...",27,Color.WHITE);root.addView(priceView);
  signalView=label("AGUARDAR · Sem análise",20,Color.rgb(242,208,108));root.addView(signalView);
  statusView=label("Conectando à fonte pública...",13,Color.LTGRAY);root.addView(statusView);
  Button refreshBtn=button("Atualizar agora");root.addView(refreshBtn);refreshBtn.setOnClickListener(v->refresh());
  root.addView(label("Carteira de demonstração · R$ 100 fictícios",18,Color.rgb(132,241,202)));
  paperView=label("Saldo fictício R$ 100,00",17,Color.WHITE);root.addView(paperView);
  Button buy=button("COMPRAR — SOMENTE SIMULAÇÃO");root.addView(buy);buy.setOnClickListener(v->paperTrade(true));
  Button sell=button("VENDER — SOMENTE SIMULAÇÃO");root.addView(sell);sell.setOnClickListener(v->paperTrade(false));
  Button reset=button("Reiniciar simulação");root.addView(reset);reset.setOnClickListener(v->{prefs.edit().clear().apply();showPaper();});
  Button overlay=button("Ativar janela flutuante");root.addView(overlay);overlay.setOnClickListener(v->startOverlay());
  root.addView(label("Os sinais são experimentais e podem falhar. Não existe garantia de rentabilidade. Nenhuma ordem real é enviada. Simulação sem tarifas ou impostos. Se faltarem dados, a análise fica indisponível.",12,Color.LTGRAY));
  picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
   @Override public void onItemSelected(AdapterView<?> parent,View view,int pos,long id){quote=null;rate=Double.NaN;priceView.setText("Consultando...");signalView.setText("AGUARDAR");refresh();showPaper();}
   @Override public void onNothingSelected(AdapterView<?> p){}
  });
  showPaper();ui.post(poll);
 }
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView label(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextColor(color);t.setTextSize(size);t.setPadding(0,dp(9),0,dp(9));return t;}
 private Button button(String text){Button b=new Button(this);b.setText(text);b.setAllCaps(false);return b;}
 private String selected(){return SYMBOLS[Math.min(3,Math.max(0,picker.getSelectedItemPosition()))];}
 private void refresh(){
  if(!pending.compareAndSet(false,true))return;
  final String symbol=selected();statusView.setText("Consultando mercado... "+symbol);
  io.submit(()->{
   MarketClient.Quote result=null;double conversion=Double.NaN;String error=null;
   try{result=MarketClient.load(symbol);}catch(Exception e){error=e.getMessage();}
   if(result!=null){try{conversion=FxClient.brlRate();}catch(Exception ignored){}}
   final MarketClient.Quote q=result;final double brl=conversion;final String err=error;
   ui.post(()->{
    pending.set(false);if(!alive||!symbol.equals(selected()))return;
    quote=q;rate=brl;
    if(q==null){priceView.setText("Cotação indisponível");signalView.setText("AGUARDAR");statusView.setText("Sem dados confiáveis: "+err+". Verifique a conexão e tente novamente.");}
    else{
     priceView.setText(formatPrice(q.price)+" USDT"+(Double.isFinite(rate)?"  ·  "+money(q.price*rate):""));
     signalView.setText(q.report.bias);
     signalView.setTextColor(q.report.bias.equals("VIÉS COMPRADOR")?Color.rgb(132,241,202):q.report.bias.equals("VIÉS VENDEDOR")?Color.rgb(249,166,158):Color.rgb(242,208,108));
     statusView.setText("RSI "+Math.round(q.report.rsi)+" · acerto histórico de validação "+Math.round(q.report.accuracy*100)+"% em "+q.report.validationSamples+" amostras. Última consulta: "+new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(q.observedAt))+(Double.isFinite(rate)?"":" · BRL indisponível; simulação pausada"));
    }
    showPaper();
   });
  });
 }
 private String money(double amount){return NumberFormat.getCurrencyInstance(new Locale("pt","BR")).format(amount);}
 private String formatPrice(double price){return String.format(Locale.US,price<1?"%,.5f":price<100?"%,.3f":"%,.2f",price);}
 private double stored(String key,double fallback){try{return Double.parseDouble(prefs.getString(key,Double.toString(fallback)));}catch(Exception e){return fallback;}}
 private void save(String key,double value){prefs.edit().putString(key,Double.toString(value)).apply();}
 private void showPaper(){
  if(paperView==null)return;
  double cash=stored("cash",100),qty=stored("qty",0);String held=prefs.getString("held","");
  String line="Saldo: "+money(cash);
  if(qty>0){line+=" · Posição "+held+": "+String.format(Locale.US,"%.8f",qty)+" unidades";
   if(quote!=null&&Double.isFinite(rate)&&held.equals(selected()))line+=" · Total estimado: "+money(cash+qty*quote.price*rate);
  }else{line+=" · Sem posição aberta";}
  paperView.setText(line);
 }
 private void paperTrade(boolean buy){
  if(quote==null||!Double.isFinite(rate)||rate<=0){toast("Sem cotação atual e conversão BRL; simulação bloqueada");return;}
  double p=quote.price*rate,cash=stored("cash",100),qty=stored("qty",0);
  String held=prefs.getString("held","");
  if(buy){if(qty>0||cash<=0){toast("Encerre a posição atual antes de comprar novamente");return;}save("qty",cash/p);save("cash",0);prefs.edit().putString("held",selected()).apply();}
  else{if(qty<=0||!held.equals(selected())){toast("Selecione o ativo da posição aberta para vender");return;}save("cash",cash+qty*p);save("qty",0);prefs.edit().remove("held").apply();}
  showPaper();toast("Operação fictícia registrada. Nenhuma ordem foi enviada.");
 }
 private void toast(String s){android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_LONG).show();}
 private void startOverlay(){
  if(!Settings.canDrawOverlays(this)){toast("Autorize Aparecer sobre outros aplicativos e retorne ao PULSO");startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));return;}
  try{startForegroundService(new Intent(this,OverlayService.class));toast("Janela flutuante ativada");}
  catch(Exception e){toast("Não foi possível abrir a janela: "+e.getMessage());}
 }
 @Override protected void onDestroy(){alive=false;ui.removeCallbacks(poll);io.shutdownNow();super.onDestroy();}
}