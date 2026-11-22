package br.com.pulso.mercado;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
/** Converts USDT quotes to BRL only when a public live USDTBRL quote is available. */
public final class FxClient {
 private FxClient(){}
 public static double brlRate() throws Exception {
  Exception last=null;
  for(String host:new String[]{"https://data-api.binance.vision","https://api.binance.com"}){
   HttpURLConnection c=null;
   try{
    c=(HttpURLConnection)new URL(host+"/api/v3/ticker/price?symbol=USDTBRL").openConnection();
    c.setConnectTimeout(6500);c.setReadTimeout(6500);c.setRequestProperty("Accept","application/json");
    if(c.getResponseCode()!=200)throw new Exception("Conversão BRL indisponível");
    StringBuilder s=new StringBuilder();
    try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){String line;while((line=in.readLine())!=null)s.append(line);}
    JSONObject json=new JSONObject(s.toString());
    if(!"USDTBRL".equals(json.getString("symbol")))throw new Exception("Par de conversão incorreto");
    double p=json.getDouble("price");
    if(!Double.isFinite(p)||p<=1||p>=20)throw new Exception("Conversão BRL inconsistente");
    return p;
   }catch(Exception e){last=e;}finally{if(c!=null)c.disconnect();}
  }
  throw new Exception(last==null?"BRL indisponível":last.getMessage());
 }
}