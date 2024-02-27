# FIS2ALS

*FIS2ALS* is a prototype which can be used to automatically generate suitable QAlloy models (*.als*) from *Fuzzy Inference System* files (*.fis*), typically developed in MATLAB<sup>®</sup>.

## Usage

Consider the ```Heater.fis``` provided in this folder. To obtain the associated QAlloy model, one may run the JAR as follows:
```
java -jar fis2als.jar Heater.fis
```
which will create a ```Heater.als``` file in the directory where the original .fis file is located. In particular, the QAlloy model generated for this example is analogous to the one provided at [*benchmark/heater.als*](https://github.com/pf7/QAlloy-F/blob/main/benchmark/heater.als). 

```Heater.fis``` specifies a Mamdani FIS, while the ```Tip.fis``` is an example of a Sugeno FIS.

## FIS Details

Currently the prototype does not support every single possible configuration, but those abiding to the listed characteristics.
- Both *Mamdani* and *Sugeno* FIS are supported.
- The triangular norms provided by QAlloy encapsulate both t-norm and respective t-conorm, meaning that the ```AndMethod``` and ```OrMethod``` should specify a t-norm supported by QAlloy accordingly. For example, the *Product* t-norm is described by ```AndMethod='prod'``` and ```OrMethod='probor'```. When that is not the case, FIS2ALS will default to a supported t-norm (usually according to the ```AndMethod``` specified) when generating the .als specification, and warn the user.
- ```AggMethod``` should coincide with ```OrMethod``` in a Mamdani FIS.
- Both implication methods (```ImpMethod='min'``` or ```='prod'```) are supported.
- For a Mamdani FIS, its rule base must be AND-based (i.e., the antecedent of every rule is connected through fuzzy logic 'And'). If it's a Sugeno FIS, any arbitrary combination of rules connected by OR/AND is supported.
- Membership functions currently supported: triangular ```trimf```, trapezoidal ```trapmf```, linear Z-shape ```linzmf``` and linear S-shape ```linsmf```.
- For the output variables of Sugeno, both ```constant``` and ```linear``` functions are supported.
- The parameters of the specified membership functions must be within the respective variable's range. For example, if a variable is declared with range ```[10,30]```, the membership function ```trimf[0 5 15]``` is not allowed.
- The defuzzification method of a Mamdani FIS is assumed to be a variant of *maximum*, while the *weighted average* is considered for a Sugeno FIS.

