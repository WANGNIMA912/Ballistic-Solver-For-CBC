public class calLib {
    public static result getResult(double horizon, double vertical, double barrel, double velocity, double drag, double gravity, double precise) {
        velocity = velocity / 20;

        double mid;

        double unknownFix = 1;

        double IniLower = Math.atan(vertical / horizon);
        double IniUpper = Math.acos(horizon * (1 - drag) / ((barrel + unknownFix) - (barrel + unknownFix) * drag + velocity * drag));
//        System.out.println(Math.toDegrees(IniUpper));

        double pl;
        double tl;
        double ph;
        double th;

        double HighestPitch = IniUpper;
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
            // double temp = (horizon - (barrel + unknownFix) * Math.cos(mid)) * (drag - 1);
            double h = (horizon - (barrel + unknownFix) * Math.cos(mid));
            double h2 = (horizon - (barrel + unknownFix) * Math.cos(mid2));
            double temp = 2 * h * (drag - 1);
            double temp2 = 2 * h2 * (drag - 1);
            double component = (velocity * Math.cos(mid) * drag) * (1 + drag);
            double component2 = (velocity * Math.cos(mid2) * drag) * (1 + drag);
            double time = Math.log(temp / component + 1) / Math.log(drag);
            double time2 = Math.log(temp2 / component2 + 1) / Math.log(drag);
            
            double componentU0A = velocity * Math.sin(mid) * (drag + 1) - gravity;
            double componentU1A = drag * (Math.pow(drag, time) - 1);
            double componentD0A = drag - 1;
            double componentU0B = velocity * Math.sin(mid2) * (drag + 1) - gravity;
            double componentU1B = drag * (Math.pow(drag, time2) - 1);
            double componentD0B = drag - 1;
            
            double data = (componentU0A * componentU1A / 2 - gravity * (componentU1A / componentD0A - time)) / componentD0A;
            double data2 = (componentU0B * componentU1B / 2 - gravity * (componentU1B / componentD0B - time2)) / componentD0B;
            
            // double data3 = drag / (drag - 1) * (velocity * Math.sin(mid) * (Math.pow(drag, time) - 1) - gravity * (Math.pow(drag, time) - 1) / (drag - 1) + time * gravity) + (barrel + unknownFix) * Math.sin(mid);
            // System.out.println(data+" "+data3);
            // double data2 = drag / (drag - 1) * (velocity * Math.sin(mid2) * (Math.pow(drag, time2) - 1) - gravity * (Math.pow(drag, time2) - 1) / (drag - 1) + time2 * gravity) + (barrel + unknownFix) * Math.sin(mid2);
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

//        Low pitch calculation
        thisUp = IniUpper;
        thisLow = IniLower;
        test = 0;
        while (true) {
            mid = (thisUp + thisLow) / 2;
//            System.out.println(Math.toDegrees(mid));
            double h = (horizon - (barrel + unknownFix) * Math.cos(mid));
            double temp = 2 * h * (drag - 1);
            double component = (velocity * Math.cos(mid) * drag) * (1 + drag);
            double time = Math.log(temp / component + 1) / Math.log(drag);
            double componentU0 = velocity * Math.sin(mid) * (drag + 1) - gravity;
            double componentU1 = drag * (Math.pow(drag, time) - 1);
            double componentD0 = drag - 1;
            double data = (componentU0 * componentU1 / 2 - gravity * (componentU1 / componentD0 - time)) / componentD0;
            // double data = drag / (drag - 1) * (velocity * Math.sin(mid) * (Math.pow(drag, time) - 1) - gravity * (Math.pow(drag, time) - 1) / (drag - 1) + time * gravity);
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


//        High pitch calculation
        thisUp = HighestPitch;
        thisLow = pl;
        test = 0;
        while (true) {
            mid = (thisUp + thisLow) / 2;
            double h = (horizon - (barrel + unknownFix) * Math.cos(mid));
            double temp = 2 * h * (drag - 1);
            double component = (velocity * Math.cos(mid) * drag) * (1 + drag);
            double time = Math.log(temp / component + 1) / Math.log(drag);
            double componentU0 = velocity * Math.sin(mid) * (drag + 1) - gravity;
            double componentU1 = drag * (Math.pow(drag, time) - 1);
            double componentD0 = drag - 1;
            double data = (componentU0 * componentU1 / 2 - gravity * (componentU1 / componentD0 - time)) / componentD0;
            // double data2 = drag / (drag - 1) * (velocity * Math.sin(mid) * (Math.pow(drag, time) - 1) - gravity * (Math.pow(drag, time) - 1) / (drag - 1) + time * gravity);
            // System.out.println("data: " + (data-data2));
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