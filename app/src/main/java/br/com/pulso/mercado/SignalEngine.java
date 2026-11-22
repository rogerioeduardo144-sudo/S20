package br.com.pulso.mercado;
import java.util.ArrayList;
import java.util.List;
/** Experimental statistical learning on CLOSED candles. Not a profit prediction. */
public final class SignalEngine {
 private SignalEngine(){}
 private static final int FEATURES=7;
 private static class Sample {final double[] x; final int y; Sample(double[] a,int b){x=a;y=b;}}
 public static final class Report {
  public final String bias; public final double estimateUp,accuracy,baseline,rsi; public final int validationSamples;
  Report(String b,double p,double a,double base,double r,int n){bias=b;estimateUp=p;accuracy=a;baseline=base;rsi=r;validationSamples=n;}
 }
 private static double clip(double a,double low,double hi){return Math.max(low,Math.min(hi,a));}
 private static double sigmoid(double x){x=clip(x,-30,30);return 1/(1+Math.exp(-x));}
 private static double[] ema(List<Double> c,int period){double k=2.0/(period+1),v=c.get(0);double[] a=new double[c.size()];for(int i=0;i<a.length;i++){v=i==0?c.get(i):c.get(i)*k+v*(1-k);a[i]=v;}return a;}
 private static double valRsi(double g,double l){if(g==0&&l==0)return 50;if(l==0)return 100;return 100-100/(1+g/l);}
 private static double[] rsi(List<Double> c,int period){
  int n=c.size();double[] v=new double[n];for(int i=0;i<n;i++)v[i]=50;
  if(n<=period)return v;double gain=0,loss=0;
  for(int i=1;i<=period;i++){double d=c.get(i)-c.get(i-1);gain+=Math.max(0,d);loss+=Math.max(0,-d);}
  gain/=period;loss/=period;v[period]=valRsi(gain,loss);
  for(int i=period+1;i<n;i++){double d=c.get(i)-c.get(i-1);gain=(gain*(period-1)+Math.max(0,d))/period;loss=(loss*(period-1)+Math.max(0,-d))/period;v[i]=valRsi(gain,loss);}return v;
 }
 private static double[] features(List<Double> c,double[] e9,double[] e21,double[] r,int i){
  double r1=(c.get(i)/c.get(i-1)-1)*100,r3=(c.get(i)/c.get(i-3)-1)*100,r5=(c.get(i)/c.get(i-5)-1)*100;
  return new double[]{1,clip(r1,-3,3),clip(r3/2,-3,3),clip(r5/3,-3,3),clip((e9[i]/e21[i]-1)*80,-3,3),clip((r[i]-50)/18,-3,3),clip(Math.abs(r1)*.3,-3,3)};
 }
 private static double predict(double[] x,double[] w){double z=0;for(int j=0;j<FEATURES;j++)z+=x[j]*w[j];return sigmoid(z);}
 private static double[] fit(List<Sample> samples){
  double[] w=new double[FEATURES];int p=0;for(Sample s:samples)p+=s.y;
  w[0]=Math.log((p+1.0)/(samples.size()-p+1.0));
  for(int epoch=0;epoch<15;epoch++)for(Sample s:samples){double e=predict(s.x,w)-s.y;for(int j=0;j<FEATURES;j++)w[j]-=.018*(e*s.x[j]+(j==0?0:.001*w[j]));}
  return w;
 }
 public static Report analyze(List<Double> c){
  if(c==null||c.size()<95)return new Report("AGUARDAR",.5,0,0,50,0);
  for(double p:c)if(!Double.isFinite(p)||p<=0)return new Report("AGUARDAR",.5,0,0,50,0);
  int n=c.size();double[] e9=ema(c,9),e21=ema(c,21),r=rsi(c,14);
  List<Sample> s=new ArrayList<>();
  for(int i=31;i<n-1;i++)s.add(new Sample(features(c,e9,e21,r,i),c.get(i+1)>c.get(i)?1:0));
  int cut=Math.max(30,(int)Math.floor(s.size()*.73));List<Sample> training=s.subList(0,cut),valid=s.subList(cut,s.size());
  double[] w=fit(training);int correct=0,up=0;
  for(Sample a:valid){if((predict(a.x,w)>=.5?1:0)==a.y)correct++;up+=a.y;}
  double acc=valid.isEmpty()?0:(double)correct/valid.size(),baseline=valid.isEmpty()?0:(double)Math.max(up,valid.size()-up)/valid.size();
  double p=predict(features(c,e9,e21,r,n-1),fit(s));
  String bias="AGUARDAR";boolean eligible=valid.size()>=20&&acc>=.52&&acc>=baseline-.04;
  if(eligible&&p>=.60&&e9[n-1]>e21[n-1]&&r[n-1]<75)bias="VIÉS COMPRADOR";
  if(eligible&&p<=.40&&e9[n-1]<e21[n-1]&&r[n-1]>25)bias="VIÉS VENDEDOR";
  return new Report(bias,p,acc,baseline,r[n-1],valid.size());
 }
}