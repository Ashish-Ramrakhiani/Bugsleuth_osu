package ramrakhiani.bugsleuth.main;
import ramrakhiani.bugsleuth.config.Configuration;

import java.util.*;
import java.util.Random;

public class GeneticAlgorithm<C extends Chromosome<C>, T extends Comparable<T>> {

    private static final int ALL_PARENTAL_CHROMOSOMES = Integer.MAX_VALUE;

    private static final Random random = new Random(Configuration.seed);

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
           // System.out.println("Fitness of "++"is "+fit);
            return fit;
        };

        public void clearCache() {
            this.cache.clear();
        }
    }

    private final ChromosomesComparator chromosomesComparator;

    private final Fitness<C, T> fitnessFunc;

    private Population<C> population;

    private List<LinkedHashSet<String>> allRankLists;
    private LinkedHashSet<String> allStatements;

    // listeners of genetic algorithm iterations (handle callback afterwards)
    private final List<IterationListener<C, T>> iterationListeners = new LinkedList<IterationListener<C, T>>();

    private boolean terminate = false;

    // number of parental chromosomes, which survive (and move to new
    // population)
    private int parentChromosomesSurviveCount = ALL_PARENTAL_CHROMOSOMES;

    private int iteration = 0;

    public GeneticAlgorithm(Population<C> population, Fitness<C, T> fitnessFunc, List<LinkedHashSet<String>> allRankLists, LinkedHashSet<String> allStatements) {
        this.population = population;
        this.fitnessFunc = fitnessFunc;
        this.chromosomesComparator = new ChromosomesComparator();
        this.allRankLists = allRankLists;
        this.allStatements = allStatements;
        this.population.sortPopulationByFitness(this.chromosomesComparator);

    }

    public void evolve() {
        int parentPopulationSize = this.population.getSize();

        Population<C> newPopulation = new Population<C>();

        float selfraction = random.nextFloat(0,1);
        int survivecount =  Math.round(selfraction * Configuration.popSize);

        //System.out.println("Survive count"+survivecount);
       //System.out.println(this.population.getSize() - survivecount);
        //System.out.println();
        while(newPopulation.getSize()<(Configuration.popSize- survivecount)){

            //System.out.println("Pop size"+newPopulation.getSize());

            if(random.nextFloat(0,1)<=0.9) {
                C chromosome = this.population.getRandomChromosome();
                C mutated = chromosome.mutate(this.allStatements);
                newPopulation.addChromosome(mutated);
            }
            if(random.nextFloat(0,1)<=0.9) {

                C parent1 = this.rouletteWheelSelection();
                C parent2 = this.rouletteWheelSelection();
                while (parent2.equals(parent1)) {
                    parent2 = this.rouletteWheelSelection();
                }

                List<C> crossovered = parent1.partially_mapped_crossover(parent2);

                // newPopulation.addChromosome(mutated);
                for (C c : crossovered) {
                    newPopulation.addChromosome(c);
                }
            }
            //System.out.println("Stuck in crossover");
        }

        while(newPopulation.getSize()<Configuration.popSize) {

            C selectedChromosome = tournamentSelector(20);
            newPopulation.addChromosome(selectedChromosome);
           //System.out.println("Stuck in tournament");

        }

        newPopulation.sortPopulationByFitness(this.chromosomesComparator);
        //newPopulation.trim(parentPopulationSize);
        //System.out.println("New population size is"+newPopulation.getSize());
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
            System.out.println("Iteration "+i);
            for (IterationListener<C, T> l : this.iterationListeners) {
                l.update(this);
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
        double pop_sector = random.nextDouble() * totalFitness;
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
        double bestFitness = Double.NEGATIVE_INFINITY;
        //System.out.println("tournament size is"+tournamentSize);
        for (int i = 0; i < tournamentSize; i++) {

            C randomChromosome = this.population.getRandomChromosome();

            //T fitness = this.chromosomesComparator.fit(randomChromosome);
            double fitness = this.fitnessFunc.calculateDouble(randomChromosome, this.allRankLists);
            //T fitness = GeneticAlgorithm.this.fitnessFunc.calculate(randomChromosome, this.allRankLists);
           // System.out.println("Fitness is"+fitness);

            if (fitness > bestFitness) {
                bestChromosome = randomChromosome;
                bestFitness = fitness;
            }
            //fitness.compareTo(bestFitness);
        }
        if (bestChromosome == null) {
            throw new RuntimeException("Tournament selection failed to select a chromosome");
        }

        return bestChromosome;
    }
}
