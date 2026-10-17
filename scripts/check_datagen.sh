#!/usr/bin/env sh
set -e

./gradlew :NeoForge:runNeoforgeDatagen :NeoForge:runGardenOfGlassDatagen :Xplat:normalizeLanguageFiles || exit 1

STATUS="$(git status --porcelain Xplat/src/generated/resources Xplat/src/main/resources/assets/botania/lang NeoForge/src/generated/resources garden_of_glass/src/generated/resources)"
if [ -z "$STATUS" ]
then
  echo "Datagen ok"
else
  echo "Generated resources are dirty after running data generators. Please make sure you committed generated files. Dirty files:"
  echo "$STATUS"
  exit 1
fi

