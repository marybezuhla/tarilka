# Приватність · Privacy

**Українською**

Тарілка не має дозволу на інтернет: вона нічого не надсилає й нічого не завантажує сама.

**Які дані є і де вони лежать**
- Щоденник (їжа, напої, вага, талія, сон, кишківник, тренування, цикл, нотатки, «Чому я їм»), профіль (стать, вік, зріст, вага, активність, мета, вагітність і годування груддю), свої продукти й рецепти — лише на цьому телефоні. Із захистом щоденник зашифрований SQLCipher, а профіль — окремо (AES-GCM); ключ лежить в Android Keystore і відкривається відбитком чи PIN-кодом телефону.
- Без шифрування навіть із захистом лишаються: налаштування, журнал кроків з датчика телефону, кілька чисел за сьогодні для нагадувань, очікувана дата циклу (лише з нагадуванням про цикл), поправка грамів для фото (як ви зазвичай виправляєте порції ШІ), фото тарілки, поки ШІ його розбирає (потім воно видаляється), і копії в Health Connect.
- Хмарних копій і перенесення Android немає (`allowBackup="false"`, усе виключено). Резервна копія — лише вручну, у файл, який обираєте ви; з паролем вона шифрується AES-256. CSV і PDF не шифруються.

**Що бачать сторонні**
- **Health Connect** — лише те, що ви там дозволили: Тарілка пише харчування, рідину й вагу та читає кроки, сон і цикл. Записи в Health Connect бачать застосунки, яким ви дали там доступ.
- **GitHub** — лише коли ви самі натискаєте «Написати відгук» чи «Надіслати список»: відкривається браузер із готовим текстом, і GitHub бачить його вже під час відкриття сторінки, а браузер запамʼятовує адресу. Опубліковано буде лише після вашого підтвердження, від вашого акаунта.
- **Hugging Face** — лише якщо ви самі відкриваєте сторінку моделі ШІ, щоб її завантажити (у версії з GitHub).
- Аналітики, реклами, акаунтів і звітів про збої назовні немає.

**Як усе стерти**
- Опції → Дані → «Видалити всі особисті дані»: щоденник (разом із циклом), свої продукти й рецепти, обрані, профіль, поправка грамів для фото, журнал кроків із датчика й список «Не знайдено»; лишаються налаштування (мова, тема, нагадування) і модель ШІ. Позначка «Також видалити записи, які Тарілка внесла в Health Connect» прибирає й те, що Тарілка туди записала. Записи інших застосунків (кроки, сон, цикл) Тарілка не чіпає: їх видаляють у самому Health Connect.
- Видалення застосунку прибирає все, що він зберігав на телефоні, разом із моделлю ШІ. Ваші файли резервних копій, CSV і PDF лишаються там, куди ви їх зберегли.

---

**In English**

Tarilka has no internet permission: it sends nothing and downloads nothing by itself.

**What data there is and where it lives**
- The diary (food, drinks, weight, waist, sleep, bowel, workouts, cycle, notes, "Why I eat"), the profile (sex, age, height, weight, activity, goal, pregnancy and breastfeeding), your own foods and recipes: only on this phone. With protection on, the diary is encrypted with SQLCipher and the profile separately (AES-GCM); the key is in the Android Keystore and opens with your fingerprint or the phone PIN.
- Not encrypted even with protection: settings, the step log from the phone's sensor, a few of today's numbers for reminders, the expected cycle date (only with cycle reminders), the photo portion correction (how you usually correct the AI's portions), a plate photo while the AI reads it (it is deleted afterwards), and copies in Health Connect.
- No Android cloud backup or device transfer (`allowBackup="false"`, everything excluded). Backups are by hand only, to a file you choose; with a password they are AES-256 encrypted. CSV and PDF are not encrypted.

**What others can see**
- **Health Connect**: only what you allowed there. Tarilka writes nutrition, fluids and weight and reads steps, sleep and cycle. Records in Health Connect are visible to apps you gave access there.
- **GitHub**: only when you tap Send feedback or Send the list yourself. The browser opens with the text filled in; GitHub sees it as soon as the page opens, and the browser remembers the address. Nothing is published until you confirm it, from your own account.
- **Hugging Face**: only if you open the AI model's page yourself to download it (GitHub version).
- No analytics, ads, accounts or crash reports sent anywhere.

**How to erase everything**
- Options → Data → Delete all personal data: the diary (cycle included), your foods and recipes, favourites, the profile, the photo portion correction, the phone-sensor step log and the Not found list; settings (language, theme, reminders) and the AI model stay. The box "Also delete the records Tarilka wrote to Health Connect" removes what Tarilka wrote there too. Records of other apps (steps, sleep, cycle) are not touched by Tarilka: delete them in Health Connect itself.
- Uninstalling the app removes everything it kept on the phone, the AI model included. Your backup, CSV and PDF files stay where you saved them.
