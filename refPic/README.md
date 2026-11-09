# Reference captures

In-game NBT snapshots of a Create Big Cannons **high-explosive shell**, taken with the
F3 entity inspector one tick apart. These are the raw samples behind the calibration and
the first-tick investigation described in the [project README](../README.md).

## Capture conditions

| item | value |
|---|---|
| projectile | HE shell, `ProjectileMass: 2.0f`, `Damage: 30.0f` |
| propellant charge | identical for both shots |
| barrel length | identical for both shots |
| shots | one level shot (fired along $-X$), one vertical shot (straight up) |
| samples | 3 per shot: tick 0 (just spawned, **not yet moved**), tick 1, tick 2 |
| source | F3 entity inspector (Chinese client), raw NBT field values |

## Naming convention

Every quantity comes in two variants:

- **without `m`** — a **position**, i.e. the `Pos` NBT tag,
- **with `m`** — a **motion**, i.e. the `Motion` NBT tag (velocity in blocks/tick).

The trailing `1` / `2` is the tick index.

| name | meaning |
|---|---|
| `xh, yh, zh` | position, horizontal shot, tick 0 |
| `xhm, yhm, zhm` | motion, horizontal shot, tick 0 |
| `xv, yv, zv` | position, vertical shot, tick 0 |
| `xvm, yvm, zvm` | motion, vertical shot, tick 0 |
| `xh1 … zhm2` | same fields at tick 1 and tick 2 |

All values are copied verbatim into `src/recordAndVerify.py`.

## Index

| # | file | shot | tick | Pos (x, y, z) | Motion (x, y, z) |
|---|---|---|---|---|---|
| 1 | `屏幕截图 2026-11-10 014835.png` | horizontal | 0 | `-890.490048, 39.474725, 2312.499402` | `-1.980048218, -0.050274025, -5.944995e-4` |
| 2 | `屏幕截图 2026-11-10 014922.png` | vertical | 0 | `-885.499561, 44.465089, 2314.499467` | `4.372568e-4, 1.930088420, -5.299825e-4` |
| 3 | `屏幕截图 2026-11-10 015206.png` | horizontal | 1 | `-892.460196, 39.399702, 2312.498811` | `-1.960247774, -0.099771285, -5.885545e-4` |
| 4 | `屏幕截图 2026-11-10 015241.png` | vertical | 1 | `-885.499125, 46.360527, 2314.498950` | `4.328842e-4, 1.860787535, -5.246827e-4` |
| 5 | `屏幕截图 2026-11-10 022739.png` | horizontal | 2 | `-894.410643, 39.275430, 2312.498225` | `-1.940645259, -0.148773572, -5.826689e-4` |
| 6 | `屏幕截图 2026-11-10 023041.png` | vertical | 2 | `-885.498695, 48.187010, 2314.498418` | `4.285554e-4, 1.792179660, -5.194359e-4` |

Full-precision values for every field live in `src/recordAndVerify.py`.

## Gallery

**horizontal shot — tick 0**

![horizontal shot, tick 0](<屏幕截图 2026-11-10 014835.png>)

**vertical shot — tick 0**

![vertical shot, tick 0](<屏幕截图 2026-11-10 014922.png>)

**horizontal shot — tick 1**

![horizontal shot, tick 1](<屏幕截图 2026-11-10 015206.png>)

**vertical shot — tick 1**

![vertical shot, tick 1](<屏幕截图 2026-11-10 015241.png>)

**horizontal shot — tick 2**

![horizontal shot, tick 2](<屏幕截图 2026-11-10 022739.png>)

**vertical shot — tick 2**

![vertical shot, tick 2](<屏幕截图 2026-11-10 023041.png>)


## What the samples show

**1. The velocity recurrence is exact.** With $d = 0.99$ and $g = 0.05$:

| relation | recorded |
|---|---|
| $v_{y,1} = 0.99\,v_{y,0} - 0.05$ (horizontal) | $-0.0997712852356408$ |
| $v_{y,2} = 0.99\,v_{y,1} - 0.05$ | $-0.1487735723832844$ |
| $v_{y,1} = 0.99\,v_{y,0} - 0.05$ (vertical) | $1.8607875353551253$ |

Both match the recorded `Motion` values with zero error in double precision.

**2. The displacement is the average of the two velocities.**

$$
\Delta \vec p_{n \to n+1} = \frac{\vec v_n + \vec v_{n+1}}{2}
$$

- horizontal shot, ticks 0→1: measured $1.970148067$, mean velocity $1.970148085$ (residual $-1.8\times10^{-8}$),
- vertical component of the same step: measured $-0.075022655$, mean $-0.075022655$ (residual $1.2\times10^{-15}$),
- vertical shot, ticks 0→1: measured $1.895437977$, mean $1.895437977$ (residual $-8.1\times10^{-13}$).

**3. The tick-0 motion does not match the tick-0 rotation.** The entity `Rotation` of the
horizontal shot reads pitch $-0.0079°$, which would imply $v_y \approx -2.7\times10^{-4}$,
yet the recorded `Motion.y` is $-0.050274$ — about one full gravity step lower — while
`Motion.x` has not decayed yet ($1.980048$, not $1.96$).

This asymmetry is the one open question in the calibration: the integration *rule* is
consistent across all ticks, but the spawn-time velocity is not what the entity orientation
suggests. See the "Open Problems" section of the [project README](../README.md).

## Caveats

- One run per configuration; no statistics, no null hypothesis. Treat the recurrence as
  evidence, not as a law — it should be re-measured with a longer capture before being
  baked into the solver.
- Positions are server-side doubles; the F3 readout rounds them for display, so the
  `.py` file (not the screenshots) is the authoritative copy.
- The vertical shot was fired straight up, so its $x/z$ motion is only numerical noise
  ($\sim 4\times10^{-4}$); it is still useful as a check that no horizontal impulse is added.
