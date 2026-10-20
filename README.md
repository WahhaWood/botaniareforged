<div align="center">

# Botania Reforged

**Форк Botania для Minecraft 1.21.1 / NeoForge с влитыми популярными аддонами и ME-машинами.**

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1-E68C2D)
![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/license-Botania%20License-green)

</div>

Id мода `botania`. Требуется Applied Energistics 2.

## Влитые аддоны

Один jar вместо груды аддонов:

| Аддон | Пакет |
| --- | --- |
| Applied Botanics | `vazkii/botania/ae2/` |
| Botanical Machinery Extra | `net/lmor/botanicalextramachinery/` |
| Voltany | `com/jykito/voltany/` |
| Garden of Glass | `garden_of_glass/` (датапак скайблока) |

## Что добавляет форк

### ME-машины для рецептов
Крафтовые станции Botania работают как узлы сети AE2, так что ME-сеть сама подаёт в них ресурсы и забирает результат.

### Ячейки маны
ME-ячейки хранят **1000 маны на байт**: ячейка 1k — ровно один полный бассейн маны. Если установлен MEGA Cells,
появляются ячейки 1M–256M и портативные ячейки: их крафтят из корпуса ячейки маны и компонента MEGA-ячейки.
Собственные ячейки маны MEGA Cells выключены, потому что они ищут отдельный мод Applied Botanics.

### Симулятор Гайи
Воспроизводит бой с Хранителем Гайи в сложном режиме без арены (`vazkii/botania/gaiasim/`).

- **Мультиблок:** плита 3×3 из блоков элементиума, на ней основание 3×3 (блок драконита в центре, малахитовые
  блоки по сторонам, блоки террастали по углам). Симулятор ставится в центр, пилоны Гайи — по углам.
- **Цена:** 1 000 000 маны (распределитель или искра сверху) и 10 000 FE/t в течение 5 минут.
- **Награда:** бросок таблицы головы Хранителя и награда за одиночную победу в сложном режиме. Реликвии
  привязываются к тому, кто поставил симулятор.
- **Лексикон:** есть запись в разделе Alfhomancy со страницей с визуализацией мультиблока.

## Сборка

Нужна Java 21.

```sh
./gradlew build
```

## Благодарности

- **Botania** и **Garden of Glass** создали **Vazkii** и участники проекта Botania.
- **Applied Botanics** создал **ramidzkh**.
- **Botanical Machinery Extra** создал **LMor**.
- **Voltany** создал **jykito**.

Это неофициальный форк, он не связан с оригинальными авторами и не одобрен ими.

## Лицензия

Botania распространяется по [лицензии Botania](LICENSE.txt), и этот форк тоже.
Части, импортированные из других проектов, сохраняют свои лицензии, см. [ALTERNATE_LICENSES.txt](ALTERNATE_LICENSES.txt).
