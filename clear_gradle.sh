#!/usr/bin/env bash
set -euo pipefail

# Определить версию из gradle-wrapper
WRAPPER="gradle/wrapper/gradle-wrapper.properties"
if [ ! -f "$WRAPPER" ]; then
  echo "Не найден $WRAPPER" >&2
  exit 1
fi

PREFERRED=$(grep -E '^distributionUrl=' "$WRAPPER" | sed -E 's/.*gradle-([0-9.]+).*/\1/')
if [ -z "$PREFERRED" ]; then
  echo "Не удалось определить версию Gradle в $WRAPPER" >&2
  exit 1
fi

echo "Оставлять версию: $PREFERRED"

# Удалить каталоги версий в .gradle, кроме нужной
if [ -d ".gradle" ]; then
  for d in .gradle/*; do
    [ -d "$d" ] || continue
    name=$(basename "$d")
    if [[ $name =~ ^[0-9]+(\.[0-9]+)*$ ]]; then
      if [ "$name" != "$PREFERRED" ]; then
        echo "Удаляю $d"
        rm -rf "$d"
      else
        echo "Сохраняю $d"
      fi
    fi
  done
else
  echo ".gradle не найден — ничего не делаю"
fi

# Обновить .gitignore
if ! grep -qxF ".gradle/" .gitignore 2>/dev/null; then
  echo ".gradle/" >> .gitignore
  echo "Добавлено .gradle/ в .gitignore"
fi

echo "Готово."