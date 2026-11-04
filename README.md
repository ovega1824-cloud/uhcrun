# UHC Run (Forge 1.21.1)

Mod de Forge 1.21.1 (Forge 52.1.0, Java 21) con las mecánicas de un UHC Run.

## Mecánicas

- **Cutclean**: los minerales (hierro, oro, cobre, ancient debris) sueltan el ítem ya fundido, y la comida de los mobs sale cocinada.
- **Borde que se cierra** por etapas, con barra de estado (bossbar), avisos de 1 min y 10 s, y zona final.
- **PvP desactivado** al inicio y que se activa a los X minutos.
- **Sin regeneración natural** (solo con manzanas doradas, pociones y cabezas doradas).
- **Vida visible** en la lista de jugadores (TAB) y bajo el nombre.
- **Dispersión** aleatoria de los jugadores al iniciar (los de un mismo `/team` salen juntos).
- **Cabeza dorada**: al morir se suelta tu cabeza; se craftea con 8 lingotes de oro + la cabeza (Regeneración II + Absorción).
- **Manzanas extra** al romper hojas y **pedernal extra** al romper grava.
- Al morir pasas a **espectador** (se sueltan tus cosas), y gana el último jugador o equipo vivo.

## Comandos (requieren permisos de operador)

| Comando | Qué hace |
|---|---|
| `/uhc start` | Inicia la partida. El centro del borde es donde estás parado. |
| `/uhc stop` | Termina la partida y restaura borde, PvP y regeneración. |
| `/uhc estado` | Muestra tiempo, vivos, PvP y tamaño del borde. |
| `/uhc cerrar` | Fuerza el siguiente cierre del borde ahora. |
| `/uhc config` | Lista las opciones. |
| `/uhc config <opcion> <valor>` | Cambia una opción (se guarda en `config/uhcrun.properties`). |

Para que alguien no participe, ponle el tag `uhc_espectador` (`/tag <jugador> add uhc_espectador`).

## Opciones principales

`borde_inicial` (2000), `borde_final` (50), `minutos_pvp` (10), `minutos_primer_cierre` (20),
`minutos_entre_cierres` (5), `segundos_cierre` (90), `porcentaje_cierre` (60),
`danio_borde` (2), `prob_manzana` (5), `pedernal` (1), `cutclean` (1),
`cabeza_dorada` (1), `dispersar` (1), `solo_en_partida` (0).

## Compilar en GitHub

Sube todo el contenido de esta carpeta (incluida `.github`) a un repositorio.
GitHub Actions compila solo y publica el `.jar` en **Releases** (y también en la pestaña **Actions → artifact**).
Copia el `.jar` a la carpeta `mods` de tu servidor o cliente con Forge 1.21.1.
