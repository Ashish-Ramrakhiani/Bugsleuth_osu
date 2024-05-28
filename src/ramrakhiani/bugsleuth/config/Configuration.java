package ramrakhiani.bugsleuth.config;

import ramrakhiani.bugsleuth.main.SeededRandom;

import java.util.Random;

public class Configuration {

    public static String rootDirectory = null;
    public static String resultDirectory = null;
    public static String defectsFilePath = null;
    public static int k = 5;
    public static long seed = 123L;
    public static int maxIter = 100;
    public static int convIn = 60;
    public static int popSize = 10;
    public static Double CP = 0.9;
    public static Double MP = 0.6;
    public static int tSize = 5;
    //public static SeededRandom random = new SeededRandom(seed);

    public static void setParameters(String rootDirectory){
        defectsFilePath = rootDirectory + "/data/704Defects.txt";
        resultDirectory = rootDirectory +"/data/BugSleuth_results";
    }
}