package ramrakhiani.bugsleuth.config;

public class Configuration {

    public static String rootDirectory = null;
    public static String resultDirectory = null;
    public static String defectsFilePath = null;
    public static int k = 5;
    public static int seed = 956;
    public static int maxIter = 1000;
    public static int convIn = 60;
    public static int popSize = 100;
    public static Double CP = 0.9;
    public static Double MP = 0.6;

    public static void setParameters(String rootDirectory){
        defectsFilePath = rootDirectory + "/data/trialDefects.txt";
        resultDirectory = rootDirectory +"/data/BugSleuth_results_kendall";
    }
}