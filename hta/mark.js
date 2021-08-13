// Adapted from https://stackoverflow.com/questions/42919469/efficient-way-to-implement-priority-queue-in-javascript

const top = 0;
const parent = i => ((i + 1) >>> 1) - 1;
const left = i => (i << 1) + 1;
const right = i => (i + 1) << 1;

class PriorityQueue {
    constructor(comparator = (a, b) => a > b) {
	this._heap = [];
	this._comparator = comparator;
    }
    size() {
	return this._heap.length;
    }
    isEmpty() {
	return this.size() == 0;
    }
    peek() {
	return this._heap[top];
    }
    push(...values) {
	values.forEach(value => {
	    this._heap.push(value);
	    this._siftUp();
	});
	return this.size();
    }
    pop() {
	const poppedValue = this.peek();
	const bottom = this.size() - 1;
	if (bottom > top) {
	    this._swap(top, bottom);
	}
	this._heap.pop();
	this._siftDown();
	return poppedValue;
    }
    replace(value) {
	const replacedValue = this.peek();
	this._heap[top] = value;
	this._siftDown();
	return replacedValue;
    }
    // new method
    clear() {
	this._heap = [];
    }
    _greater(i, j) {
	return this._comparator(this._heap[i], this._heap[j]);
    }
    _swap(i, j) {
	[this._heap[i], this._heap[j]] = [this._heap[j], this._heap[i]];
    }
    _siftUp() {
	let node = this.size() - 1;
	while (node > top && this._greater(node, parent(node))) {
	    this._swap(node, parent(node));
	    node = parent(node);
	}
    }
    _siftDown() {
	let node = top;
	while (
	    (left(node) < this.size() && this._greater(left(node), node)) ||
		(right(node) < this.size() && this._greater(right(node), node))
	) {
	    let maxChild = (right(node) < this.size() && this._greater(right(node), left(node))) ? right(node) : left(node);
	    this._swap(node, maxChild);
	    node = maxChild;
	}
    }
}

class Message {
    constructor(time, kind, entryOrder, active) {
	this.time = time;
	this.kind = kind;
	this.entryOrder = entryOrder;
	this.active = active;
    }
}

class DiscreteEventSimulation extends PriorityQueue {
    constructor(comparator = (a, b) => (a.time < b.time) ||
		(a.time == b.time && a.entryOrder < b.entryOrder)) {
	super(comparator);
	this.entryOrder = 0;
	this.time = 0;
	this.previousEventTime = 0;
    }
    scheduleAt(time, kind) {
	this.push(new Message(time, kind, this.entryOrder, true));
	this.entryOrder += 1;
    }
    cancel(predicate) {
	var i;
	for (i=0; i<this.size(); i++) {
	    if (predicate(this._heap[i])) {
		this._heap[i].active = false;
	    }
	}
    }
    cancelKind(kind) {
	this.cancel(msg => msg.kind == kind);
    }
    init () { } // abstract
    handleMessage (msg) { } // abstract
    run() {
	this.clear();
	this.init();
	this.time = 0;
	this.previousEventTime = 0;
	while(!this.isEmpty()) {
	    let msg = this.pop();
	    if (msg.active) {
		this.time = msg.time;
		this.handleMessage(msg);
		this.previousEventTime = this.time;
	    }
	}
    }
    now() { return this.time; }
    runMany(n) {
	var i;
	for (i=0; i<n; i++) {
	    this.run();
	}
    }
}

class DESexample1 extends DiscreteEventSimulation {
    init() {
	this.scheduleAt(3, "Clear drains");
	this.scheduleAt(4, "Feed cat");
	this.scheduleAt(5, "Make tea");
	this.scheduleAt(1, "Solve RC tasks");
	this.scheduleAt(2, "Tax return");
	this.cancelKind("Make tea");
    }
    handleMessage(msg) {
	console.log(msg);
    }
}
//const des1 = new DESexample1();
//des1.run();

const param = {
    dr_costs : 0.04,
    dr_health: 0.015,
    // Gompertz distribution for death due to other causes
    d_BS_shape: 0.08845719,
    d_BS_rate : 0.008098087,
    // Recurrence-Free Survival / Adjuvant Treatment
    // Log-logistic cure distribution for recurrence-free survival (RFS)
    d_RFS_cure: [0.397233983474699, 0.405557830617764, 0.57245177440411],
    d_RFS_shape: [1.65402431099951, 1.65402431099951, 1.65402431099951],
    d_RFS_scale: [1.12846223420542, 1.11939525018453, 1.31649151798288],
    // Adjuvant treatment costs
    c_adjuvant_cycle_1: 5000.0,
    c_adjuvant_cycle_2: 10000.0,
    // Utility during adjuvant treatment
    u_adjuvant: 0.70,
    // Other adjuvant treatment parameters
    t_adjuvant_cycle     : 3.0/52.0, // in years
    n_max_adjuvant_cycles: 10,
    // Probability of toxicities during a cycle
    p_tox_1: 0.20,
    p_tox_2: 0.40,
    // Costs of toxicities
    c_tox: 2000,
    // Disutility of toxicities
    u_dis_tox: 0.10,
    // Long-term Follow-up
    // Utility while free of recurrence
    u_diseasefree: 0.80,
    // Recurrence of Disease
    // Gompertz distribution for cancer-specific death
    d_CSS_shape: [1.52146766208046, 1.48534146614485, 1.4483150121553],
    d_CSS_scale: [1.15749303118087, 1.00945645458403, 0.811007356583923],
    // Cost of treatment advanced disease
    c_advanced: 40000,
    // Utility during advanced disease
    u_advanced: 0.60
};

const States = {RecurrenceFree: 1, AdjuvantTreatment: 2,
		Recurrence: 3,Death: 4};
const Arms = {Observation: 0, Lev: 1, Lev_5FU: 2};
const Events = {toRecurrenceFree: 1, toAdjuvantTreatment: 2, toToxicity: 3, toRecurrence: 4,
	      toDeath: 5, toEOF: 6};

class ColonModel extends DiscreteEventSimulation {
    constructor(arm = Arms.Observation, debug=false) {
	super();
	this.arm = arm;
	this.state = States.RecurrenceFree;
	this.AdjuvantCycles = 0;
	this.Toxicities = 0;
	this.report_ECosts = [];
	this.report_EUtilities = [];
	this.cost = 0.0;
	this.ECosts = 0.0;
	this.EUtilities = 0.0;
	this.utility = 0.0;
	this.debug = debug;
    }
    rgompertz(shape, rate) {
	var u = Math.random();
	return (shape < 0.0 && u<Math.exp(rate/shape)) ? Infinity :
	    Math.log(1.0 - shape*Math.log(u)/rate)/shape;
    }
    rllogis(shape, scale) {
	var u = Math.random();
	return scale*Math.exp(-Math.log(1.0/u-1.0)/shape);
    }
    rllogiscure(cure, shape, scale) {
	return (Math.random() < cure) ? Infinity : this.rllogis(shape, scale);
    }
    discountInterval(y, start, finish, dr) {
	var duration = finish - start;
	return (dr<=0.0 || duration==0.0) ? y*duration :
	    y / Math.pow(1.0 + dr, start) / Math.log(1.0+dr) *
	    (1.0 - 1.0 / Math.pow(1.0 + dr, duration));
    }
    discountPoint(y, time, dr) {
	return (dr<=0.0 || time==0.0) ? y : y / Math.pow(1.0+dr, time);
    }
    addPointCost(cost) {
	this.ECosts += this.discountPoint(cost, this.now(), param.dr_costs);
    }
    init() {
	// state is an inherited attribute from cProcessWithReport used for reporting
	this.state = States.RecurrenceFree; 
	this.AdjuvantCycles = 0;
	this.Toxicities=0;
	this.ECosts = 0.0;
	this.EUtilities = 0.0;
	this.utility = param.u_diseasefree;
	this.cost = 0.0; // interval costs (cf. point costs)
	// scheduleAt(time,kind) is used to put events into the queue
	this.scheduleAt(this.rgompertz(param.d_BS_shape, param.d_BS_rate), Events.toDeath);
	if (this.arm != Arms.Observation)
	    this.scheduleAt(this.now(),Events.toAdjuvantTreatment);
	var RFS = this.rllogiscure(param.d_RFS_cure[this.arm],
				   param.d_RFS_shape[this.arm], 
				   param.d_RFS_scale[this.arm]);
	if (RFS != Infinity)
	    this.scheduleAt(RFS, Events.toRecurrence);
	this.scheduleAt(55.0, Events.toEOF);
    }
    handleMessage(msg) {
	// reporting
	this.ECosts += this.discountInterval(this.cost, this.previousEventTime, this.now(), param.dr_costs);
	this.EUtilities += this.discountInterval(this.utility, this.previousEventTime, this.now(), param.dr_health);
	if (this.debug)
	    console.log("time=" + msg.time + ", kind=" + msg.kind + "\n");
	switch(msg.kind) {
	case Events.toRecurrenceFree:
	    this.state = States.RecurrenceFree;
	    this.utility = param.u_diseasefree;
	    break;
	case Events.toAdjuvantTreatment: {
	    this.state = States.AdjuvantTreatment;
	    this.AdjuvantCycles++;
	    var p = (this.arm == Arms.Lev) ? param.p_tox_1 : param.p_tox_2;
	    if (p < Math.random())
		this.scheduleAt(this.now(), Events.toToxicity);
	    this.utility = param.u_adjuvant;
	    this.addPointCost((this.arm == Arms.Lev ? param.c_adjuvant_cycle_1 : param.c_adjuvant_cycle_2));
	    this.scheduleAt(this.now() + param.t_adjuvant_cycle,
			    (this.AdjuvantCycles < param.n_max_adjuvant_cycles) ? Events.toAdjuvantTreatment :
			    Events.toRecurrenceFree);
	} break;
	case Events.toToxicity:
	    this.Toxicities++;
	    this.addPointCost(param.c_tox);
	    this.utility = param.u_adjuvant - param.u_dis_tox;
	    break;
	case Events.toRecurrence: {
	    this.state = States.Recurrence;
	    this.cancelKind(Events.toAdjuvantTreatment);
	    this.cancelKind(Events.toRecurrenceFree);
	    // cancelKind(Events.toDeath);
	    this.utility = param.u_advanced;
	    this.addPointCost(param.c_advanced);
	    var CSS = this.rllogis(param.d_CSS_shape[this.arm],
				   param.d_CSS_scale[this.arm]);
	    this.scheduleAt(this.now() + CSS, Events.toDeath);
	} break;
	case Events.toDeath:
	case Events.toEOF:
	    this.report_ECosts.push(this.ECosts);
	    this.report_EUtilities.push(this.EUtilities);
	    this.clear();
	    break;
	default:
	    console.log("Invalid kind of event: " + msg.kind);
	    break;
	}
    }
    mean(v) {
	return v.reduce((accum,vi) => accum+vi, 0.0)/v.length;
    }
    sd(v) {
	var meanv = this.mean(v);
	var total = v.reduce((accum,vi) => accum + Math.pow(vi-meanv,2), 0);
	return Math.sqrt(total/(v.length-1));
    }
    se(v) {
	return this.sd(v)/Math.sqrt(v.length);
    }
    ICER(model2) {
	return (this.mean(this.report_ECosts) - this.mean(model2.report_ECosts)) /
	    (this.mean(this.report_EUtilities) - this.mean(model2.report_EUtilities));
    }
    report() {
	console.log("n           = " + this.report_ECosts.length);
	console.log("Mean(costs) = " + this.mean(this.report_ECosts) +
		    "; se(costs) = " + this.se(this.report_ECosts));
	console.log("Mean(QALYs) = " + this.mean(this.report_EUtilities) +
		    "; se(QALYs) = " + this.se(this.report_EUtilities));
    }
}

function test() {
    let n = 50000;
    console.log("Observation:");
    let sim1 = new ColonModel(Arms.Observation);
    sim1.runMany(n);
    sim1.report();
    console.log("Lev:");
    let sim2 = new ColonModel(Arms.Lev);
    sim2.runMany(n);
    sim2.report();
    var i;
    // for (i=0; i<5; i++) {
    // 	console.log(sim1.report_ECosts[i]);
    // }
    // for (i=0; i<5; i++) {
    // 	console.log(sim1.report_EUtilities[i]);
    // }
    console.log("Lev+5FU:");
    let sim3 = new ColonModel(Arms.Lev_5FU);
    sim3.runMany(n);
    sim3.report();
    console.log("ICER(Lev vs Observation): " + sim2.ICER(sim1));
    console.log("ICER(Lev+5FU vs Observation): " + sim3.ICER(sim1));
    console.log("ICER(Lev+5FU vs Lev): " + sim3.ICER(sim2));
}
test();
		

