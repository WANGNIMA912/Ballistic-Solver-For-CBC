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
| mean solve time | **3.6 µs** |
| median / p99 | 3.6 µs / 5.0 µs |
| throughput | **~275,000 solves/s** |
| 10,000 random targets | **37 ms** |
| share of one 60 FPS frame | ~1/4,600 |
| vs. 40-step numeric simulation | **1.9× faster** |

Three bisections over closed-form expressions — the solver never steps the simulation, so the cost does not grow with flight time.

Measured with 10,000 random horizontal distances $H \in [530, 1230]$ blocks, $V = 30$, barrel `10`, `v = 320` blocks/s, `d = 0.99`, `g = 0.05`, tolerance `0.005`; JDK 25, single thread, after JIT warm-up. Reproduce with:

```bash
javac -d out src/*.java
java -cp out Benchmark
```

The bare `Main` loop, without explicit warm-up, reports ~3.8 µs per solve.

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

1. The projectile does not spawn at the centre of the last barrel block — there is an offset that must be modelled.
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

where `unknownFix = -2.5` is an empirical correction for the spawn offset (see §4.1).

The horizontal distance still to be covered is therefore $H - L\cos\theta$, which is substituted into $t(x)$ above.

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

Any elevation above this can never reach $H$, no matter how long it flies.

### 3.3 Step 2 — apex test (reachability)

For a fixed $H$, the landing height $V(\theta)$ is a **single-peaked** function of elevation: it rises to a maximum, then falls back. The peak is located by bisection on the sign of a finite-difference derivative (step $0.005$ rad), comparing $V(\theta)$ with $V(\theta + 0.005)$.

If the peak height is below the target height, the target is unreachable and the solver returns a zeroed result immediately:

```java
if (dumb < vertical) return new result(0, 0, 0, 0);
```

This is what makes the remaining two bisections safe: on either side of the peak, $V(\theta)$ is monotonic.

### 3.4 Steps 3 and 4 — the two arcs

- **Low arc**: bisection on $[\theta_{\min},\, \theta_{\text{apex}}]$, where $V(\theta)$ is increasing.
- **High arc**: bisection on $[\theta_{\text{apex}},\, \theta_{\max}]$, where $V(\theta)$ is decreasing.

Both use the same residual:

$$
\text{cannon}(\theta) = L\sin\theta + y\big(t(\theta)\big) - V
$$

and stop when $|\text{cannon} - V| < \texttt{precise}$ (default `0.005`) or after 100 iterations.

The result object carries both solutions and both flight times:

```java
result r = calLib.getResult(H, V, barrel, speed, drag, gravity, precise);
r.getPitchLow();  r.getTimeLow();    // flat arc
r.getPitchHigh(); r.getTimeHigh();   // lobbed arc
```

All three bisections together need on the order of a few hundred transcendental evaluations — sub-millisecond per call, which is why `Main` can loop it 750 times as a smoke test.

---

## 4. Observed Problems In-Game

### 4.1 Spawn offset (`unknownFix`) — unresolved

The projectile does not spawn at the centre of the last barrel block; its spawn position is offset by an amount that is not exposed anywhere in-game.

The value used is `unknownFix = -2.5`, i.e. an effective barrel length of $L = \texttt{barrel} - 2.5$. This is roughly consistent with the known CBC spawn behaviour of backing the projectile off by ~2 blocks from the muzzle, plus half a block of centring, but it has **not** been derived — it is fitted.

Consequence: the model is systematically off by a constant amount that is independent of range, and the solver inherits that error.

> **Status: unresolved.** See `refPic/` for the in-game entity dumps used while trying to pin it down.

### 4.2 Suspicion: the first tick is special

Because the residual was not explained by the spawn offset alone, the next suspect was the **first tick**. The hypothesis was that the projectile's very first movement step is computed differently from all later steps (for example, not yet having a previous velocity to average with).

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

### 4.3 What the recordings actually show

This resolves the first-tick question.

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

So the integration rule is **identical for the first tick and for every later tick**. The apparent anomaly has a simpler cause: the tick-0 velocity is not the result of a perfectly level shot.

Its magnitude is $\lvert v_0\rvert = \sqrt{1.980048^2 + 0.050274^2} \approx 1.980687$ and its direction is about $-1.45°$ below horizontal — which is why its vertical component happens to sit near $-g$ (here $-1.0055\,g$). The horizontal component is *not* decayed yet, which confirms that this is the initial state and not a state that has already been integrated once.

Because the first tick's displacement is the average of that velocity and the next one, it looks like neither $v_0$ nor $v_1$ — it lands exactly in between, which is what made it look like a separate rule.

In other words the first displacement is not "speed times one tick"; it is $(v_0 + v_1)/2$, and it always has been.

### 4.4 Consequence for the solver

The closed forms in §2 advance the position with the *new* velocity (`pos += v_new`), whereas the game uses the trapezoidal step above. For the horizontal axis the two differ by a constant factor

$$
\frac{(1+d)/2}{d} = \frac{1.99}{1.98} \approx 1.00505
$$

which over a 100-tick flight is a few blocks of range — enough to matter for precision fire, and one more candidate contributor to the residual that `unknownFix` is currently absorbing.

On the vertical axis the closed form and the step-by-step recurrence differ by exactly $g\,t$ (the closed form predicts a height that is higher by one gravity step per tick), so the currently implemented vertical prediction is optimistic by that amount. This has not yet been folded back into the solver.

---

## 5. Open Problems

1. **`unknownFix` is still fitted, not derived.** The spawn rule needs to be read directly from the mod source or measured with a controlled harness (fixed barrel length, several distances, no terrain interaction).
2. **Tick-0 launch direction is not exactly level.** The recorded spawn velocity is about $1.9807$ blocks/tick at $-1.45°$, so its vertical component lands near $-g$ by coincidence rather than by rule. Whether this comes from mount pitch calibration, a spawn-time velocity adjustment, or just an imprecise "level" setup is still open. It affects the initial condition, not the integration rule.
3. **High-arc solutions are ill-conditioned.** Near the maximum range $x(t)$ flattens out, so a 0.5 % model mismatch can move the impact height by tens of blocks. The low arc is well behaved; the high arc needs either a better model or a range guard.
4. **No numerical guards.** If $H$ exceeds the asymptotic range, `acos` receives an argument outside $[-1, 1]$, returns `NaN`, and the first bisection loops forever (it has no iteration cap). A target at $H = 1600$ blocks with $v = 320$ blocks/s hangs the solver.

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
├── refPic/                   # in-game entity dumps used while debugging
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
