# GuardX VPNBot

Android VPN приложение на основе v2rayNG для безопасного подключения к интернету.

## О проекте

Это модифицированная версия [v2rayNG](https://github.com/2dust/v2rayNG) с дополнительными функциями и улучшениями.

## Возможности

- Поддержка различных протоколов VPN
- Простой и понятный интерфейс
- Быстрое подключение
- Поддержка нескольких архитектур: ARM64, ARM, x86, x86_64

## Скачивание

Скачайте последнюю версию APK из раздела [Releases](https://github.com/ВАШ_ЮЗЕРНЕЙМ/vpn_bot_app_v2/releases):

- `GuardX-VPNBot_X.X.X_arm64-v8a.apk` - для современных устройств (рекомендуется)
- `GuardX-VPNBot_X.X.X_armeabi-v7a.apk` - для старых устройств
- `GuardX-VPNBot_X.X.X_universal.apk` - для всех устройств (больший размер)

## Установка

1. Скачайте подходящий APK файл
2. Разрешите установку из неизвестных источников в настройках Android
3. Откройте APK файл и установите приложение
4. Готово!

## Сборка из исходников

### Требования

- Android Studio Arctic Fox или новее
- JDK 17
- Android SDK 35
- Gradle 9.0

### Инструкция

1. Клонируйте репозиторий:
```bash
git clone https://github.com/ВАШ_ЮЗЕРНЕЙМ/vpn_bot_app_v2.git
cd vpn_bot_app_v2
```

2. Откройте проект в Android Studio

3. Соберите APK:
   - **Вариант 1:** Build → Build Bundle(s) / APK(s) → Build APK(s)
   - **Вариант 2:** Через терминал:
```bash
./gradlew assemblePlaystoreRelease
```

4. APK будет в `app/build/outputs/apk/playstore/release/`

## Лицензия

Этот проект распространяется под лицензией GPL-3.0, как и оригинальный v2rayNG.

Основано на проекте [v2rayNG](https://github.com/2dust/v2rayNG) by [@2dust](https://github.com/2dust)

## Благодарности

- [v2rayNG](https://github.com/2dust/v2rayNG) - оригинальный проект
- [v2ray-core](https://github.com/v2ray/v2ray-core) - ядро v2ray
- Всем контрибьюторам open source проектов

## Поддержка

Если у вас возникли проблемы или вопросы, создайте [Issue](https://github.com/ВАШ_ЮЗЕРНЕЙМ/vpn_bot_app_v2/issues).

## Отказ от ответственности

Это приложение предназначено только для легального использования. Пользователь несет полную ответственность за использование приложения в соответствии с законами своей страны.
