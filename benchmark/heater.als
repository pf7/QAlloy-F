open util/mf[Temperature, T] as mfTemperature
open util/mf[Humidity, H] as mfHumidity
open util/mf[Heater, P] as mfHeater

one sig Celsius{}
fuzzy sig T in Celsius{}

abstract sig Temperature{
	R : Humidity set -> set Heater
}
one sig cold, warm, hot extends Temperature{}
fuzzy sig fuzzyT in Temperature{}

one sig Percentage{}
fuzzy sig H in Percentage{}

abstract sig Humidity{}
one sig dry, normal, wet extends Humidity{}
fuzzy sig fuzzyH in Humidity{}

one sig Power{}
fuzzy sig P in Power{}

abstract sig Heater{}
one sig low, mid, high extends Heater{}
fuzzy sig fuzzyP in Heater{}

fact fuzzification{
	// Normalized [-20.0, 50.0] to [0,1]
	fuzzyT =
		linz[0.214 ** cold, 0.429 ** cold] +
		trapezoid[0.357 ** warm, 0.571 ** warm, 0.714 ** warm, 0.857 ** warm] +
		lins[0.714 ** hot, 0.857 ** hot] 
	fuzzyH =
		linz[0.0 ** dry, 0.5 ** dry] +
		triangle[0.25 ** normal, 0.5 ** normal, 0.75 ** normal] +
		lins[0.5 ** wet, 1.0 ** wet] 
	fuzzyP =
		linz[0.0 ** low, 0.3 ** low] +
		triangle[0.25 ** mid, 0.5 ** mid, 0.75 ** mid] +
		lins[0.7 ** high, 1.0 ** high] 
}

fun ruleBase : Temperature -> Humidity -> Heater{
		cold -> wet -> high +
		cold -> dry -> mid +
		warm -> normal -> low +
		hot -> dry -> low
}
fact{ all t : Temperature, h : Humidity, p1, p2 : Heater | t -> h -> p1 in R and t -> h -> p2 in R implies p1 = p2 }

fun I : Heater {
	fuzzyH.(fuzzyT.R)
}

fact defuzzification {
	let maxI = max[I] {
			I :> maxI <= fuzzyP :> maxI
			no maxI implies P = 0.5 ** Power
	}
}

run highTemperature { ruleBase = R and P < 0.1**Power and T > 0.8**Celsius }
check highPower { ruleBase = R and T > 0.5**Celsius implies P <= 0.5**Power }
check highPowerExtends { ruleBase in R and T > 0.5**Celsius implies P <= 0.5**Power }
run lowHumidityExtends { ruleBase in R and T = 0.5**Celsius and no H and P < 0.5**Power }
