sig Patient {
  fuzzy Q : some Symptom,
  fuzzy nQ : some Symptom }
abstract sig Symptom {
  fuzzy R : set Disease,
  fuzzy nR : set Disease }
one sig Temp, Cough, StmPn, ChtPn, Hdche extends Symptom {}
abstract sig Disease {}
one sig ViralFv, Typhoid, StmPrb, Malaria, ChtPrb extends Disease {}

fact intuitionistic { 
  R <= sub[1** Symptom -> Disease, nR] and Q <= sub[1**Patient -> Symptom, nQ] }

fun score : Patient -> Disease {
	sub[add[0.5 ** Patient -> Disease, 0.5 ** (Q.R)], 0.5 ** n_diagnostic]
}

fun n_diagnostic : Patient -> Disease {
  sub[Patient -> Disease,sub[Patient -> Symptom, nQ] . (sub[Symptom -> Disease, nR])]
}

fun diagnosis : Patient -> Disease {
  { p:Patient, d:Disease | d in max[p.score] }
}

run two_diagnosis { 
	some p : Patient | Malaria + ChtPrb in p.diagnosis 
	R = expert_R 
	nR = expert_nR
} for 1 Patient

run same_diagnosis { 
	some p1, p2 : Patient | p1.diagnosis = p2.diagnosis and no p1.Q & p2.Q 
	R = expert_R 
	nR = expert_nR
} for 2 Patient

check maxChestPain{ 
	R = expert_R and nR = expert_nR implies all p:Patient | ChtPn in max[p.Q] implies ChtPrb in p.diagnosis 
} for 1

fun expert_R : Symptom -> Disease {
  (0.4**Temp + 0.4**Cough + 0.1**StmPn + 0.1**ChtPn + 0.3**Hdche) -> ViralFv +
  (0.7**Temp + 0.2**Hdche + 0.7**Cough + 0.1**ChtPn) -> Malaria +
  (0.3**Temp + 0.2**Cough + 0.1**ChtPn + 0.6**Hdche + 0.2**StmPn) -> Typhoid +
  (0.1**Temp + 0.2**Cough + 0.8**StmPn + 0.2**ChtPn + 0.2**Hdche) -> StmPrb +
  (0.1**Temp + 0.2**StmPn + 0.8**ChtPn + 0.2**Cough) -> ChtPrb }


fun expert_nR : Symptom -> Disease {
  (0.3**Cough + 0.7**StmPn + 0.7**ChtPn + 0.5**Hdche) -> ViralFv +
  (0.6**Hdche + 0.9**StmPn + 0.8**ChtPn) -> Malaria + 
  (0.3**Temp + 0.6**Cough + 0.9**ChtPn + 0.1**Hdche + 0.7**StmPn) -> Typhoid +
  (0.7**Temp + 0.7**Cough + 0.7**ChtPn + 0.4**Hdche) -> StmPrb +
  (0.8**Temp + 0.8**StmPn + 0.1**ChtPn + 0.8**Cough + 0.8**Hdche) -> ChtPrb
}



