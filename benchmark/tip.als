open util/mf[Service, S]
open util/mf[Food, F]

one sig Rating{}
fuzzy sig S, F in Rating{}

abstract sig Service{
	R : Food set -> set Rule,
	fuzzy W : Food set -> set Rule
}

one sig poor, good, excellent, null_s extends Service{}
fuzzy sig fuzzyS in Service{}

abstract sig Food{}
one sig rancid, average, delicious, null_f extends Food{}
fuzzy sig fuzzyF in Food{}

one sig Tip{
	fuzzy Y : set Rule
}

sig Rule{}
fact{ all r : Rule | some R.r }
one sig rule1 extends Rule{}

fact fuzzification{
	fuzzyS =
		linz[0.1 ** poor, 0.3 ** poor] +
		trapezoid[0.2 ** good, 0.4 ** good, 0.6 ** good, 0.8 ** good] +
		lins[0.7 ** excellent, 0.9 ** excellent]
	fuzzyF =
		linz[0 ** rancid, 0.3 ** rancid] +
		triangle[0.25 ** average, 0.5 ** average, 0.75 ** average] +
		lins[0.7 ** delicious, 1 ** delicious] 
}

fact RuleBase{
	poor -> rancid +
	good -> null_f +
	excellent -> delicious in R.Rule

	all s : Service, f : Food, r1, r2 : Rule |
		s->f->r1 in R and s->f->r2 in R implies r1 = r2
	all s1,s2 : Service, f1,f2 : Food, r : Rule |
		s1->f1->r in R and s2->f2->r in R implies s1=s2 and f1=f2

	all s : Service, f : Food - null_f | (some r : Rule | s->f->r in R) implies (no r2 : Rule | s -> null_f -> r2 in R)
	all s : Service - null_s, f : Food | (some r : Rule | s->f->r in R) implies (no r2 : Rule | null_s -> f -> r2 in R)
}

fact Weight{
	W = { s : Service, f : Food, r : Rule |
		s->f->r in R implies{
			s = null_s implies s -> (f <: fuzzyF) -> r
			else f = null_f implies (s <: fuzzyS) -> f -> r
			else ((s <: fuzzyS -> drop f) + (drop s -> f <: fuzzyF)) -> r
		}
		else 0 ** s->f->r }
}

fun toTip[x : Rating] : Tip {
  	(Tip->x).Rating
}

fact ruleY{
	Y.(rancid.(poor.R)) = 0 ** Tip
	Y.(null_f.(good.R)) = toTip[S]
	Y.(delicious.(excellent.R)) = toTip[add[0.4 ** S, add[0.4 ** F, 0.2 ** Rating]]]
}

fun defuzzification : Tip {
	let yR = Tip.Y, wR= Food.(Service.W), tW = #(0.0625 ** wR) {
		toTip[div[#{ r : Rule | 0.0625 ** div[0.0625 ** mul[yR :> r, wR:> r], tW] }, 0.0625 ** Rule]]
	} 
}

fun toTip[rs : Rule] : Tip {
	(Tip -> rs).rule1
}

run findGenerousTip {
	defuzzification = 0.85 ** Tip
} for 1 but exactly 3 Rule

check wasItGoodService {
	let t = defuzzification | t >= 0.4 ** Tip and t <= 0.6 ** Tip implies (fuzzyS :> good) > 0.8 ** good
} for 2 but exactly 3 Rule

check cheapTip{
	S = 0.3 ** Rating and F = 0.2 ** Rating implies defuzzification < 0.5 ** Tip
} for 4 but exactly 4 Rule

