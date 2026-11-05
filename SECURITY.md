# Безпека · Security

**Українською**

**Як повідомити про вразливість.** Приватно, через GitHub: вкладка Security → «Report a vulnerability» у цьому репозиторії. Будь ласка, не пишіть про вразливість у звичайних Issues, поки її не виправлено.

**Строки.**
- Підтвердження, що звіт отримано, — протягом 3 днів.
- Критичну вразливість (доступ до чужих даних, обхід захисту, підроблене оновлення) виправляю протягом 7 днів.
- Решту — у найближчому випуску.

**Що підтримується.** Лише остання версія з [Releases](https://github.com/Marybezu/tarilka/releases/latest). Виправлення виходять новою версією; старі не оновлюються.

**Мінімальна версія Android — 12** (API 31): ключ щоденника в сховищі ключів, що відкривається відбитком або PIN-кодом, працює так з Android 11, а кольори телефона (Material You) — з Android 12; одна межа простіша для перевірки, ніж дві. Для ШІ на телефоні потрібно від 6 ГБ оперативної памʼяті.

**Як перевірити, що APK справжній.** Сертифікат підпису має SHA-256 `64:EA:F9:73:EC:74:AA:88:AB:62:A6:B3:2B:95:52:CC:87:13:04:23:85:FA:3D:74:9F:66:69:8B:CD:B2:5C:60`. Перевірка: `apksigner verify --print-certs tarilka-<версія>.apk` (apksigner є в Android SDK Build Tools).

---

**In English**

**How to report a vulnerability.** Privately, through GitHub: the Security tab → Report a vulnerability in this repository. Please do not describe it in public Issues until it is fixed.

**Timelines.**
- Receipt is confirmed within 3 days.
- A critical vulnerability (access to someone's data, bypassing protection, a forged update) is fixed within 7 days.
- Others in the next release.

**Supported versions.** Only the latest one in [Releases](https://github.com/Marybezu/tarilka/releases/latest). Fixes come as a new version; older ones are not updated.

**Minimum Android version: 12** (API 31). The diary key in the key store, opened with a fingerprint or PIN, works this way from Android 11, and the phone's colours (Material You) need Android 12; one floor is simpler to test than two. The on-device AI needs 6 GB of RAM or more.

**How to check that an APK is genuine.** The signing certificate's SHA-256 is `64:EA:F9:73:EC:74:AA:88:AB:62:A6:B3:2B:95:52:CC:87:13:04:23:85:FA:3D:74:9F:66:69:8B:CD:B2:5C:60`. Check: `apksigner verify --print-certs tarilka-<version>.apk` (apksigner comes with the Android SDK Build Tools).
