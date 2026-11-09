public class result {
    private double pitchLow;
    private double timeLow;
    private double pitchHigh;
    private double timeHigh;
    public result( double pitchLow,double timeLow, double pitchHigh,double timeHigh) {
        this.pitchLow = pitchLow;
        this.timeLow = timeLow;
        this.pitchHigh = pitchHigh;
        this.timeHigh = timeHigh;
    }
    public result(){

    }


    public double getPitchLow(){
        return this.pitchLow;
    }

    public double getTimeLow() {
        return timeLow;
    }
    public double getPitchHigh() {
        return pitchHigh;
    }

    public double getTimeHigh() {
        return timeHigh;
    }

}