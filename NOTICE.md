# Ліцензії й джерела · Licences and sources

Тарілка — вільна програма під **GNU GPL версії 3 або (на ваш вибір) будь-якої пізнішої версії** (GPL-3.0-or-later), без жодних гарантій; повний текст — у [LICENSE](LICENSE).
Tarilka is free software under the **GNU GPL version 3 or (at your option) any later version** (GPL-3.0-or-later), with no warranty; full text in [LICENSE](LICENSE).

## Бібліотеки · Libraries

| Бібліотека · Library | Автор · Author | Ліцензія · Licence |
| --- | --- | --- |
| Kotlin, kotlinx.coroutines | JetBrains s.r.o. | Apache 2.0 |
| AndroidX: Core, AppCompat, Activity, Lifecycle, ExifInterface, Room, SQLite, Biometric | The Android Open Source Project (Google) | Apache 2.0 |
| Jetpack Compose: UI, Material 3, Material Icons | The Android Open Source Project (Google) | Apache 2.0 |
| Health Connect client (`androidx.health.connect`) | The Android Open Source Project (Google) | Apache 2.0 |
| SQLCipher for Android | © Zetetic LLC | BSD-3-Clause |
| LiteRT-LM (лише у версії з GitHub · GitHub version only) | Google LLC | Apache 2.0 |

Для тестів (не входять у застосунок) · For tests only (not in the app): JUnit 4 (EPL 1.0), org.json (Public Domain).

## Модель ШІ · AI model

Модель Gemma (необовʼязкова, у версії з GitHub) не входить у застосунок: людина завантажує її сама. Вона належить Google і має умови, вказані на сторінці моделі.
The Gemma model (optional, GitHub version) is not part of the app: people download it themselves. It belongs to Google and comes under the terms on its model page.

## Дані про продукти · Food data

- U.S. Department of Agriculture, Agricultural Research Service. FoodData Central, SR Legacy (2018), суспільне надбання · public domain. `scripts/food_sources/usda.csv`.
- Типові значення з етикеток українських продуктів · typical label values of Ukrainian products: `scripts/food_sources/label.csv`.
- Страви, розраховані за рецептами з цих даних · dishes calculated from recipes using the data above: `scripts/food_sources/dishes.json`.
- Клітковина для кількох продуктів, яких немає в SR Legacy · fibre for a few foods without it in SR Legacy: Ciqual 2020 (ANSES) та інші, вказані в `scripts/food_sources/fiber_fill.csv`.

## Поради · Advice

Поради застосунку спираються на настанови ВООЗ, EFSA, WCRF, NHS і EAT-Lancet; джерело вказано біля кожної поради. Вони загальні й не замінюють лікаря.
The app's tips follow guidance from WHO, EFSA, WCRF, NHS and EAT-Lancet; each tip names its source. They are general and do not replace a doctor.
