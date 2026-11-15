# Metro Voice — рынок и конкуренты (ноябрь 2026)

## Главный вывод

**Боль обратно пропорциональна качеству бортовой информации, а не размеру метро.** Крупные богатые метро (Лондон, Токио, новые составы Нью-Йорка и Москвы) — это лучшие табло, внятные объявления и почти полное покрытие связью в тоннелях. Последнее ещё и обнуляет технический барьер: с непрерывной связью «следующую станцию» тривиально делают Google / Citymapper / Transit. Сильнее всего боль там, где табло мало, объявления плохие, а связи между станциями нет: Тбилиси и похожие системы.

## Насколько распространена проблема

Reddit нашему поисковому агенту недоступен — посчитать упоминания там напрямую не удалось. Косвенные данные:

- Обзоры наушников: при максимальном шумоподавлении тестер пропускал объявления на платформе; стандартный обходной путь — режим прозрачности вручную.
- CNBC, тест Adaptive Audio в AirPods: объявления на станциях BART оставались приглушёнными при музыке, приходилось ставить на паузу.
- Нью-Йорк: объявления в старых вагонах годами критикуют как неразборчивые, у части составов нет цифровых табло; старые вагоны выводятся из эксплуатации в плане 2025–2029, MTA в 2026 смотрит в сторону ИИ-объявлений на нескольких языках.
- Тбилиси: объявления на грузинском и английском, но цифровые табло есть лишь в немногих вагонах; путеводители прямо советуют отслеживать остановку в приложении.
- Япония: отдельная категория приложений-будильников «не проспать станцию» (Inemuri, GPS-будильники) — боль есть, но в форме «уснул», а не «в наушниках».
- Незрячие пользователи (форум AppleVis) явно просили озвучку «следующая остановка» в Moovit.

**Оценка:** боль реальная, но слабая — «витамин, а не обезболивающее». Обходные пути дешёвые: режим прозрачности, взгляд в окно, счёт станций. Значит, готовность платить низкая.

## Кто уже решает

| Решение | Что делает | Ограничение |
|---|---|---|
| Сами наушники (Adaptive Audio, Adaptive Sound Control, прозрачность) | Подмешивают внешний звук | Плохо работает на громкой музыке; **главная угроза: функция «пропускать объявления» в прошивке убьёт сценарий** |
| Transit — GO Underground | Офлайн-детекция движения, позиция между станциями, напоминание о выходе | Ближайший аналог; визуальный, сопровождение маршрута, не голосовой гид |
| Citymapper — Get Off Alerts | Оповещение о выходе, заявлено офлайн | Только «выходи», механизм не раскрыт |
| Google Maps | Напоминание о выходе в режиме поездки | Поведение под землёй не документировано |
| Moovit | Оповещения о выходе | По старому отзыву — зависят от связи |
| Inemuri (Япония) | Будильник у станции назначения, 8 878 станций, $2.99, только iPhone | Будильник, не сопровождение |
| Metro Station Prague (open source) | Офлайн по сотам, уведомления в пражском метро | Уведомления часто приходят поздно — полезный урок для нас |
| Яндекс Метро | Офлайн-маршруты, вагон, выходы | Функции объявления станций не найдено |

**Пробел:** непрерывного голосового сопровождения «станция / следующая» в наушники не найдено. Отсутствие доказательств ≠ доказательство отсутствия — проверить вручную в Transit и Citymapper.

## Целевые города

| Город | Боль | Технический барьер для конкурентов | Вердикт |
|---|---|---|---|
| Нью-Йорк | Высокая на старых вагонах, падает с их заменой | Связь в тоннелях — по линиям, полное покрытие ~через 10 лет | Интересен, но экспресс/локальные поезда, пропускающие станции, сильно усложняют алгоритм; рынок занят Transit / Citymapper / Google |
| Лондон | Низкая: табло и объявления почти везде | Полная 4G/5G в тоннелях — цель на конец 2026 | Слабый |
| Токио | Низкая: двуязычные объявления и табло | — | Слабый для местных; японские приложения-будильники уже есть |
| Москва | Низкая–средняя | Связь в тоннелях есть | Доминирует Яндекс; монетизация через Google Play в России ограничена с 2022 |
| Тбилиси и похожие (Баку, Ереван, Ташкент, Алматы — гипотеза) | Высокая | Связь только на станциях | **Лучший фит**, но рынки маленькие |

## Монетизация — трезво

- B2C-навигация по транспорту плохо монетизируется: Citymapper при лояльной аудитории не смог заработать и в 2023 был продан Via, по сообщениям — недорого.
- Transit: подписка Royale ($24.99/год), но значимый канал — **транспортные агентства, которые оплачивают подписку всем пассажирам**.
- Нишевый ориентир: Inemuri — разовая покупка $2.99.

Реалистичные модели:
1. Разовая покупка / пакет города за $2–5.
2. **B2B: доступность.** Агентства обязаны обеспечивать доступность (ADA (Americans with Disabilities Act) в США); голосовой гид для слабовидящих и визуальный для слабослышащих — понятная продажа.
3. Технология как функция для крупного игрока (лицензия / продажа).

## Когнитивные искажения в исходной гипотезе

- **Эффект ложного консенсуса:** «мне неудобно → многим неудобно». Проверяется только данными.
- **Эвристика доступности:** «большое шумное метро = больше боли». Шум — да, но там же лучшая инфраструктура информирования.
- Вывод: Тбилиси — не тестовый полигон перед «настоящими» рынками, а, возможно, и есть лучший рынок.

## Источники

- https://www.anclab.pro/blog/buying-guides-by-use-case-new-releases/subway-noise-cancelling-headphones-commuter-tested
- https://www.cnbc.com/2023/09/18/airpods-pro-usb-c-hands-on-adaptive-audio-and-conversation-awareness.html
- https://www.nysun.com/article/new-york-inaudible-announcements-in-subways-are
- https://www.cbsnews.com/newyork/news/mta-considering-upgrading-speakers-in-old-subway-cars
- https://www.planetizen.com/news/2026/09/138504-mta-looks-bring-ai-assisted-multilingual-announcement-system-subways
- https://www.amny.com/nyc-transit/mta-new-r211-trains-d-train/
- https://about.att.com/story/2026/att-boldyn-cellular-service-mta.html
- https://www.nbcnewyork.com/news/local/could-cell-service-be-coming-to-nyc-subway-tunnels-mta-says-yes/3795582/
- https://www.ispreview.co.uk/index.php/2026/06/london-underground-set-to-complete-4g-and-5g-mobile-build-by-end-of-2026.html
- https://www.boldyn.com/news/whole-tube-network-on-track-for-mobile-coverage-by-end-of-the-year-as-further-sections-go-live
- https://appsavvytraveller.com/tbilisi-public-transport/
- https://madloba.info/en/blog/transport-in-georgia/tbilisi-metro
- https://www.tokyoupdates.metro.tokyo.lg.jp/en/post-610/
- https://jnsforum.com/community/topic/10839-announcements-on-board-tokyo-metro-trains-and-beyond
- https://apps.apple.com/us/app/id1630651126 (Inemuri)
- https://www.producthunt.com/p/transit-bus-subway-times/go-underground
- https://content.citymapper.com/news/823/get-off-alerts
- https://techcrunch.com/2017/12/09/google-maps-will-soon-tell-you-when-its-time-to-get-off-your-train-or-bus
- https://applevis.com/comment/54687 (Moovit)
- https://www.github.com/pr0chz/metrostation (Metro Station Prague)
- https://yandex.ru/support/metro
- https://www.bloomberg.com/news/articles/2023-03-16/citymapper-journey-planning-app-sold-to-transit-tech-company-via
- https://techcrunch.com/2023/05/02/citymapper-lowers-the-paywall-for-premium-features-but-keeps-a-monthly-subscription-for-removing-ads
- https://www.masstransitmag.com/technology/passenger-info/mobile-applications/article/21234959/transit-launches-subscriptionbased-royale
