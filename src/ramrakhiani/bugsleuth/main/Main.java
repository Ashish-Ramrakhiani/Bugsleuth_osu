package ramrakhiani.bugsleuth.main;

import ramrakhiani.bugsleuth.config.Configuration;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.io.BufferedWriter;

public class Main {
    public static void main(String[] args) {

        LinkedHashSet<String> allStatements = new LinkedHashSet<>();
        List<LinkedHashSet<String>> allRankLists = new ArrayList<>();
        Boolean all_defects = false;
        LinkedHashSet<String> defects_all = new LinkedHashSet<String>();

        try
        {
            setParametersFromSettingsFile();
        }
        catch(IOException e){};

        String defect = args[0].trim().toLowerCase();
        int numberOfFiles = Integer.parseInt(args[1]);


        readDefects(defect, defects_all,all_defects);

        for(String defects: defects_all ) {

            defect = defects;
            allRankLists.clear();
            allStatements.clear();
            System.out.println("Processing defect "+defect);

            for (int i = 2; i <= numberOfFiles + 1; i++) {
                allRankLists.add(new LinkedHashSet<>());
                String filePath = args[i];


                File flPath = new File(filePath);
                if (!flPath.exists()) {
                    System.out.println(filePath + " file path does not exist");
                    System.exit(1);
                }

                readStatementsFromFile(defect, filePath, i - 2, allStatements, allRankLists);
            }
            if(allRankLists.size()!= numberOfFiles)
            {
                System.out.println("Not enough input results found for "+defect);
                continue;
            }

            Population<CandidateList> population = createInitialPopulation(Configuration.popSize, allStatements);
            Fitness<CandidateList, Double> fitness = new CandidateFitness();

            GeneticAlgorithm<CandidateList, Double> ga = new GeneticAlgorithm<CandidateList, Double>(population, fitness, allRankLists, allStatements, defect,Configuration.seed,Configuration.convIn);
            addListener(ga, defect);
            ga.evolve(Configuration.maxIter);
        }
    }

    private static void addListener(GeneticAlgorithm<CandidateList, Double> ga, String defect) {

        System.out.println(String.format("%s\t%s\t%s\t%s", "iter", "fit", "chromosome", "global best chromosome"));

        ga.addIterationListener(new IterationListener<CandidateList, Double>() {

            private final double threshold = 1e-5;
            int noImprovement = 0;
            CandidateList globalbest = null;
            @Override
            public void update(GeneticAlgorithm<CandidateList, Double> ga, String defect) {


                CandidateList best = ga.getBest();

                if(ga.getIteration() == 0) {
                    System.out.println("First iteration");
                    System.out.println("Global Best changed");
                    globalbest = best.clone();
                    noImprovement++;

                }
                else {
                    if(ga.fitness(best) < ga.fitness(globalbest))
                    {
                        noImprovement = 0;
                        globalbest = best.clone();
                        System.out.println("Global Best changed");
                    }
                    else
                    {
                        noImprovement++;
                    }
                }

                double bestFit = ga.fitness(best);
                int iteration = ga.getIteration();
                if(iteration == Configuration.maxIter -1)
                {
                    saveResultsToFile(defect,best.getCandidate());
                }
                // Listener prints best achieved solution
                System.out.println(String.format("%s\t%s\t%s\t%s", iteration, bestFit, Arrays.toString(best.getCandidate()),Arrays.toString(globalbest.getCandidate())));
                //System.out.println(String.format("%s", Arrays.toString(globalbest.getCandidate())));
                /*if (iteration == 0)
                {
                    for(int i =0;i<ga.getPopulation().getSize();i++)
                    {
                        System.out.println("Fitness:"+ga.fitness(ga.getPopulation().getChromosomeByIndex(i)));
                        System.out.println("Chromosome:"+Arrays.toString(ga.getPopulation().getChromosomeByIndex(i).getCandidate()));
                    }
                    System.out.println("Worst " + Arrays.toString(ga.getWorst().getCandidate()));
                    System.out.println("Worst fitness" + ga.fitness(ga.getWorst()));
                }*/
                if (noImprovement >= Configuration.convIn) {
                    ga.terminate();
                }
            }
        });
    }

    public static class CandidateFitness implements Fitness<CandidateList, Double> {

        @Override
        public Double calculate(CandidateList chromosome , List<LinkedHashSet<String>> allRankLists) {


            List<String> genotype = new ArrayList<String>(Arrays.asList(chromosome.getCandidate()));

            double totalDistance = 0.0;

            for(LinkedHashSet<String> rankList:allRankLists)
            {

                LinkedHashSet<String> union = new LinkedHashSet<String>(genotype);
                union.addAll(rankList);
                double distance = 0.0;

                for (String statement : union) {

                    int rankInGenotype = genotype.contains(statement) ? new ArrayList<String>(genotype).indexOf(statement) + 1 : rankList.size() + 1;
                    int rankInList = rankList.contains(statement) ? new ArrayList<String>(rankList).indexOf(statement) + 1 : rankList.size() + 1;
                    distance += Math.abs(rankInGenotype - rankInList);

                }
                totalDistance += distance;
                union.clear();
            }
            return totalDistance;

        }

        public double calculateDouble(CandidateList chromosome, List<LinkedHashSet<String>> allRankLists) {
            return calculate(chromosome, allRankLists);
        }

    }

    private static Population<CandidateList> createInitialPopulation(int populationSize, LinkedHashSet<String> allStatements) {
        Population<CandidateList> population = new Population<CandidateList>(Configuration.seed);
        CandidateList base = new CandidateList();
        for (int i = 0; i < populationSize; i++) {
            CandidateList chr = base.getRandomRanklist(allStatements);
            population.addChromosome(chr);
        }
        return population;
    }

    public static class CandidateList implements Chromosome<CandidateList>, Cloneable {

        private static final Random random = new Random(Configuration.seed );
        private String[] candidate = new String[Configuration.k];

        public CandidateList mutate(LinkedHashSet<String> allStatements){
           int mut_op = random.nextInt(4);
            //System.out.println("Seed is:"+Configuration.random.getSeed());
            //int mut_op = 1;
            CandidateList result = this.clone();
            switch(mut_op) {
                case 0: {
                    int index1 = random.nextInt(this.candidate.length);
                    int index2 = random.nextInt(this.candidate.length);
                    while(index2==index1)
                    {
                        index2 = random.nextInt(this.candidate.length);
                    }
                    String temp = result.candidate[index1];
                    result.candidate[index1] = result.candidate[index2];
                    result.candidate[index2] = temp;
                    //System.out.println("Seed  inside mut is:"+Configuration.random.getSeed());
                }
                case 1:
                {
                    String temp = result.candidate[0];
                    result.candidate[0] = result.candidate[result.candidate.length-1];
                    result.candidate[result.candidate.length-1] = temp;

                }
                case 2:
                {
                    List<String> allStatementsList = new ArrayList<>(allStatements);
                    int index1 = random.nextInt(0,allStatements.size());
                   // System.out.println("Seed  inside mut 3 is:"+Configuration.random.getSeed());
                    while(Arrays.asList(result.candidate).contains(allStatementsList.get(index1)))
                    {
                        index1 = random.nextInt(0,allStatements.size());

                    }
                    int index2 = random.nextInt(this.candidate.length);
                    result.candidate[index2] = allStatements.toArray(new String[0])[index1];

                }
               case 3:
                {
                   List<String> allStatementsList = new ArrayList<>(allStatements);
                    int index = random.nextInt(this.candidate.length);
                    String path = result.candidate[index].split("#")[0];
                    int line_no = Integer.parseInt(result.candidate[index].split("#")[1]);

                    String[] statementList = new String[6];
                    for(int i =1,j=0;i<=3;i++,j++)
                    {
                        statementList[j] = path+"#"+(line_no+i);
                        j++;
                        statementList[j] = path+"#"+(line_no-i);

                    }
                    for(int k =0;k<statementList.length;k++)
                    {
                        if(allStatementsList.contains(statementList[k]) && !(Arrays.asList(result.candidate).contains(statementList[k])))
                        {
                            result.candidate[index] = statementList[k];
                            break;
                        }
                    }

                }

            }
            return result;
        }
        public CandidateList getRandomRanklist(LinkedHashSet<String> allStatements)
        {
            CandidateList result = this.clone();
            random.setSeed(Configuration.seed);
            String[] stmt_pool = allStatements.toArray(new String[0]);
            for(int i=0;i<Configuration.k;i++)
            {
                int index = random.nextInt(stmt_pool.length);
                while(Arrays.asList(result.candidate).contains(stmt_pool[index])) {
                    index = random.nextInt(stmt_pool.length);
                }
                result.candidate[i] = stmt_pool[index];
            }
            return result;

        }
        public String[] getCandidate() {
            return this.candidate;
        }
        @Override
        protected CandidateList clone() {
            CandidateList clone = new CandidateList();
            System.arraycopy(this.candidate, 0, clone.candidate, 0, this.candidate.length);
            return clone;
        }
        public List<CandidateList> partially_mapped_crossover(CandidateList other){
            CandidateList thisClone = this.clone();
            CandidateList otherClone = other.clone();
            int index1 = random.nextInt(this.candidate.length - 1);
            int index2 = random.nextInt(index1,this.candidate.length-1);

            for (int i = index1; i <=index2; i++) {
                String tmp = thisClone.candidate[i];
                thisClone.candidate[i] = otherClone.candidate[i];
                otherClone.candidate[i] = tmp;
            }
            repair(thisClone,otherClone,index1,index2);
            repair(otherClone,thisClone,index1,index2);

            return Arrays.asList(thisClone, otherClone);
        }

        private static void repair(CandidateList thisClone, CandidateList otherClone,int index1, int index2)
        {
            for(int i=0;i<index1;i++)
            {
                int index = indexExists(thisClone,thisClone.candidate[i],index1,index2);
                while(index!=-1)
                {
                    thisClone.candidate[i] = otherClone.candidate[index];
                    index = indexExists(thisClone,thisClone.candidate[i],index1,index2);
                }

            }
            for(int i=index2+1,n=thisClone.candidate.length;i<n;i++)
            {

                int index = indexExists(thisClone,thisClone.candidate[i],index1,index2);
                while(index!=-1)
                {
                    thisClone.candidate[i] = otherClone.candidate[index];
                    index = indexExists(thisClone,thisClone.candidate[i],index1,index2);
                }

            }

        }

        public static int indexExists(CandidateList thisClone,String statement, int begin, int end)
        {
            for(int i= begin;i<=end;i++)
            {
                if(thisClone.candidate[i].equals(statement))
                {
                    return i;
                }
            }
            return -1;
        }

    }

    private static HashMap<String, Double> sortByValues(HashMap<String, Double> map) {
        List<Object> list = new LinkedList<Object>(map.entrySet());

        Collections.sort(list, new Comparator<Object>() {
            public int compare(Object o1, Object o2) {
                return ((Comparable) ((Map.Entry) (o1)).getValue()).compareTo(((Map.Entry) (o2)).getValue());
            }
        });
        Collections.reverse(list);

        double maxscore = Double.parseDouble(list.get(0).toString().split("=")[1].trim());
        double minscore = Double.parseDouble(list.get(list.size() - 1).toString().split("=")[1].trim());

        HashMap<String, Double> sortedHashMap = new LinkedHashMap<String, Double>();
        for (Iterator<Object> it = list.iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            Double score = (Double) entry.getValue();
            Double normalizedScore = (score - minscore) / (maxscore - minscore);
            sortedHashMap.put((String) entry.getKey(), normalizedScore);
        }
        return sortedHashMap;
    }


    private static void readDefects(String defect, LinkedHashSet<String> defects_all, Boolean all_defects)
    {
        if(defect.equals("all"))
        {
            String line,d;
            all_defects = true;
            File fl_result = new File(Configuration.defectsFilePath);
            if (!fl_result.exists()) {
                System.out.println("Error, results not found in the file path: "+Configuration.defectsFilePath);
                System.exit(1);
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(Configuration.defectsFilePath))){
                while ((line = reader.readLine()) != null)
                {
                    d = line.trim().toLowerCase();
                    if(!(d.isEmpty()))
                    {
                        defects_all.add( line.trim().toLowerCase());
                    }
                }

            }catch(IOException e) {e.printStackTrace();}
        }
        else {
            defects_all.add(defect);
        }


    }

    private static void readStatementsFromFile(String defect,String filePath, int rankListIndex, LinkedHashSet<String> allStatements,List<LinkedHashSet<String>> allRankLists ) {


        String result_file = filePath + "/" + defect.split("_")[0].toLowerCase() + "/" + defect.split("_")[1] + "/stmt-susps.txt";
        File fl_result = new File(result_file);
        if (!fl_result.exists()) {
            System.out.println("Error, results not found in the file path: "+result_file);
        }
        else
        {
            HashMap<String, Double> FL = new HashMap<String, Double>();
            HashMap<String, Double> sortedFL = new HashMap<String, Double>();

            try (BufferedReader reader = new BufferedReader(new FileReader(result_file))) {
                String line;
                LinkedHashSet<String> uniqueStatements = new LinkedHashSet<>();
                uniqueStatements.clear();
                reader.readLine();

                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        double score = Double.parseDouble(parts[1]);

                        if(score > 0.0)
                        {
                            String statement = parts[0].trim();

                            if(!FL.containsKey(statement))
                            {
                                FL.put(statement, score);
                            }
                            else {
                                if(FL.get(statement) < score) {
                                    FL.put(statement, score);

                                }

                            }

                        }
                    }
                }

                if(!FL.isEmpty())
                {
                    sortedFL = sortByValues(FL);

                    int counter = 0;
                    for(String stmt: sortedFL.keySet())
                    {
                        uniqueStatements.add(stmt);
                        counter ++;
                        if(counter == Configuration.k)
                            break;
                    }
                    allStatements.addAll(uniqueStatements);
                    allRankLists.get(rankListIndex).addAll(uniqueStatements);

                    uniqueStatements.clear();
                }


            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Configuring the value for root directory
     */

    private static void setParametersFromSettingsFile() throws IOException{
        File settings_file = new File("bugsleuth.settings");
        BufferedReader br = new BufferedReader(new FileReader(settings_file));
        String line;
        while ((line = br.readLine()) != null) {
            if (line.contains("root"))
                Configuration.rootDirectory = line.split("=")[1].trim();
        }
        br.close();
        System.out.println("Setting root directory as: " + Configuration.rootDirectory);
        Configuration.setParameters(Configuration.rootDirectory);
    }

    private static void saveResultsToFile(String defect,String[] bestPhenotype) {
        String result_file = Configuration.resultDirectory+"/BugSleuth_results_seed"+Configuration.seed + "/" + defect.split("_")[0].toLowerCase() + "/"
                + defect.split("_")[1] + "/stmt-susps.txt";
        File rfile = new File(result_file);

        rfile.getParentFile().mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(rfile))) {
            for (String gene : bestPhenotype) {
                writer.write(gene);
                writer.newLine();
            }
            System.out.println("Results saved to "+ result_file);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}