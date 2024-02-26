# Benchmark

Here are presented a few examples of QAlloy-F models, a script to benchmark QAlloy-F, and folder *times* containing the results of evaluating the performance of QAlloy-F against these examples (considering a 10 minute timeout). 

The benchmark is performed against every SMT solver supported (Z3, MathSAT, CVC4 and Yices) and for the algebraic Product, Gödelian and Łukasiewicz triangular norms.

## Usage

To run the benchmark, first place the QAlloy-F JAR (e.g. the one provided at [here](https://github.com/pf7/QAlloy-F/releases/tag/v1.0.0)) in the current folder, then execute the ```benchmark.sh``` script as follows:
```
./benchmark.sh -n 3 -t 60000 heater.als portrait.als
```
Which will do 3 runs per solver and per t-norm, for each QAlloy command in both ```heater.als``` and ```portrait.als``` files, with a 1 minute timeout. A folder ```times/``` will be created in the current directory, which will contain the results for each model written in *.csv* format. In this case,  an ```heater.csv``` and ```portrait.csv``` will be produced.

The QAlloy JAR is assumed to be named ```QAlloy-F.jar``` and otherwise can be specified using the option ```-j```.

For the full options run,
```
./benchmark.sh -h
```

By default, the benchmark will do 10 runs per cmd/solver/t-norm with a 10 minute timeout, for every .als model specified.