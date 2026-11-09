

//TIP 要<b>运行</b>代码，请按 <shortcut actionId="Run"/> 或
// 点击装订区域中的 <icon src="AllIcons.Actions.Execute"/> 图标。
public class Main {
    public static void main(String[] args) {
        long start=System.nanoTime();
        int ed=750;
        for (int i =1;i<=ed;i++){
            double random=Math.random();
            result result=calLib.getResult(530+700*random,30,10,320,0.99,0.05,0.005);
            print(i);
            print(Math.toDegrees(result.getPitchHigh()));

        }
        long end =System.nanoTime();
        print((end-start)*Math.pow(10,-9)/ed+" Seconds");
        print((end-start)*Math.pow(10,-9)+" Seconds");


    }
    public static <l> void print(l thing){
        System.out.println(thing);
    }
}