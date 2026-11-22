package br.com.pulso.mercado;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
/** Public market data only. No order execution or authentication. */
public final class MarketClient {
 private MarketClient(){}
 public static final class Quote {
  public final String symbol; public final double price; public final long observedAt; public final SignalEngine.Report report;
  public Quote(String s,double p,long at,SignalEngine.Report r){symbol=s;price=p;observedAt=at;report=r;}
 }
 public static Quote load(String symbol) throws Exception { return load(symbol,"1m"); }
 public static Quote load(String symbol,String interval) throws Exception {
  if(!"1m".equals(interval)&&!"5m".equals(interval))throw new Exception("Intervalo não permitido");
  if(!symbol.matches("(BTC|ETH|SOL|BNB)USDT"))throw new Exception("Ativo não permitido");
  Exception last=null;
  for(String host:new String[]{"https://data-api.binance.vision","https://api.binance.com"}){
   HttpURLConnection c=null;
   try {
    URL url=new URL(host+"/api/v3/klines?symbol="+symbol+ "&interval="+interval+"&limit=320");
    c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(6500);c.setReadTimeout(6500);
    c.setRequestProperty("Accept","application/json");
    if(c.getResponseCode()!=200)throw new Exception("HTTP "+c.getResponseCode());
    StringBuilder body=new StringBuilder();
    try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){String line;while((line=in.readLine())!=null)body.append(line);}
    JSONArray rows=new JSONArray(body.toString());long now=System.currentTimeMillis();
    if(rows.length()<96)throw new Exception("Histórico insuficiente");
    JSONArray live=rows.getJSONArray(rows.length()-1);long liveOpen=live.getLong(0);
    if(liveOpen>now+90000L||now-liveOpen>("5m".equals(interval)?360000L:180000L))throw new Exception("Feed desatualizado");
    double price=live.getDouble(4);if(!Double.isFinite(price)||price<=0)throw new Exception("Cotação inválida");
    List<Double> closes=new ArrayList<>();
    for(int i=0;i<rows.length();i++){JSONArray row=rows.getJSONArray(i);if(row.getLong(6)>=now)continue;double v=row.getDouble(4);if(Double.isFinite(v)&&v>0)closes.add(v);}
    if(closes.size()<95)throw new Exception("Histórico fechado insuficiente");
    return new Quote(symbol,price,now,SignalEngine.analyze(closes));
   }catch(Exception ex){last=ex;}finally{if(c!=null)c.disconnect();}
  }
  throw new Exception(last==null?"Feed indisponível":last.getMessage());
 }
}