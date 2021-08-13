/* -*- mode: java; c-basic-offset: 4 -*- */
// (setq indent-tabs-mode nil tab-width 4 c-basic-offset 4)
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.ArrayList;

public class testDES 
{
    public static int entryOrder = 0;
    public static class Message<Kind> implements Comparable<Message<Kind>> {
        public boolean active;
        public double time;
        public double sendingTime;
        public Kind kind;
	public int entryOrder;
        public Message(double time, double sendingTime, Kind kind) {
            this.active = true;
            this.time = time;
            this.sendingTime = sendingTime;
            this.kind = kind;
	    this.entryOrder = entryOrder++;
        }
        public int compareTo(Message<Kind> other) {
            return time < other.time ? -1 :
		(time == other.time && entryOrder < other.entryOrder) ? -1 : 1;
	    // no equality
        }
	public boolean isActive() { return this.active; }
        public void cancel() { this.active = false; }
    }
	interface Predicate<T> {
	    public boolean apply(T object);
	}
    abstract public static class DiscreteEventSimulation<Kind> extends PriorityQueue<Message<Kind>> {
        boolean running = true;
        double time = 0.0;
	double previousEventTime = 0.0;
        abstract void init();
        abstract void handleMessage(Message<Kind> msg);
        void scheduleAt(Message<Kind> msg) { this.add(msg); }
        void scheduleAt(double time, Kind kind) {
            this.scheduleAt(new Message<Kind>(time, now(), kind));
        }
	double now() { return this.time; }
        void cancelIf(Predicate<Message<Kind>> predicate) {
            for (Message<Kind> msg : this)
                if (predicate.apply(msg))
                    msg.cancel();
        }
        public void cancelKind(Kind kind) {
            for (Message<Kind> msg : this)
                if (msg.kind == kind)
                    msg.cancel();
        }
	double rgompertz(Random gen, double shape, double rate) {
	    double u = gen.nextDouble();
	    return (shape < 0.0 && u<Math.exp(rate/shape)) ? Double.POSITIVE_INFINITY :
		Math.log(1.0 - shape*Math.log(u)/rate)/shape;
	}
	double rllogis(Random gen, double shape, double scale) {
	    double u = gen.nextDouble();
	    return scale*Math.exp(-Math.log(1.0/u-1.0)/shape);
	}
	double rllogiscure(Random gen, double cure, double shape, double scale) {
	    return (gen.nextDouble() < cure) ? Double.POSITIVE_INFINITY : rllogis(gen, shape, scale);
	}
	double discountInterval(double y, double start, double finish, double dr) {
	    double duration = finish - start;
	    return dr<=0.0 || duration==0.0 ? y*duration :
		y / Math.pow(1.0 + dr, start) / Math.log(1.0+dr) *
		(1.0 - 1.0 / Math.pow(1.0 + dr, duration));
	}
	double discountPoint(double y, double time, double dr) {
	    return dr<=0.0 || time==0.0 ? y : y / Math.pow(1.0+dr, time);
	}
        public void stop() { running = false; }
        void run() {
            this.running = true;
            this.time = 0.0;
	    this.previousEventTime = 0.0;

            this.init();
            while (this.size() != 0 && this.running)
                {
                    Message<Kind> msg = this.remove();
                    if (msg.isActive()) {
			this.time = msg.time;
                        this.handleMessage(msg);
			this.previousEventTime = this.time;
		    }
                }
            this.clear();
        }
	void runMany(int n) {
	    for(int i=0; i<n; i++)
		run();
	}
    }
    public enum States {RecurrenceFree, AdjuvantTreatment,Recurrence, Death};
    public enum Arms {Observation, Lev, Lev_5FU};
    public enum Events {toRecurrenceFree, toAdjuvantTreatment, toToxicity, toRecurrence, toDeath, toEOF};
    public static class ColonModel extends DiscreteEventSimulation<Events> {
        boolean logging = false;
	Param param;
	Arms arm;
	States state;
	int AdjuvantCycles, Toxicities, id;
	double cost, utility; // current values
	// reporting
	double ECosts, EUtilities;
	ArrayList<Double> report_ECosts;
	ArrayList<Double> report_EUtilities;
	Random gen;
	boolean debug = false;
	public class Param {
	    double dr_costs  = 0.04;
	    double dr_health = 0.015;
	    // Gompertz distribution for death due to other causes
	    double d_BS_shape = 0.08845719;
	    double d_BS_rate  = 0.008098087;
	    // Recurrence-Free Survival / Adjuvant Treatment
	    // Log-logistic cure distribution for recurrence-free survival (RFS)
	    double[] d_RFS_cure = {0.397233983474699, 0.405557830617764, 0.57245177440411};
	    double[] d_RFS_shape = {1.65402431099951, 1.65402431099951, 1.65402431099951};
	    double[] d_RFS_scale = {1.12846223420542, 1.11939525018453, 1.31649151798288};
	    // Adjuvant treatment costs
	    double c_adjuvant_cycle_1 = 5000.0;
	    double c_adjuvant_cycle_2 = 10000.0;
	    // Utility during adjuvant treatment
	    double u_adjuvant = 0.70;
	    // Other adjuvant treatment parameters
	    double t_adjuvant_cycle      = 3.0/52.0; // in years
	    int n_max_adjuvant_cycles = 10;
	    // Probability of toxicities during a cycle
	    double p_tox_1 = 0.20;
	    double p_tox_2 = 0.40;
	    // Costs of toxicities
	    double c_tox = 2000;
	    // Disutility of toxicities
	    double u_dis_tox = 0.10;
	    // Long-term Follow-up
	    // Utility while free of recurrence
	    double u_diseasefree = 0.80;
	    // Recurrence of Disease
	    // Gompertz distribution for cancer-specific death
	    double[] d_CSS_shape = {1.52146766208046, 1.48534146614485, 1.4483150121553};
	    double[] d_CSS_scale = {1.15749303118087, 1.00945645458403, 0.811007356583923};
	    // Cost of treatment advanced disease
	    double c_advanced = 40000;
	    // Utility during advanced disease
	    double u_advanced = 0.60;
	}
	public ColonModel(Arms arm_in) {
	    this.param = new Param();
	    this.arm = arm_in;
	    this.gen = new Random(12345);
	    report_ECosts = new ArrayList<Double>();
	    report_EUtilities = new ArrayList<Double>();
	    this.id = -1;
	}
	void setDebug(boolean debug) {
	    this.debug = debug;
	}
	void addPointCost(double cost) {
	    ECosts += discountPoint(cost, now(), param.dr_costs);
	}
	@Override
	void init() {
	    // state is an inherited attribute from cProcessWithReport used for reporting
	    id++;
	    state = States.RecurrenceFree; 
	    AdjuvantCycles = 0;
	    Toxicities=0;
	    ECosts = 0.0;
	    EUtilities = 0.0;
	    utility = param.u_diseasefree;
	    cost = 0.0; // interval costs (cf. point costs)
	    // scheduleAt(double,short) is used to put events into the queue
	    scheduleAt(rgompertz(gen,param.d_BS_shape, param.d_BS_rate), Events.toDeath);
	    if (arm != Arms.Observation)
		scheduleAt(now(),Events.toAdjuvantTreatment);
	    double RFS = rllogiscure(gen,
				     param.d_RFS_cure[arm.ordinal()],
				     param.d_RFS_shape[arm.ordinal()], 
				     param.d_RFS_scale[arm.ordinal()]);
	    if (RFS != Double.POSITIVE_INFINITY)
		scheduleAt(RFS, Events.toRecurrence);
	    scheduleAt(55.0, Events.toEOF);
	}
	@Override
	void handleMessage(Message<Events> msg) {
	    // reporting
	    ECosts += discountInterval(cost, previousEventTime, now(), param.dr_costs);
	    EUtilities += discountInterval(utility, previousEventTime, now(), param.dr_health);
	    if (this.debug) {
		String txt = String.format("id=%d, time=%f, kind=%d", id, msg.time, msg.kind.ordinal());
	    	System.out.println(txt);
	    }
	    switch(msg.kind) {
	    case toRecurrenceFree:
		state = States.RecurrenceFree;
		utility = param.u_diseasefree;
		break;
	    case toAdjuvantTreatment: {
		state = States.AdjuvantTreatment;
		AdjuvantCycles++;
		double p = (arm == Arms.Lev) ? param.p_tox_1 : param.p_tox_2;
		if (p < gen.nextDouble())
		    scheduleAt(now(), Events.toToxicity);
		utility = param.u_adjuvant;
		addPointCost((arm == Arms.Lev ? param.c_adjuvant_cycle_1 : param.c_adjuvant_cycle_2));
		scheduleAt(now() + param.t_adjuvant_cycle,
			   (AdjuvantCycles < param.n_max_adjuvant_cycles) ? Events.toAdjuvantTreatment :
			   Events.toRecurrenceFree);
	    } break;
	    case toToxicity:
		Toxicities++;
		addPointCost(param.c_tox);
		utility = param.u_adjuvant - param.u_dis_tox;
		break;
	    case toRecurrence: {
		state = States.Recurrence;
		cancelKind(Events.toAdjuvantTreatment);
		cancelKind(Events.toRecurrenceFree);
		// cancelKind(Events.toDeath);
		utility = param.u_advanced;
		addPointCost(param.c_advanced);
		double CSS = rllogis(gen,
				     param.d_CSS_shape[arm.ordinal()],
				     param.d_CSS_scale[arm.ordinal()]);
		scheduleAt(now() + CSS, Events.toDeath);
	    } break;
	    case toDeath:
	    case toEOF:
		report_ECosts.add(Double.valueOf(ECosts));
		report_EUtilities.add(Double.valueOf(EUtilities));
		this.clear();
		break;
	    default:
		System.out.println("Invalid kind of event: " + msg.kind);
		break;
	    }
	}
	double mean(ArrayList<Double> v) {
	    return v.stream().mapToDouble(a -> a).sum()/v.size();
	}
	double sd(ArrayList<Double> v) {
	    double meanv = mean(v);
	    double total = v.stream().mapToDouble(a -> Math.pow(a-meanv,2)).sum();
	    return Math.sqrt(total/(v.size()-1));
	}
	double se(ArrayList<Double> v) {
	    return sd(v)/Math.sqrt(v.size());
	}
	ArrayList<Double> getCosts() {
	    return report_ECosts;
	}
	ArrayList<Double> getQALYs() {
	    return report_EUtilities;
	}
	double ICER(ColonModel model2) {
	    return (mean(report_ECosts) - mean(model2.getCosts())) /
		(mean(report_EUtilities) - mean(model2.getQALYs()));
	}
	void report() {
	    System.out.println("n           = " + report_ECosts.size());
	    System.out.println("Mean(costs) = " + mean(report_ECosts) +
			       "; se(costs) = " + se(report_ECosts));
	    System.out.println("Mean(QALYs) = " + mean(report_EUtilities) +
			       "; se(QALYs) = " + se(report_EUtilities));
	}
    }
    public static ColonModel sim;
    public static void runSimulation(int iarm, int n) 
    {
    	Arms arm = (iarm==0) ? Arms.Observation :
    	    (iarm==1) ? Arms.Lev : Arms.Lev_5FU;
    	sim = new ColonModel(arm);
    	sim.runMany(n);
    }
    public static double getMeanECosts()
    {
    	return sim.mean(sim.report_ECosts);
    }
    public static double getMeanEQALYs()
    {
    	return sim.mean(sim.report_EUtilities);
    }
    public static double[] getEUtilities()
    {
    	return sim.report_EUtilities.stream().mapToDouble(d -> d).toArray();
    }
    public static double[] getECosts()
    {
    	return sim.report_ECosts.stream().mapToDouble(d -> d).toArray();
    }
    public static void main(String[] args) throws InterruptedException
    {
	boolean debug = false;
	int n = debug ? 10 : 1000000;
	System.out.println("Observation:");
	ColonModel sim1 = new ColonModel(Arms.Observation);
	sim1.setDebug(debug);
	sim1.runMany(n);
	sim1.report();
	System.out.println("Lev:");
	ColonModel sim2 = new ColonModel(Arms.Lev);
	sim2.setDebug(debug);
	sim2.runMany(n);
	sim2.report();
	System.out.println("Lev+5FU:");
	ColonModel sim3 = new ColonModel(Arms.Lev_5FU);
	sim3.setDebug(debug);
	sim3.runMany(n);
	sim3.report();
	System.out.println("ICER(Lev vs Observation): " + sim2.ICER(sim1));
	System.out.println("ICER(Lev+5FU vs Observation): " + sim3.ICER(sim1));
	System.out.println("ICER(Lev+5FU vs Lev): " + sim3.ICER(sim2));
    }
}

