# Bypass

App Android standalone que resolve links de gate no proprio aparelho.
Sem conta, sem quota, sem servidor. Tudo roda no telefone.

## Download

Ultima versao assinada: aba **Releases** deste repositorio.

- pacote: `com.local.bypass`
- minSdk 24, targetSdk 34
- assinado v1 + v2

## Fluxo

1. abre o app -> campo de link
2. cola (`colar` puxa do clipboard) ou compartilha de outro app (share target `text/plain`)
3. `bypassar` -> o resolvedor segue a cadeia
4. resultado com `copiar` / `abrir` / `enviar`

## Arquitetura

Modulos independentes, o Hub escolhe o vencedor por score:

| modulo | papel |
|---|---|
| `CrawlerModule` | varredura generica de links |
| `WpProbe` | sites WordPress (wp-json, feeds) |
| `SpaProbe` | apps de pagina unica (rotas, JSON embutido) |
| `MetaProbe` | meta refresh, canonical, og:url |
| `WebProbe` | heuristicas de HTML |
| `ScriptProbe` | URLs dentro de JS |
| `LinkvertiseProbe` | API do Linkvertise (serial sem captcha) |
| `FormGateProbe` | gate por POST de formulario |
| `Lootlabs` | gate com captcha |
| `Rekonise` | corrente de partes |
| `WebGate` | WebView para desafio real (Cloudflare, Turnstile) |

## Build

Sem Gradle. `build.py` orquestra aapt2 + javac + d8 + zipalign + apksigner.

```
python3 build.py 3.4
```

Requisitos: JDK 17, build-tools android-14, `android.jar` API 34.

## Assinatura

O keystore de release **nao** esta neste repositorio. Gere o seu:

```
keytool -genkeypair -keystore release.keystore -alias bypass \
  -keyalg RSA -keysize 2048 -validity 10000
```
