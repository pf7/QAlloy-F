package kodkod.engine.config;

import java.util.Arrays;
import java.util.Collection;

import static kodkod.engine.config.QuantitativeOptions.QuantitativeSolver.*;
import static kodkod.engine.config.QuantitativeOptions.QuantitativeType.*;
import static kodkod.engine.config.QuantitativeOptions.Tnorm.*;

/**
 * Analogously to the qualitative {@link Options},
 * represents the solver used on a quantitative problem, it's configurations and other
 * characteristics to be considered during the solving process.
 *
 * @specfield solver             : QuantitativeSolver // Solver to be used
 * @specfield analysisType       : QuantitativeType   // Analysis context
 * @specfield binaryLocation     : String             // Path to this.solver executable binary
 * @specfield maximumWeight      : Integer            // Integer values upper bound
 * @specfield incremental        : boolean            // true iff this.solver will perform incremental solving
 * @specfield maxPrimaryVariable : int                // Maximum number of primary variables in this.solver
 * @specfield tnorm              : Tnorm              // T-norm to be considered
 */
public class QuantitativeOptions {

    /**
     * Specifies the type of quantitative analysis being performed.
     */
    public enum QuantitativeType {
        INTEGER,
        FUZZY;

        /**
         * @return Analysis type associated with the given name
         * @throws IllegalArgumentException qt != "INTEGER" || qt != "FUZZY"
         */
        public static QuantitativeType getQT(String qt){
            if(qt.equalsIgnoreCase("INTEGER"))
                return INTEGER;
            if(qt.equalsIgnoreCase("FUZZY"))
                return FUZZY;
            throw new IllegalArgumentException("Unsupported analysis type in a quantitative setting:" + qt);
        }

        /**
         * @return qt = "INTEGER" || qt = "FUZZY"
         */
        public static boolean contains(String qt){
            try{
                getQT(qt);
                return true;
            }catch(IllegalArgumentException e){
                return false;
            }
        }
    }

    /**
     * Represents the currently supported quantitative solvers and its characteristics, like:
     * - if it's executed through a binary;
     * - if it supports incremental solving.
     *
     * @specfield onBinary : boolean
     * @specfield canBeIncremental : boolean
     */
    public enum QuantitativeSolver{
        CVC4( true, true),
        Z3(true, true),
        MathSAT(true, true),
        Yices(true, true);

        private final boolean onBinary;
        private final boolean canBeIncremental;

        QuantitativeSolver(boolean onBinary, boolean incremental){
            this.onBinary = onBinary;
            this.canBeIncremental = incremental;
        }

        /**
         * Checks if there is a solver with the given name currently supported.
         */
        public static boolean containsSolver(String solver){
            return Arrays.asList("CVC4", "Z3", "MATHSAT", "YICES").contains(solver.toUpperCase());
        }

        /**
         * @return Corresponding QuantitativeSolver
         * @throws IllegalArgumentException if !QuantitativeSolver.contains(solver)
         */
        public static QuantitativeSolver getSolver(String solver){
            QuantitativeSolver s;

            switch (solver.toUpperCase()){
                case "CVC4":
                    s = CVC4;
                    break;
                case "Z3":
                    s = Z3;
                    break;
                case "MATHSAT":
                    s = MathSAT;
                    break;
                case "YICES":
                    s = Yices;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported solver: " + solver);
            }

            return s;
        }

        /**
         * @return this.onBinary
         */
        public boolean hasBinary(){
            return onBinary;
        }

    }

    /**
     * Supported T-norms.
     */
    public enum Tnorm {
        Godelian,
        Lukasiewicz,
        Product,
        Drastic,
        Einstein,
        // Composition based T-norms TODO
        ADD_MIN,
        MAX_PRODUCT;

        /**
         * @return Corresponding T-norm
         * @throws IllegalArgumentException if !Tnorm.contains(t)
         */
        public static Tnorm getTNorm(String t){
            Tnorm tnorm;

            switch (t.toUpperCase()){
                case "GODELIAN":
                    tnorm = Godelian;
                    break;
                case "LUKASIEWICZ":
                    tnorm = Lukasiewicz;
                    break;
                case "PRODUCT":
                    tnorm = Product;
                    break;
                case "DRASTIC":
                    tnorm = Drastic;
                    break;
                case "EINSTEIN":
                    tnorm = Einstein;
                    break;
                case "ADD_MIN":
                    tnorm = ADD_MIN;
                    break;
                case "MAX_PRODUCT":
                    tnorm = MAX_PRODUCT;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported T-norm: " + t);
            }

            return tnorm;
        }
    }

    private final QuantitativeSolver solver;
    private final QuantitativeType analysisType;
    private final String binaryLocation; //can be null (null iff !solver.hasBinary?)
    private final Integer maximumWeight; //null <=> unlimited
    private final boolean incremental;
    private int maxPrimaryVariable;
    // Fuzzy Options
    private final Tnorm tnorm;

    /**
     * Stores the quantitative solving options associated with the given parameters.
     * @param solver Solver to be used during solving
     * @param qt Analysis context where the solver will act
     * @param binaryLocation Path to the solver binary (can be null)
     * @param maximumWeight Maximum integer value (can be null)
     * @param incrementalSolving true => prefer incremental solving, if the solver supports it, else false
     *        i.e.,
     *        incrementalSolving => this.incremental = this.solver.canBeIncremental
     *                           else this.incremental = false
     * @param tnorm T-norm of choice.
     * @throws IllegalArgumentException if !QuantitativeSolver.contains(solver) ||
     *                                     !QuantitativeType.contains(qt) ||
     *                                     !contains(solver, qt) ||
     *                                     !Tnorm.contains(tnorm)
     */
    public QuantitativeOptions(String solver, String qt, String binaryLocation, Integer maximumWeight, boolean incrementalSolving, String tnorm){
        this.solver = getSolver(solver);
        this.analysisType = getQT(qt);
        if(!contains(solver, qt))
            throw new IllegalArgumentException("The solver " + solver + " does not support the quantitative context:" + qt);

        this.binaryLocation = binaryLocation;
        this.maximumWeight = maximumWeight;
        this.incremental = incrementalSolving && this.solver.canBeIncremental;
        this.maxPrimaryVariable = 0;
        this.tnorm = getTNorm(tnorm);
    }

    /**
     * Stores the quantitative solving options associated with the given parameters.
     * @param solver Solver to be used during solving
     * @param qt Analysis context where the solver will act
     * @param binaryLocation Path to the solver binary (can be null)
     * @param maximumWeight Maximum integer value (can be null)
     * @param incrementalSolving true => prefer incremental solving, if the solver supports it, else false
     *        i.e.,
     *        incrementalSolving => this.incremental = this.solver.canBeIncremental
     *                           else this.incremental = false
     * @throws IllegalArgumentException if !QuantitativeSolver.contains(solver) ||
     *                                     !QuantitativeType.contains(qt) ||
     *                                     !contains(solver, qt)
     */
    public QuantitativeOptions(String solver, String qt, String binaryLocation, Integer maximumWeight, boolean incrementalSolving){
        this(solver, qt, binaryLocation, maximumWeight, incrementalSolving, "Godelian");
    }

    /**
     * Stores the quantitative solving options associated with the given parameters.
     * @param solver Solver to be used during solving
     * @param qt Analysis context where the solver will act
     * @param binaryLocation Path to the solver binary (can be null)
     * @param maximumWeight Maximum integer value (can be null)
     * @param incrementalSolving true => prefer incremental solving, if the solver supports it, else false
     *        i.e.,
     *        incrementalSolving => this.incremental = this.solver.canBeIncremental
     *                           else this.incremental = false
     * @param t T-norm of choice.
     */
    public QuantitativeOptions(QuantitativeSolver solver, QuantitativeType qt, String binaryLocation, Integer maximumWeight, boolean incrementalSolving, Tnorm t){
        this.solver = solver;
        this.analysisType = qt;
        this.binaryLocation = binaryLocation;
        this.maximumWeight = maximumWeight;
        this.incremental = incrementalSolving;
        this.maxPrimaryVariable = 0;
        this.tnorm = t;
    }

    /**
     * Stores the quantitative solving options associated with the given parameters and remaining default values.
     * @param solver Solver to be used during solving
     * @param qt Analysis context where the solver will act
     * @param binaryLocation Path to the solver binary (can be null)
     * @throws IllegalArgumentException if !QuantitativeSolver.contains(solver)
     */
    public QuantitativeOptions(String solver, String qt, String binaryLocation){
        this(solver, qt, binaryLocation, null, true, "Godelian");
    }

    /**
     * Stores the quantitative solving options associated with the given parameters and remaining default values.
     * @param solver Solver to be used during solving
     * @param qt Analysis context where the solver will act
     * @param binaryLocation Path to the solver binary (can be null)
     */
    public QuantitativeOptions(QuantitativeSolver solver, QuantitativeType qt, String binaryLocation){
        this(solver, qt, binaryLocation, null, true, Godelian);
    }

    /**
     * Creates default solver options for the quantitative context specified.
     * If {@code qt} == INTEGER or FUZZY then the solver defaults to MathSAT
     */
    public QuantitativeOptions(QuantitativeType qt){
        this(MathSAT, qt, null, null, false, Godelian);
    }

    /**
     * Deep copy of the given options.
     */
    public QuantitativeOptions(QuantitativeOptions options){
        this.solver = options.solver;
        this.analysisType = options.analysisType;
        this.binaryLocation = options.binaryLocation;
        this.maximumWeight = options.maximumWeight;
        this.incremental = options.incremental;
        this.maxPrimaryVariable = options.maxPrimaryVariable;
        this.tnorm = options.tnorm;
    }

    /**
     * @return this.solver
     */
    public QuantitativeSolver solver(){
        return this.solver;
    }

    /**
     * @return this.analysisType
     */
    public QuantitativeType getAnalysisType(){
        return this.analysisType;
    }

    /**
     * @return this.binaryLocation
     */
    public String getBinaryLocation() {
        return binaryLocation;
    }

    /**
     * Checks if integer function symbols will have an upper bound.
     * @return this.maximumWeight != null && this.maximumWeight >= 0
     */
    public boolean hasMaximumWeight(){
        return this.maximumWeight != null && this.maximumWeight >= 0;
    }

    /**
     * @return this.maximumWeight
     */
    public Integer getMaximumWeight() {
        return maximumWeight;
    }

    /**
     * @return this.incremental
     */
    public boolean incremental(){
        return this.incremental;
    }

    /**
     * Sets the maximum number of primary variables allowed.
     */
    public void setMaxPrimaryVariable(int maxPrimaryVariable){
        this.maxPrimaryVariable = maxPrimaryVariable;
    }

    /**
     * @return this.maxPrimaryVariable
     */
    public int getMaxPrimaryVariable(){
        return maxPrimaryVariable;
    }

    /**
     * @return this.tnorm
     */
    public Tnorm getTnorm() {
        return this.tnorm;
    }

    /**
     * Checks if there is a supported quantitative solver for the quantitative context specified.
     */
    public static boolean contains(String solver, String quantitativeType){
        boolean containsSolver = false;
        if(QuantitativeSolver.containsSolver(solver) && QuantitativeType.contains(quantitativeType))
            containsSolver = getSolvers(QuantitativeType.getQT(quantitativeType)).contains(getSolver(solver));
        return containsSolver;
    }

    /**
     * @return Supported solvers.
     */
    public static Collection<QuantitativeSolver> getSolvers(){
        return Arrays.asList(CVC4, Z3, MathSAT, Yices);
    }

    /**
     * @return Supported solvers for the given quantitative type.
     */
    public static Collection<QuantitativeSolver> getSolvers(QuantitativeType qt){
        return Arrays.asList(CVC4, Z3, MathSAT, Yices);
    }

    /**
     * @return String of this quantitative options
     */
    public String toString(){
        return  "Quantitative Type:\n" + analysisType +
                "\nSolver:" + solver +
                "\nBinary Location:" + binaryLocation +
                "\nMaximum weight:" + maximumWeight +
                "\nIncremental solving:" + incremental +
                "\nmaxPrimaryVar:" + maxPrimaryVariable +
                "\ntnorm:" + tnorm;
    }

    /**
     * @return deep-copy of this
     */
    @Override
    public QuantitativeOptions clone(){
        return new QuantitativeOptions(this);
    }

}
