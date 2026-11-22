import br.com.pulso.mercado.SignalEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public final class TestSignal {
 private static void assertTrue(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args){
  assertTrue("AGUARDAR".equals(SignalEngine.analyze(Collections.emptyList()).bias),"Empty must wait");
  List<Double> flat=new ArrayList<>(),trending=new ArrayList<>();
  for(int i=0;i<320;i++){flat.add(100.0);trending.add(100+0.08*i+Math.sin(i/5.0)*0.2);}
  SignalEngine.Report a=SignalEngine.analyze(flat),b=SignalEngine.analyze(trending);
  assertTrue(Double.isFinite(a.rsi)&&a.rsi>=0&&a.rsi<=100,"RSI invalid");
  assertTrue(Double.isFinite(b.estimateUp)&&b.estimateUp>=0&&b.estimateUp<=1,"Estimate invalid");
  assertTrue(b.validationSamples>=20,"No historical holdout");
  System.out.println("PASS: core signal tests");
 }
}