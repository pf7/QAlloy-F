module linear

let mul[A, B]{ A fun/mul B }

let div[A, B]{ A fun/div B }

let sub[A, B]{ A fun/sub B }

let add[A, B]{ A fun/add B }

let rem[A, B]{ A fun/rem B }

let max[A]{
	{ a : A | all a2 : A - a | #(a <: A) >= #(a2 <: A) } 
}

let maxValue[A]{
	{ a : A | (all a2 : A - a | #(a <: A) >= #(a2 <: A)) implies (a <: A) else 0 ** a } 
}

let larger [A, B] {
	A >= B implies A else (A <= B implies B else none)
}

let min[A]{
	{ a : A | all a2 : A - a | #(a <: A) <= #(a2 <: A) } 
}

let minValue[A]{
	{ a : A | (all a2 : A - a | #(a <: A) <= #(a2 <: A)) implies (a <: A) else 0 ** a } 
}

let smaller [A, B] {
	A <= B implies A else (A >= B implies B else none)
}

let negate [A] { 0 ** A fun/sub A }

let eq [A, B] { A = B }

let gt [A, B] { A > B }

let lt [A, B] { A < B }

let gte [A, B] { A >= B }

let lte [A, B] { A <= B }

let zero [A] { A = 0 ** A }

let pos  [A] { A > 0 ** A }

let neg  [A] { A < 0 ** A }

let nonpos [A] { A <= 0 ** A }

let nonneg [A] { A >= 0 ** A }

let signum [A] { let T = drop A | A < 0 ** T => -1 ** T else (A > 0 ** T => T else 0 ** T) }
