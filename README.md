# PULSO — Android

Aplicativo educacional de acompanhamento de cotações públicas de criptoativos, com indicadores técnicos e carteira **simulada**, nunca ordens reais.

- Dados: API pública de mercado Binance (sujeita a disponibilidade regional e limites de acesso).
- Ativos: BTC, ETH, SOL e BNB, com par USDT.
- Acompanhamento pelo aplicativo e janela flutuante sobre outros aplicativos, mediante permissão explícita no Android.
- Indicadores: médias móveis e RSI; sinais experimentais, **não preveem retornos nem representam recomendação de investimento**.
- Compilação: GitHub Actions > Gerar APK PULSO > Artifacts > PULSO-Android-APK.

**Segurança:** sem login, sem coleta de credenciais de corretoras, sem compra ou venda real. O aplicativo pode deixar de receber cotações se não houver internet, se houver restrições locais ou indisponibilidade do provedor. O indicador mostra erro em vez de simular preços. Projeto de teste, sem auditoria de segurança nem validação no aparelho do usuário.

Projeto enviado com autorização do titular ao repositório S20. Este repositório está público, mas o código não contém credenciais.
