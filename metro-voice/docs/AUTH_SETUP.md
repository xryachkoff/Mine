# Вход через Google и Apple: настройка

Без этой настройки приложение работает в гостевом режиме, а кнопки входа сообщают «вход ещё не настроен».

## 1. Firebase-проект

1. https://console.firebase.google.com → **Add project** (Analytics не нужна).
2. **Add app → Android**:
   - Package name: `metrovoice.app`
   - SHA-1 (общий debug-ключ проекта `app/debug.keystore`):
     `2D:1E:B8:33:16:AA:47:36:E3:81:03:2E:C3:39:3C:57:4B:92:02:26`
3. Скачать `google-services.json` и положить в `app/google-services.json` (в git не попадает).

## 2. Google

Firebase → **Authentication → Sign-in method → Google → Enable**. Больше ничего: `default_web_client_id` попадёт в сборку из `google-services.json`.

## 3. Apple

Нужен Apple Developer Program ($99/год).

1. developer.apple.com → Identifiers → App ID с включённым **Sign in with Apple**.
2. Identifiers → **Services ID** (например `metrovoice.app.signin`), включить Sign in with Apple, в Return URLs указать
   `https://<project-id>.firebaseapp.com/__/auth/handler`.
3. Keys → новый ключ с **Sign in with Apple** → скачать `.p8`, запомнить Key ID и Team ID.
4. Firebase → Authentication → Sign-in method → **Apple → Enable**: Services ID, Team ID, Key ID, содержимое `.p8`.

На Android вход через Apple открывает веб-страницу Apple (нативного SDK нет).

## 4. Сборка

```bash
./gradlew :app:assembleDebug
```

Плагин `google-services` подключается автоматически, если файл `app/google-services.json` существует.
