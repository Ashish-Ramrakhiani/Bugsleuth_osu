package ramrakhiani.bugsleuth.main;
import ramrakhiani.bugsleuth.config.Configuration;

import java.util.*;

public class GeneticAlgorithm<C extends Chromosome<C>, T extends Comparable<T>> {

    private static final int ALL_PARENTAL_CHROMOSOMES = Integer.MAX_VALUE;


    private class ChromosomesComparator implements Comparator<C> {

        private final Map<C, T> cache = new WeakHashMap<C, T>();

        @Override
        public int compare(C chr1, C chr2) {
            T fit1 = this.fit(chr1);
            T fit2 = this.fit(chr2);
            int ret = fit1.compareTo(fit2);
            return ret;
        }

        public T fit(C chr) {
            T fit = this.cache.get(chr);
            if (fit == null) {
                fit = GeneticAlgorithm.this.fitnessFunc.calculate(chr,GeneticAlgorithm.this.allRankLists);
                this.cache.put(chr, fit);
            }
            return fit;
        };
        public void clearCache() {
            this.cache.clear();
        }
    }

    private final ChromosomesComparator chromosomesComparator;

    private final Fitness<C, T> fitnessFunc;

    private Population<C> population;

    private final List<LinkedHashSet<String>> allRankLists;
    private final LinkedHashSet<String> allStatements;

    private final String defect;
    private final Random random;
    private long seed;
    private final int convIn;

    // listeners of genetic algorithm iterations (handle callback afterwards)
    private final List<IterationListener<C, T>> iterationListeners = new LinkedList<IterationListener<C, T>>();

    private boolean terminate = false;

    // number of parental chromosomes, which survive (and move to new
    // population)
    private int parentChromosomesSurviveCount = ALL_PARENTAL_CHROMOSOMES;

    private int iteration = 0;
    private int noImprovement = 0;

    public GeneticAlgorithm(Population<C> population, Fitness<C, T> fitnessFunc, List<LinkedHashSet<String>> allRankLists, LinkedHashSet<String> allStatements, String defect, long seed, int convIn) {
        this.population = population;
        this.fitnessFunc = fitnessFunc;
        this.chromosomesComparator = new ChromosomesComparator();
        this.allRankLists = allRankLists;
        this.allStatements = allStatements;
        this.defect = defect;
        this.population.sortPopulationByFitness(this.chromosomesComparator);
        this.random = new Random(seed);
        this.seed = seed;
        this.convIn = convIn;

    }

    public void evolve() {
        int parentPopulationSize = this.population.getSize();

        Population<C> newPopulation = new Population<C>(this.seed);

        float selfraction = random.nextFloat(0,1);
        int survivecount =  Math.round(selfraction * Configuration.popSize);

        while(newPopulation.getSize()<(Configuration.popSize- survivecount)){

           if(random.nextFloat(0,1)<=Configuration.MP) {
                C chromosome = this.population.getRandomChromosome();
                C mutated = chromosome.mutate(this.allStatements);
                newPopulation.addChromosome(mutated);
            }

           if(random.nextFloat(0,1)<=Configuration.CP)
                {

                C parent1 = this.rouletteWheelSelection();
                C parent2 = this.rouletteWheelSelection();
                while (parent2.equals(parent1)) {
                    parent2 = this.rouletteWheelSelection();
                }

                List<C> crossovered = parent1.partially_mapped_crossover(parent2);
                for (C c : crossovered) {
                    newPopulation.addChromosome(c);
                }
            }
        }

        while(newPopulation.getSize()<Configuration.popSize) {

            C selectedChromosome = tournamentSelector(Configuration.tSize);
            newPopulation.addChromosome(selectedChromosome);
        }

        newPopulation.sortPopulationByFitness(this.chromosomesComparator);
        this.population = newPopulation;
    }

    public void evolve(int count) {
        this.terminate = false;

        for (int i = 0; i < count; i++) {
            if (this.terminate) {
                break;
            }
            this.evolve();
            this.iteration = i;
            for (IterationListener<C, T> l : this.iterationListeners) {
                l.update(this, this.defect);
            }
        }
    }

    public int getIteration() {
        return this.iteration;
    }

    public void terminate() {
        this.terminate = true;
    }

    public Population<C> getPopulation() {
        return this.population;
    }

    public C getBest() {

        this.population.sortPopulationByFitness(this.chromosomesComparator);
        return this.population.getChromosomeByIndex(0);
    }

    public C getWorst() {
        return this.population.getChromosomeByIndex(this.population.getSize() - 1);
    }

    public void setParentChromosomesSurviveCount(int parentChromosomesCount) {
        this.parentChromosomesSurviveCount = parentChromosomesCount;
    }

    public int getParentChromosomesSurviveCount() {
        return this.parentChromosomesSurviveCount;
    }

    public void addIterationListener(IterationListener<C, T> listener) {
        this.iterationListeners.add(listener);
    }

    public void removeIterationListener(IterationListener<C, T> listener) {
        this.iterationListeners.remove(listener);
    }

    public T fitness(C chromosome) {
        return this.chromosomesComparator.fit(chromosome);
    }

    public void clearCache() {
        this.chromosomesComparator.clearCache();
    }

    public double calculateTotalFitness() {
        double totalFitness = 0.0;
        for (int i = 0; i < this.population.getSize(); i++) {
            C chromosome = this.population.getChromosomeByIndex(i);
            totalFitness += this.fitnessFunc.calculateDouble(chromosome, this.allRankLists);
        }
        return totalFitness;
    }

    public C rouletteWheelSelection() {
        double totalFitness = calculateTotalFitness();
        double pop_sector =random.nextDouble() * totalFitness;
        //double pop_sector =0.7 * totalFitness;
        double cumulativeFitness = 0.0;

        for (C chromosome : this.population) {
            cumulativeFitness += this.fitnessFunc.calculateDouble(chromosome, this.allRankLists);
            if (cumulativeFitness >= pop_sector) {
                return chromosome;
            }
        }
        throw new RuntimeException("Roulette wheel selection failed to select a chromosome");
    }

    public C tournamentSelector(int tournamentSize) {
        C bestChromosome = null;
        double bestFitness = Double.POSITIVE_INFINITY;
        for (int i = 0; i < tournamentSize; i++) {

          C randomChromosome = this.population.getRandomChromosome();

            double fitness = this.fitnessFunc.calculateDouble(randomChromosome, this.allRankLists);
            if (fitness < bestFitness) {
                bestChromosome = randomChromosome;
                bestFitness = fitness;
            }
        }
        if (bestChromosome == null) {
            throw new RuntimeException("Tournament selection failed to select a chromosome");
        }

        return bestChromosome;
    }
}
