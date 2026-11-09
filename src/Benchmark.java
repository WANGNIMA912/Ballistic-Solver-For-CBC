import java.util.*;

/**
 * Micro-benchmark for the closed-form solver.
 *
 * <p>Runs 10,000 solves over random horizontal distances in [530, 1230] blocks
 * (the range used by {@link Main}), reports mean / median / p90 / p99 latency,
 * and compares against a 40-step numeric search over a tick-by-tick simulation.
 *
 * <p>Run with: {@code java -cp out Benchmark}
 */
public class Benchmark {
    static final double D = 0.99, G = 0.05, VPS = 320.0, L0 = 10.0, V = 30.0, PREC = 0.005;

    /** Reference implementation: trapezoidal tick integration, then bisection on elevation. */
    static double simulateY(double theta, double H) {
        double v = VPS / 20, L = L0 - 2.5;
        double x = L * Math.cos(theta), y = L * Math.sin(theta);
        double vx = v * Math.cos(theta), vy = v * Math.sin(theta);
        for (int n = 0; n < 4000; n++) {
            double ax = -(1 - D) * vx, ay = -(1 - D) * vy - G;
            double nx = x + vx + 0.5 * ax, ny = y + vy + 0.5 * ay;
            vx += ax; vy += ay; x = nx; y = ny;
            if (x >= H) return y;
        }
        return y;
    }

    static double numericSolve(double H) {
        double lo = Math.atan(V / H), hi = Math.PI / 4;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (simulateY(mid, H) < V) lo = mid; else hi = mid;
        }
        return (lo + hi) / 2;
    }

    static void stats(String name, long[] t) {
        long[] s = t.clone();
        Arrays.sort(s);
        double mean = 0;
        for (long x : s) mean += x;
        mean /= s.length;
        System.out.printf(Locale.ROOT,
            "%-14s n=%-6d mean=%7.3f us  median=%7.3f us  p90=%7.3f us  p99=%7.3f us  max=%7.3f us%n",
            name, s.length, mean / 1000,
            s[s.length / 2] / 1000.0,
            s[(int) (s.length * 0.90)] / 1000.0,
            s[(int) (s.length * 0.99)] / 1000.0,
            s[s.length - 1] / 1000.0);
    }

    public static void main(String[] args) {
        int n = 10000;
        Random rnd = new Random(42);
        double[] H = new double[n];
        for (int i = 0; i < n; i++) H[i] = 530 + 700 * rnd.nextDouble();

        for (int i = 0; i < 30000; i++) calLib.getResult(H[i % n], V, L0, VPS, D, G, PREC);

        long[] closed = new long[n];
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            long s = System.nanoTime();
            calLib.getResult(H[i], V, L0, VPS, D, G, PREC);
            closed[i] = System.nanoTime() - s;
        }
        long totalClosed = System.nanoTime() - t0;

        int m = 2000;
        for (int i = 0; i < 100; i++) numericSolve(H[i % n]);
        long[] num = new long[m];
        for (int i = 0; i < m; i++) {
            long s = System.nanoTime();
            numericSolve(H[i]);
            num[i] = System.nanoTime() - s;
        }

        stats("closed-form", closed);
        stats("tick+numeric", num);
        System.out.printf(Locale.ROOT, "10,000 solves in %.3f ms  ->  %.0f solves/s%n",
            totalClosed / 1e6, 1e9 * n / totalClosed);

        double meanC = 0, meanN = 0;
        for (long x : closed) meanC += x;
        for (long x : num) meanN += x;
        meanC /= closed.length;
        meanN /= num.length;
        System.out.printf(Locale.ROOT, "speedup over numeric search = %.1fx%n", meanN / meanC);
    }
}
