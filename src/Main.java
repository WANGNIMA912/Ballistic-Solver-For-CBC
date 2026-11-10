public class Main {
    public static void main(String[] args) {
        int ed = 10000;
        long start = System.nanoTime();
        for (int i = 1; i <= ed; i++) {
            double random = Math.random();
            // result result=calLib.getResult(530+700*random,30,10,320,0.99,0.05,0.005);
            calLib.getResult(530 + 700 * random, 30, 10, 320, 0.99, 0.05, 0.005);
            // print(i);
            // print(Math.toDegrees(result.getPitchHigh()));
        }
        long end = System.nanoTime();
        print("Averge time spent: " + (end - start) * Math.pow(10, -9) / ed + " Seconds");
        print("Total time spent: " + (end - start) * Math.pow(10, -9) + " Seconds");


    }

    public static <l> void print(l thing) {
        System.out.println(thing);
    }
}