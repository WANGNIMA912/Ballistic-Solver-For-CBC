public class calLib {
    public static result getResult(double horizon, double vertical, double barrel, double velocity, double drag, double gravity, double precise) {
        double temem;
        velocity = velocity / 20;
        double mid;
        double unknownFix = -2.5;

        double IniLower = Math.atan(vertical / horizon);
        double IniUpper = Math.acos(horizon * (1 - drag) / ((barrel + unknownFix) - (barrel + unknownFix) * drag + velocity * drag));
//        System.out.println(Math.toDegrees(IniUpper));

        double pl;
        double tl;
        double ph;
        double th;

        temem = IniUpper;
        double thisUp;
        double thisLow;
        int test;
        double dumb;

        thisUp = IniUpper;
        thisLow = IniLower;
        test = 0;
        while (true) {
            mid = (thisUp + thisLow) / 2;
            double mid2 = mid + 0.005;
            double temp = (horizon - (barrel + unknownFix) * Math.cos(mid)) * (drag - 1);
            double temp2 = (horizon - (barrel + unknownFix) * Math.cos(mid2)) * (drag - 1);
            double time = Math.log(temp / (velocity * Math.cos(mid) * drag) + 1) / Math.log(drag);
            double time2 = Math.log(temp2 / (velocity * Math.cos(mid2) * drag) + 1) / Math.log(drag);
            double data = drag / (drag - 1) * (velocity * Math.sin(mid) * (Math.pow(drag, time) - 1) - gravity * (Math.pow(drag, time) - 1) / (drag - 1) + time * gravity) + (barrel + unknownFix) * Math.sin(mid);
            double data2 = drag / (drag - 1) * (velocity * Math.sin(mid2) * (Math.pow(drag, time2) - 1) - gravity * (Math.pow(drag, time2) - 1) / (drag - 1) + time2 * gravity) + (barrel + unknownFix) * Math.sin(mid2);
            if ((data2 <= data && Math.abs(data2 - data) < precise) || data == data2) {
                IniUpper = mid;
//                System.out.println("Secondary boundary attempted: " + test);
//                System.out.println(Math.toDegrees(IniUpper));
                dumb = data;
                break;
            } else if (data2 > data) {
                thisLow = mid;
                test++;
            } else {
                thisUp = mid;
                test++;
            }
        }
        if (dumb < vertical) {
            return new result(0, 0, 0, 0);
        }

        thisUp = IniUpper;
        thisLow = IniLower;
        test = 0;
        while (true) {
            mid = (thisUp + thisLow) / 2;
//            System.out.println(Math.toDegrees(mid));
            double temp = (horizon - (barrel + unknownFix) * Math.cos(mid)) * (drag - 1);
            double time = Math.log(temp / (velocity * Math.cos(mid) * drag) + 1) / Math.log(drag);
            double data = drag / (drag - 1) * (velocity * Math.sin(mid) * (Math.pow(drag, time) - 1) - gravity * (Math.pow(drag, time) - 1) / (drag - 1) + time * gravity);
            double cannon = (barrel + unknownFix) * Math.sin(mid) + data;
            if (Math.abs(cannon - vertical) < precise || cannon == vertical || test > 100) {
                pl = mid;
                tl = time;
//                System.out.println("Low pitch: " + Math.toDegrees(pl) + "deg\n" + "Time: " + time / 20 + "s");
//                System.out.println("Tried: " + test);
                break;
            } else if (cannon < vertical) {
//                System.out.println(cannon);
                thisLow = mid;
                test++;
            } else {
//                System.out.println(cannon);
                thisUp = mid;
                test++;
            }
        }

        thisUp = temem;
        thisLow = pl;
        test = 0;
        while (true) {
            mid = (thisUp + thisLow) / 2;
            double temp = (horizon - (barrel + unknownFix) * Math.cos(mid)) * (drag - 1);
            double time = Math.log(temp / (velocity * Math.cos(mid) * drag) + 1) / Math.log(drag);
            double data = drag / (drag - 1) * (velocity * Math.sin(mid) * (Math.pow(drag, time) - 1) - gravity * (Math.pow(drag, time) - 1) / (drag - 1) + time * gravity);
            double cannon = (barrel + unknownFix) * Math.sin(mid) + data;
            if (Math.abs(cannon - vertical) < precise || cannon == vertical || test > 100) {
                ph = mid;
                th = time;
//                System.out.println("High pitch: " + Math.toDegrees(ph) + "deg\n" + "Time: " + time / 20 + "s");
//                System.out.println("Tried: " + test);
                break;
            } else if (cannon > vertical) {
//                System.out.println(cannon);
                thisLow = mid;
                test++;
            } else {
                thisUp = mid;
                test++;
            }
        }
        return new result(pl, tl, ph, th);

    }


    public static double getDist(double v, double t, double d) {
        v = v / 20;
        return (v * d * (Math.pow(d, t) - 1) / (d - 1));
    }

    public static double getVertDist(double velocity, double time, double drag, double gravity) {
        velocity = velocity / 20;
        return drag / (drag - 1) * (velocity * (Math.pow(drag, time) - 1) - gravity * (Math.pow(drag, time) - 1) / (drag - 1) + time * gravity);
    }

    public static double getTick(double s, double d, double v) {
        v = v / 20;
        return Math.log(s * (d - 1) / (v * d) + 1) / Math.log(d);
    }
}