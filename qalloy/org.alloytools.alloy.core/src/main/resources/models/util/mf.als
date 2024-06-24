module mf[A, C]

fun singleton[a : A] : A {
	let x = (drop C).(C -> drop a) |
		x = a implies 1 ** A else 0 ** A
}

fun singleton[x, a : A] : A {
	x = a implies 1 ** A else 0 ** A
}

// triangle(x ; a, b, c) = max(min(x-a / b-a, c-x /c-b), 0)
//                       = min(x-a / b-a, c-x /c-b) // Bounded
fun triangle[a, b, c : A] : A {
	let type = drop b, x = (drop C).(C -> type) |
		smaller[div[sub[x, a], sub[b, a]], div[sub[c, x], sub[c, b]]]
}

fun triangle[x, a, b, c : A] : A {
	smaller[div[sub[x, a], sub[b, a]], div[sub[c, x], sub[c, b]]]
}

// trapezoidal(x; a, b, c, d) = max(min(x-a / b-a, 1, d-x / d-c), 0)
//                            = min(x-a / b-a, d-x / d-c) // Bounded
fun trapezoid[a, b, c, d : A] : A {
	let type = drop b, x = (drop C).(C -> type) |
		smaller[div[sub[x, a], sub[b,a]], div[sub[d,x], sub[d,c]]]
}

fun trapezoid[x, a, b, c, d : A] : A {
	smaller[div[sub[x, a], sub[b,a]], div[sub[d,x], sub[d,c]]]
}

// linear z-shape
fun linz[a, b : A] : A {
    let type = drop (a+b), x = (drop C).(C -> type) {
        a = b implies (x < a implies 1 ** type else 0 ** type)
	   else {
			x < a implies 1 ** type
			else x > b implies 0 ** type
			else div[sub[b, x], sub[b, a]]
		}
    }
}

fun linz[x, a, b : A] : A {
    let type = drop (a+b){
        a = b implies (x < a implies 1 ** type else 0 ** type)
	   else {
			x < a implies 1 ** type
			else x > b implies 0 ** type
			else div[sub[b, x], sub[b, a]]
		}
    }
}

// linear s-shape
fun lins[a, b : A] : A {
    let type = drop (a+b), x = (drop C).(C -> type) {
	   a = b implies (x < a implies 0 ** type else 1 ** type)
	   else {
			x < a implies 0 ** type
			else x > b implies 1 ** type
			else div[sub[x, a], sub[b, a]]
		}
    }
}

fun lins[x, a, b : A] : A {
    let type = drop (a+b) {
	   a = b implies (x < a implies 0 ** type else 1 ** type)
	   else {
			x < a implies 0 ** type
			else x > b implies 1 ** type
			else div[sub[x, a], sub[b, a]]
		}
    }
}
