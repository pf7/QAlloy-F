sig Patient {
	fuzzy Q : some Symptom 
}

abstract sig Symptom {
	fuzzy R : set Disease 
}
one sig Temp, Cough, StmPn, ChtPn, Hdche extends Symptom {}

abstract sig Disease {}
one sig ViralFv, Typhoid, StmPrb, Malaria, ChtPrb extends Disease {}

fun diagnosis : Patient -> Disease {
	{ p:Patient, d:Disease | d in max[p .Q. R] } 
}

fun expert_R : Symptom -> Disease {
	  (0.4**Temp + 0.4**Cough + 0.1**StmPn + 0.1**ChtPn + 0.3**Hdche) -> ViralFv +
	  (0.7**Temp + 0.2**Hdche + 0.7**Cough + 0.1**ChtPn) -> Malaria +
	  (0.3**Temp + 0.2**Cough + 0.1**ChtPn + 0.6**Hdche + 0.2**StmPn) -> Typhoid +
	  (0.1**Temp + 0.2**Cough + 0.8**StmPn + 0.2**ChtPn + 0.2**Hdche) -> StmPrb +
	  (0.1**Temp + 0.2**StmPn + 0.8**ChtPn + 0.2**Cough) -> ChtPrb 
}

run two_diagnosis { 
	some p : Patient | Malaria + ChtPrb in p.diagnosis 
	R = expert_R 
} for 1 Patient

run same_diagnosis { 
	some p1, p2 : Patient | p1.diagnosis = p2.diagnosis and no p1.Q & p2.Q 
	R = expert_R 
} for 2 Patient

check maxChestPain { 
	R = expert_R implies all p : Patient | ChtPn in max[p.Q] implies ChtPrb in p.diagnosis 
} for 4 Patient



