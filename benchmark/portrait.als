sig Portrait{
	 fuzzy R : set Portrait
}

fact EquivalenceRelation{
	all p: Portrait | p->p in R
	R = ~R
	all p1, p2, p3 : Portrait | let 
		m1 = #(p1 <: R :> p2),
		m2 = #(p2 <: R :> p3),
		m = m1 > m2 implies m2 else m1 {
			#(p1 <: R :> p3) >= m
	}
}

fun class[p : one Portrait, X : Portrait -> Portrait] : set Portrait{
	{ p2 : Portrait | p->p2 in drop X }
}

run threeToTwo{
	some disj p1,p2,p3 : Portrait | p1+p2+p3 = class[p1, 0.4-cut R] and p1+p2 = class[p1, 0.7-cut R]
} for exactly 6 Portrait

check invProportionality{
	all p : Portrait | class[p, 0.5-cut R] in class[p, 0.3-cut R]
} for 4 Portrait 
