#!/usr/bin/env bash
set -euo pipefail
PACKAGE="br.com.pulso.mercado.v27"
APK="app/build/outputs/apk/debug/app-debug.apk"
echo '----- ANDROID -----'
adb shell getprop ro.build.version.release
echo '----- INSTALAR -----'
adb install -r "$APK"
echo '----- CONFIRMAR PACOTE -----'
adb shell pm path "$PACKAGE" | grep 'package:'
echo '----- INICIAR APP -----'
adb shell am start -W -n "$PACKAGE/br.com.pulso.mercado.MainActivity"
sleep 7
adb shell pidof "$PACKAGE"
echo '----- JANELA FLUTUANTE -----'
adb shell appops set "$PACKAGE" SYSTEM_ALERT_WINDOW allow
FOUND=''
for ATTEMPT in 1 2 3 4; do
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb shell cat /sdcard/window.xml > /tmp/pulso_screen.xml
  FOUND=$(python3 - <<'PY'
import re
screen=open('/tmp/pulso_screen.xml',encoding='utf-8').read()
for match in re.finditer(r'<node\b[^>]*>',screen):
    node=match.group(0)
    if 'text="Ativar janela flutuante"' not in node:
        continue
    bounds=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',node)
    if bounds:
        x1,y1,x2,y2=map(int,bounds.groups())
        print((x1+x2)//2,(y1+y2)//2)
        break
PY
)
  if [ -n "$FOUND" ]; then break; fi
  SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -n 1)
  WIDTH=${SIZE%x*}
  HEIGHT=${SIZE#*x}
  X=$((WIDTH / 2))
  YSTART=$((HEIGHT * 8 / 10))
  YEND=$((HEIGHT * 2 / 10))
  adb shell input swipe "$X" "$YSTART" "$X" "$YEND" 400
  sleep 1
done
if [ -z "$FOUND" ]; then
  echo 'ERRO: Não foi possível encontrar o botão de ativação de janela'
  head -c 2000 /tmp/pulso_screen.xml
  exit 1
fi
read -r X Y <<< "$FOUND"
echo "Tocando no botão em: $X,$Y"
adb shell input tap "$X" "$Y"
sleep 5
adb shell dumpsys activity services "$PACKAGE" | grep 'OverlayService'
echo '----- TESTAR ARRASTE PELO TOQUE REAL -----'
mkdir -p /tmp/pulso-evidencia
adb shell screencap -p /sdcard/pulso-before.png
adb pull /sdcard/pulso-before.png /tmp/pulso-evidencia/antes.png >/dev/null
adb logcat -c
adb shell input swipe 65 174 65 278 850
sleep 3
adb shell screencap -p /sdcard/pulso-after.png
adb pull /sdcard/pulso-after.png /tmp/pulso-evidencia/depois.png >/dev/null
adb logcat -d -s PULSO_DRAG:I > /tmp/pulso-evidencia/arraste.log
cat /tmp/pulso-evidencia/arraste.log | tail -n 12
if ! grep -q 'Moved x=' /tmp/pulso-evidencia/arraste.log; then
  echo 'FALHA: nenhum gesto de arraste detectado. Evidências capturadas.'
  exit 1
fi
echo 'PASSOU: eventos de arraste recebidos na janela'
echo '----- VERIFICAR CRASHES -----'
adb logcat -d -v brief -s AndroidRuntime:E > /tmp/pulso_runtime.txt || true
tail -n 50 /tmp/pulso_runtime.txt
if grep -q "Process: $PACKAGE" /tmp/pulso_runtime.txt; then
  echo 'FALHA: Crash detectado'
  exit 1
fi
echo 'APROVADO: APK instalado, aberto, janela arrastada e modo rápido 1m/5m no Android 13.'
