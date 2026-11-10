# BallisticCal

> A research log for a **closed-form, tick-discrete ballistic solver** built for
> [Create Big Cannons](https://github.com/Cannoneers-of-Create/CreateBigCannons) (Minecraft 1.21.1 / NeoForge).

The question this project tries to answer is simple to state and awkward to compute:

> *Given a target at horizontal distance $H$ and height difference $V$, at what elevation must the barrel be set?*

In-game this is normally solved by walking shots onto the target. The goal here is to compute it directly.

---

## Performance at a glance

A single solve costs a few microseconds — fast enough to recompute a whole firing table every tick.

| metric | value |
|---|---|
| mean solve time | **3.1 µs** |
| median / p99 | 3.1 µs / ~5 µs |
| throughput | **~315,000 solves/s** |
| 10,000 random targets | **31 ms** |
| share of one 60 FPS frame | ~1/5,400 |
| vs. 40-step numeric simulation | **2.2× faster** |

Three bisections over closed-form expressions — the solver never steps the simulation, so the cost does not grow with flight time.

Measured with 10,000 random horizontal distances $H \in [530, 1230]$ blocks, $V = 30$, barrel `10`, `v = 320` blocks/s, `d = 0.99`, `g = 0.05`, tolerance `0.005`; JDK 25, single thread, after JIT warm-up. Reproduce with:

```bash
javac -d out src/*.java
java -cp out Benchmark
```

The bare `Main` loop, without explicit warm-up, reports ~3.2 µs per solve.

> **How the model was cracked:** two unknowns fell to the same method — capturing the shell once per tick. It gave (a) the real displacement rule, **the average of the two tick velocities**, which forced a rewrite of the closed forms (§4.3); and (b) the real spawn point, **two blocks beyond the last barrel block**, which turned `unknownFix` from a fitted −2.5 into **1** (§4.1). Both results came from measurement, not from reading the mod's source. See the [capture log](refPic/README.md).

---

## 1. Research Background

Big cannon projectiles in CBC are **not** simulated with a closed-form parabola. They are integrated once per game tick:

- horizontal velocity is multiplied by a drag factor each tick,
- vertical velocity is multiplied by the same factor **and** reduced by a gravity constant,
- position is advanced once per tick.

The mechanism, stated in the form used throughout this project:

| axis | per-tick update |
|---|---|
| horizontal | $v_x \leftarrow v_x \cdot d$ |
| vertical | $v_y \leftarrow v_y \cdot d - g$ |

with the working values

$$
d = 0.99,\qquad g = 0.05\ \text{blocks/tick}^2
$$

and $20$ ticks per second (input speeds are given in blocks/s and divided by 20 internally).

Because velocity decays geometrically, the trajectory is **not** a parabola and has **no** simple inverse. Trial-and-error aiming is slow, wastes ammunition, and is unreliable at long range where the flight time is tens of seconds.

Two further complications make the problem interesting:

1. The projectile does not spawn at the centre of the last barrel block — there is an offset that has to be modelled (measured in §4.1).
2. For any reachable target there are generally **two** firing solutions (a flat one and a lobbed one), and both are wanted: the flat one for direct fire, the lobbed one for shooting over terrain.

---

## 2. Initial Numerical Model

### 2.1 Discrete motion law

Writing $v_0$ for the muzzle speed component and stepping tick by tick:

$$
v_{n} = d^{\,n} v_0 \qquad \text{(horizontal)}
$$

$$
v_{y,n} = d^{\,n} v_{y0} - g\,\frac{d^{\,n}-1}{d-1} \qquad \text{(vertical)}
$$

Both are geometric sequences; the vertical one is a geometric sequence with a constant forcing term, which is why it is described as a *mixed arithmetic / geometric* series.

### 2.2 Horizontal: "given speed and distance, return time"

Summing the geometric series $v_0 d,\; v_0 d^2,\; \dots,\; v_0 d^t$:

$$
x(t) = v_0\,\frac{d\,(d^{\,t}-1)}{d-1}
$$

Inverted for time:

$$
t(x) = \frac{\ln\!\left(1 + \dfrac{x\,(d-1)}{v_0\,d}\right)}{\ln d}
$$

### 2.3 Vertical: "given speed and time, return height"

Summing the velocity recurrence:

$$
y(t) = \frac{d}{d-1}\left[\,v_{y0}\,(d^{\,t}-1) - \frac{g\,(d^{\,t}-1)}{d-1} + g\,t\,\right]
$$

> **Note.** The forms in §2.2–§2.3 are the *first* version of the model. The implementation has since moved to the trapezoidal forms in §3.5 (see §4.3 for why). They are kept here because the derivation is part of the research story.

### 2.4 Closed forms as utilities

These two inversions are exposed directly in `calLib` as standalone helpers, because they are useful on their own (e.g. computing time-of-flight for a known distance):

| function | input | output |
|---|---|---|
| `getDist(v, t, d)` | initial speed, ticks | horizontal distance |
| `getVertDist(v, t, d, g)` | initial vertical speed, ticks | height reached |
| `getTick(s, d, v)` | distance, drag, speed | ticks to travel that distance |

With the solver these are mostly redundant, but they were the first thing written and they made the geometry checkable by hand.

---

## 3. Algorithm Design

### 3.1 Muzzle geometry

The projectile starts at

$$
(x_0,\, y_0) = \big(L\cos\theta,\; L\sin\theta\big),
\qquad L = \texttt{barrel} + \texttt{unknownFix}
$$

where `unknownFix = 1` reflects the measured spawn point — two block-widths past the centre of the last block (see §4.1).

The horizontal distance still to be covered is therefore $H - L\cos\theta$, which is substituted into $t(\theta)$ in §3.5.

### 3.2 Step 1 — firing bounds

- **Lower bound**: the straight line to the target, $\theta_{\min} = \arctan(V/H)$. No negative-gravity shells are modelled, so this is a hard floor.
- **Upper bound**: the elevation at which the *asymptotic* horizontal range still reaches $H$. Letting $t \to \infty$:

$$
x_{\infty}(\theta) = \left(L + \frac{v d}{1-d}\right)\cos\theta
$$

Requiring $x_{\infty} \ge H$ gives

$$
\theta_{\max} = \arccos\!\left(\frac{H\,(1-d)}{L\,(1-d) + v\,d}\right)
$$

Any elevation above this can never reach $H$, no matter how long it flies. The bound still uses the un-averaged range $vd/(1-d)$, so it is conservative by about 0.5 %.

### 3.3 Step 2 — apex test (reachability)

For a fixed $H$, the height the shell reaches **above the muzzle**, $y(\theta)$, is a **single-peaked** function of elevation: it rises to a maximum, then falls back. (The $L\sin\theta$ term is dropped in this test; that keeps the peak easy to compare against the target height.)

The peak is located by bisection on the sign of a finite-difference derivative (step $0.005$ rad), comparing $y(\theta)$ with $y(\theta + 0.005)$.

If the peak is below the target height, the target is unreachable and the solver returns a zeroed result immediately:

```java
if (dumb < vertical) return new result(0, 0, 0, 0);
```

This is what makes the remaining two bisections safe: on either side of the peak, $y(\theta)$ is monotonic.

### 3.4 Steps 3 and 4 — the two arcs, in that order

The two solutions are found **sequentially**, and the order is not interchangeable:

1. **Low arc first** — bisection on $[\theta_{\min},\, \theta_{\text{apex}}]$, where $y(\theta)$ is increasing.
2. **High arc second** — bisection on $[\,p_{\text{low}},\, \theta_{\max}]$, where $y(\theta)$ is decreasing. Its lower end is the *result* of step 1, not the apex angle.

```text
θ_min        θ_low        θ_apex        θ_high       θ_max
  |------------|--------------|------------|------------|
  ↑            ↑              ↑            ↑            ↑
straight     LOW ARC       peak height   HIGH ARC    asymptote
 line        result                      result
```

The high-arc search cannot start before $p_{\text{low}}$ exists, so **step 4 depends on step 3**. Sweeping the high arc independently (from the apex angle) would search a wider interval and risk landing on the wrong branch near the maximum range.

Both bisections use the same residual:

$$
\text{cannon}(\theta) = L\sin\theta + y\big(t(\theta)\big) - V
$$

and stop when $|\text{cannon} - V| < \texttt{precise}$ (default `0.005`) or after 100 iterations.

```java
// Low pitch
thisUp = IniUpper; thisLow = IniLower;
while (true) { ... if (cannon < vertical) thisLow = mid; else thisUp = mid; }

// High pitch -- starts from pl, the low-arc result
thisUp = HighestPitch; thisLow = pl;
while (true) { ... if (cannon > vertical) thisLow = mid; else thisUp = mid; }
```

### 3.5 The closed forms evaluated at each candidate

$$
t(\theta) = \frac{\ln\!\left(1 - \dfrac{2\,(H - L\cos\theta)(1-d)}{v\cos\theta\; d\,(1+d)}\right)}{\ln d}
$$

$$
y(t) = \frac{1}{d-1}\left[\frac{\big(v\sin\theta\,(1+d) - g\big)\, d\,(d^{t}-1)}{2} - g\left(\frac{d\,(d^{t}-1)}{d-1} - t\right)\right]
$$

These are the **trapezoidal** forms: the $(1+d)/2$ factor from §4.3 is folded into the series, and $v\sin\theta(1+d) - g$ is the vertical velocity after one update. At $t=1$ they collapse to the first-tick displacement derived in §4.3 — the same equation the game uses.

The result object carries both solutions and both flight times:

```java
result r = calLib.getResult(H, V, barrel, speed, drag, gravity, precise);
r.getPitchLow();  r.getTimeLow();    // flat arc
r.getPitchHigh(); r.getTimeHigh();   // lobbed arc
```

All three bisections together need on the order of a few hundred transcendental evaluations — about 3 µs per call, which is why `Main` can loop it 10,000 times as a smoke test.

---

## 4. Observed Problems In-Game

> **Raw captures:** [refPic/README.md](refPic/README.md) — the six F3 entity snapshots behind this section, indexed by shot and tick, with the full field values.
>
> **Breakthroughs in this section:** the same six frames overturned the "first tick is special" hypothesis and pinned down the exact integration rule (§4.3), and the same sampling located the spawn point (§4.1). Both came from measurement, not from reading the mod's source.

### 4.1 Spawn offset (`unknownFix`) — resolved by the same sampling

The projectile does not spawn at the centre of the last barrel block, and the offset is not exposed anywhere in-game. It used to be a fitted constant, `unknownFix = -2.5`, with no derivation behind it.

Tick-level sampling settled it. With the shell captured once per tick and the barrel geometry known, the spawn point lines up with a position **two block-widths past the centre of the last barrel block** — that is, about 1.5 blocks beyond its outer face. In the current parametrisation that is:

```java
double unknownFix = 1;   // was -2.5
```

(`barrel` counts block widths: $N$ blocks give `barrel = N`, while the centre-to-centre distance from the pivot to the last block is only $N-1$. The `+1` closes that counting gap and adds the remaining block, so $L = (N-1) + 2$.)

Consequence: the constant, range-independent bias is gone, and the solver no longer has to absorb it in a fudge factor.

> **Status: resolved.** Same method as §4.3: one capture per tick, and the arithmetic to tell the frames apart.

### 4.2 Suspicion: the first tick is special

At the time, the spawn offset alone could not explain the residual, so the next suspect was the **first tick**. The hypothesis was that the projectile's very first movement step is computed differently from all later steps (for example, not yet having a previous velocity to average with).

To test it, `src/recordAndVerify.py` stores raw samples taken in-game for two shots of the same shell, with the same propellant charge and the same barrel length:

- **horizontal shot** — barrel level, shell fired along $-X$,
- **vertical shot** — barrel pointing straight up.

Each shot has three samples, taken one tick apart. **Sample 0 is the freshly spawned projectile, before it has moved at all** — this matters, because the tick-0 position is the spawn position, not a post-integration position.

| sample | contents |
|---|---|
| `xh, yh, zh` | position (horizontal shot) |
| `xhm, yhm, zhm` | motion / delta-movement (horizontal shot) |
| `xv, yv, zv` | position (vertical shot) |
| `xvm, yvm, zvm` | motion / delta-movement (vertical shot) |

### 4.3 Breakthrough: the first tick is a trapezoid

This is the turning point of the project.

The first-tick rule was **not** read out of the mod's source code — it was recovered from six in-game frames plus a handful of arithmetic checks. The data was strong enough to overturn the original hypothesis outright: the first movement step is not special at all.

**Velocity recurrence** — holds to full double precision:

| relation | expected | recorded | error |
|---|---|---|---|
| $v_{y,1} = 0.99\,v_{y,0} - 0.05$ (horizontal shot) | `-0.0997712852356408` | `-0.0997712852356408` | `0.00e+0` |
| $v_{y,2} = 0.99\,v_{y,1} - 0.05$ | `-0.1487735723832844` | `-0.1487735723832844` | `0.00e+0` |
| $v_{y,1} = 0.99\,v_{y,0} - 0.05$ (vertical shot) | `1.8607875353551253` | `1.8607875353551253` | `0.00e+0` |
| $\lvert v_1\rvert / \lvert v_0\rvert$ (horizontal component) | `0.99` | `0.9900000189` | `~2e-8` |

**Displacement between consecutive samples** — this is the answer to the "first tick" question:

$$
\boxed{\;\Delta \vec{p}_{\,n \to n+1} \;=\; \frac{\vec v_n + \vec v_{n+1}}{2}\;}
$$

i.e. the distance moved is the **average of the velocity at the start and at the end of the tick** — trapezoidal integration, equivalent to `pos += v + 0.5 * a`.

Measured, horizontal shot:

| step | horizontal speed | $v_y$ | measured $\Delta p$ (horiz) | measured $\Delta p$ (y) | mean speed | mean $v_y$ | residual |
|---|---|---|---|---|---|---|---|
| 0 → 1 | 1.980048308 → 1.960247862 | -0.050274025 → -0.099771285 | 1.970148067 | -0.075022655 | 1.970148085 | -0.075022655 | -1.8e-8 / 1.2e-15 |
| 1 → 2 | 1.960247862 → 1.940645346 | -0.099771285 → -0.148773572 | 1.950446585 | -0.124272429 | 1.950446604 | -0.124272429 | -2.0e-8 / 5.0e-16 |

Measured, vertical shot:

| step | $v_y$ | measured $\Delta p$ (y) | mean $v_y$ | residual |
|---|---|---|---|---|
| 0 → 1 | 1.930088420 → 1.860787535 | 1.895437977 | 1.895437977 | -8.1e-13 |
| 1 → 2 | 1.860787535 → 1.792179660 | 1.826483598 | 1.826483598 | -2.2e-15 |

So the integration rule is **identical for the first tick and for every later tick**. What made the first tick look different is the *initial state*, not the rule.

The recorded tick-0 motion is $(-1.980048,\ -0.050274,\ -5.9\times10^{-4})$, while the same frame's entity `Rotation` reads pitch $-0.0079°$ — which would imply $v_y \approx -2.7\times10^{-4}$, two orders of magnitude away from the recorded vertical component.

Because the first tick's displacement is the average of that velocity and the next one, it looks like neither $v_0$ nor $v_1$ — it lands exactly in between, which is what made it look like a separate rule.

In other words the first displacement is not "speed times one tick"; it is $(v_0 + v_1)/2$, and it always has been.

Written in terms of the **launch** speed $v$ (before any update), the first-tick displacement is:

$$
\Delta p_x = v\,d\,\frac{1+d}{2},
\qquad
\Delta p_y = \frac{v d + (v d - g)\,d - 2g}{2}
$$

which is the same as $(v_0 + v_1)/2$ with $v_0 = vd - g$ and $v_1 = v_0 d - g$. Both forms reproduce the recorded displacement: horizontal $1.970148$ (the formula matches to six decimals; the measured value also includes a $z$ component of $\sim6\times10^{-4}$), vertical $1.895438$ with a residual of $8.8\times10^{-13}$.

**The tick-0 anomaly is a sampling artefact, not a model anomaly.** The recorded tick-0 `Motion` is exactly the launch velocity after *one* update, paired with a position that has not moved yet:

- launch $v_y = 2.0\sin(-0.0079°) = -2.76\times10^{-4}$ → one update gives $0.99(-2.76\times10^{-4}) - 0.05 = -0.050273$, matching the recorded $-0.050274$;
- launch $v_x \approx 2.0$ → one update gives $2.0\times0.99 = 1.9800$, matching the recorded $1.980048$.

So the velocity is stepped once before the first screenshot while the position is still at the spawn point. The integration rule never differed between ticks.

**Why this counts as a breakthrough.** Until this point the first tick had to be treated as a possible exception — an unexplained unknown sitting at the head of every trajectory, and a reason to distrust close-range solutions in particular. After it:

- the whole flight, tick 0 included, is governed by a single trapezoidal rule;
- the first-tick displacement now has a closed form (the boxed equations above) instead of being a special case;
- the same sampling settled the spawn point (§4.1), so the two quantities that used to be guesses — the displacement rule and the muzzle offset — are now both measured.

The method is worth noting as much as the result: no decompiler, no source reading — just six NBT snapshots taken one tick apart, and the arithmetic that tells them apart.

### 4.4 Consequence for the solver

The first version of the closed forms (§2) advanced the position with the *new* velocity (`pos += v_new`). That differed from the game by a constant factor

$$
\frac{(1+d)/2}{d} = \frac{1.99}{1.98} \approx 1.00505
$$

on the horizontal axis, and by one $g$ per tick on the vertical one — a few blocks of range and several blocks of height over a long shot.

The rewrite in §3.5 folds the trapezoid into the series, so that discrepancy is gone. Checked against a direct tick-by-tick simulation of the game's integrator, the closed forms agree to machine precision (about $10^{-14}$ after 100 ticks). The solver now computes the same trajectory the game does, not an approximation of it.

---

## 5. Open Problems

1. **High-arc solutions are ill-conditioned.** Near the maximum range $x(t)$ flattens out, so a 0.5 % model mismatch can move the impact height by tens of blocks. The low arc is well behaved; the high arc needs either a better model or a range guard.
2. **No numerical guards.** If $H$ exceeds the asymptotic range, `acos` receives an argument outside $[-1, 1]$, returns `NaN`, and the first bisection loops forever (it has no iteration cap). A target at $H = 1600$ blocks with $v = 320$ blocks/s hangs the solver.

---

## 6. Repository Layout

```text
BallisticCal/
├── src/
│   ├── calLib.java           # solver: bounds, apex test, two bisections
│   ├── result.java           # (pitchLow, timeLow, pitchHigh, timeHigh)
│   ├── res.java              # trivial subclass, unused
│   ├── Main.java             # 10,000-iteration smoke test
│   ├── Benchmark.java        # latency percentiles + numeric-search comparison
│   └── recordAndVerify.py    # raw in-game samples + quick arithmetic checks
├── refPic/                   # in-game entity dumps + capture log (refPic/README.md)
└── README.md
```

## 7. Running

```bash
javac -d out src/*.java
java -cp out Main        # 10,000-iteration smoke test
java -cp out Benchmark   # latency percentiles + comparison
```

For the sample checks:

```bash
python src/recordAndVerify.py
```

## 8. References

- Create Big Cannons — `rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile`, the per-tick integration this model reproduces.
- CBC projectile properties are data-driven (`munition_properties/projectiles/*.json`), where `drag` and `gravity` are the two numbers this project parameterises.
