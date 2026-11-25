# Metro Voice

Навигатор по метро для ушей: говорит в наушники текущую и следующую станцию. Работает офлайн, без GPS под землёй: позиция выводится из сетевой геолокации на станциях, ритма поезда (акселерометр) и графа линии.

- Спецификация: [docs/SPEC.md](docs/SPEC.md)
- Рынок и конкуренты: [docs/MARKET.md](docs/MARKET.md)

## Структура

| Модуль | Что внутри |
|---|---|
| `core/` | Чистый Kotlin, без Android: граф метро, детектор движения, трекер гипотез (HMM), политика объявлений, формат записи поездки, импорт из OpenStreetMap. Тестируется на ноутбуке |
| `app/` | Android: foreground service (датчики, геолокация, соты, Wi-Fi → движок → голос), лента линии, экспорт поездки |

## Сборка

Нужны JDK 17+ и Android SDK (platform 35).

```bash
./gradlew :core:test                 # тесты ядра
./gradlew :app:assembleDebug         # APK: app/build/outputs/apk/debug/app-debug.apk
```

## Вход и профиль

Вход через Google и Apple — Firebase Authentication, настройка: [docs/AUTH_SETUP.md](docs/AUTH_SETUP.md). Без неё приложение работает в гостевом режиме. Профиль (станции «дом» и «работа») хранится на телефоне.

## Скриншоты

Рендерятся из настоящего Compose-кода на JVM (Robolectric + Roborazzi), без эмулятора:

```bash
./gradlew :app:recordRoborazziDebug   # → docs/screenshots/*.png
```

| | | |
|---|---|---|
| ![](docs/screenshots/01-home.png) | ![](docs/screenshots/04-trip-station.png) | ![](docs/screenshots/10-line.png) |

## Стенд: симуляция поездок

Проверяет логику трекера на настоящем графе Тбилиси с синтетическими датчиками: телефон в руке, пропавшие точки, грубая геолокация, остановка в тоннеле, пересадка.

```bash
./gradlew :core:cli --args="simulate app/src/main/assets/cities/tbilisi.json"            # таблица сценариев
./gradlew :core:cli --args="simulate app/src/main/assets/cities/tbilisi.json transfer"   # + хронология сценариев с этим словом
```

Условия сценариев — допущения, а не измерения: стенд ловит ошибки логики, реальные записи поездок проверяют сами допущения. В приложении тот же симулятор запускается из профиля («Демо-поездка»).

## Дизайн-система

Токены в `app/src/main/kotlin/metrovoice/app/ui/Theme.kt`, компоненты в `ui/Components.kt`.

- Цвета: холст `F7F7F7`, карточки `FFFFFF`, чернила `1C1A1A`, акценты `D0EDF6` (спокойный) и `FDF6E4` (тёплый). Цвета линий берутся из данных города и затемняются до контраста 4.5:1 под белый текст.
- Шрифт: Onest (SIL OFL, `licenses/Onest-OFL.txt`). Шкала 12 / 14 / 16 / 20 / 28 / 36.
- Иконка: векторная, адаптивная (`res/mipmap-anydpi/ic_launcher.xml`), с одноцветным слоем для тематических иконок Android 13+; значок уведомлений `ic_stat_metro`.
- Экраны: главная (куда едем, линии по видам транспорта), поездка, линия, история, профиль.
- Вид транспорта — поле `mode` у линии в пакете города: новые линии и трамваи добавляются данными.

## Данные города

1. Выгрузка из [overpass-turbo.eu](https://overpass-turbo.eu): запрос ниже → Export → raw OSM data → `data/osm/tbilisi-overpass.json`.
2. Конвертация в пакет города:

```bash
./gradlew :core:cli --args="import-osm data/osm/tbilisi-overpass.json app/src/main/assets/cities/tbilisi.json tbilisi"
```

Запрос Overpass:

```
[out:json][timeout:60];
{{geocodeArea:Tbilisi}}->.a;
rel["route"="subway"](area.a)->.r;
.r out body;
node(r.r);
out body;
node["railway"="station"]["station"="subway"](area.a);
out body;
```

## Разбор записанной поездки

История поездок (иконка календаря) → «Поделиться» отдаёт файл `trip-….jsonl.gz`. Проигрывание через движок на ноутбуке:

```bash
./gradlew :core:cli --args="replay app/src/main/assets/cities/tbilisi.json trip-YYYYMMDD-HHMMSS.jsonl"
```

Выводит, что приложение сказало бы и когда, рядом с разметкой «я на станции X» (`TRUTH`).
