package br.com.pulso.mercado;
import android.content.SharedPreferences;
import java.text.NumberFormat;
import java.util.Locale;

/** Carteira fictícia, independente da corretora. Nenhuma ordem é executada. */
public final class PaperWallet {
 private static final double FEE=0.001; // hipótese: 0,10% por lado
 private static final double SLIPPAGE=0.0005; // hipótese: 0,05% por lado
 private PaperWallet(){}
 public static double read(SharedPreferences p,String key,double fallback) {
  try{return Double.parseDouble(p.getString(key,Double.toString(fallback)));}
  catch(Exception ex){return fallback;}
 }
 public static String money(double value) {
  return NumberFormat.getCurrencyInstance(new Locale("pt","BR")).format(value);
 }
 public static synchronized String trade(SharedPreferences prefs,boolean buy,String symbol,double priceBrl,long observedAt) {
  if(!Double.isFinite(priceBrl)||priceBrl<=0||observedAt<=0||System.currentTimeMillis()-observedAt>35000L)
   return "Cotação ou câmbio indisponível/desatualizado. Simulação bloqueada.";
  double cash=read(prefs,"cash",100),quantity=read(prefs,"qty",0);
  String held=prefs.getString("held","");
  SharedPreferences.Editor e=prefs.edit();
  if(buy){
   if(quantity>0||cash<=0)return "Já existe uma posição fictícia. Venda antes de comprar novamente.";
   double fillPrice=priceBrl*(1+FEE+SLIPPAGE);
   double qty=cash/fillPrice;
   if(!Double.isFinite(qty)||qty<=0)return "Preço de simulação inválido.";
   e.putString("qty",Double.toString(qty)).putString("cash","0").putString("held",symbol);
   e.putString("entry",Double.toString(priceBrl)).apply();
   return "COMPRA FICTÍCIA de "+symbol+". Custos estimados incluídos.";
  }
  if(quantity<=0||!symbol.equals(held))return "Não há posição fictícia desse ativo para vender.";
  double received=quantity*priceBrl*(1-FEE-SLIPPAGE);
  e.putString("cash",Double.toString(cash+received)).putString("qty","0").remove("held").remove("entry").apply();
  return "VENDA FICTÍCIA efetuada. Saldo "+money(cash+received)+" (sem impostos).";
 }
 public static synchronized String summary(SharedPreferences p,String symbol,double priceBrl) {
  double cash=read(p,"cash",100),quantity=read(p,"qty",0);
  String held=p.getString("held","");
  String text="Carteira teste: "+money(cash);
  if(quantity>0){
   text+=" | "+held+" em aberto";
   if(symbol.equals(held)&&Double.isFinite(priceBrl)&&priceBrl>0){
    double equity=cash+quantity*priceBrl*(1-FEE-SLIPPAGE);
    text+=" | se vender: "+money(equity)+" ("+money(equity-100)+")";
   }
  } else text+=" | sem posição";
  return text;
 }
}