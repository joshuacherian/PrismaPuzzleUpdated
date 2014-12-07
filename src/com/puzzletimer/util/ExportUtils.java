package com.puzzletimer.util;

import java.util.ArrayList;
import java.io.File;
import java.io.PrintWriter;

import com.puzzletimer.models.Solution;
import com.puzzletimer.state.SolutionManager;
import com.puzzletimer.statistics.Average;

/**
 * Created by Wes on 12/6/2014.
 */
public class ExportUtils
{
    private static ArrayList<Solution> currentFive;
    private static ArrayList<Solution> currentTwelve;
    private static ArrayList<Solution> currentHundred;

    public static void ExportToFile(File target, SolutionManager solutionManager)
    {
        ArrayList<Solution> solutionsList = new ArrayList<Solution>();
        Solution[] solutionsArray = solutionManager.getSolutions();

        // we want the solutions to be in reverse order (earliest to latest)
        // so calculated averages are correct (i.e. Ao12 is of the current solve
        // and the 11 previous solves)
        for (Solution solution : solutionsArray) {
            solutionsList.add(0, solution);
        }

        currentFive = new ArrayList<Solution>();
        currentTwelve = new ArrayList<Solution>();
        currentHundred = new ArrayList<Solution>();

        try
        {
            String[] columnHeaders = { "Solution Date & Time", "Scramble", "Time Elapsed (seconds)", "Current Ao5",
                                       "Current Ao12", "Current Ao100"};

            PrintWriter writer = new PrintWriter(target);
            writer.write(StringUtils.join(",", columnHeaders) + "\n");

            for (Solution solution : solutionsList)
            {
                String ao5 = "";
                String ao12 = "";
                String ao100 = "";
                currentFive.add(solution);
                currentTwelve.add(solution);
                currentHundred.add(solution);

                if(currentFive.size() > 5) { currentFive.remove(0); }
                if(currentTwelve.size() > 12) { currentTwelve.remove(0); }
                if(currentHundred.size() > 100) { currentHundred.remove(0); }

                if (currentFive.size() == 5)
                {
                    Average aoFive = new Average(0,1);
                    aoFive.setSolutions(currentFive.toArray(new Solution[currentFive.size()]));
                    ao5 = "" + SolutionUtils.formatSeconds(aoFive.getValue());
                }

                if (currentTwelve.size() == 12)
                {
                    Average aoTwelve = new Average(0,1);
                    aoTwelve.setSolutions(currentTwelve.toArray(new Solution[currentTwelve.size()]));
                    ao12 = "" + SolutionUtils.formatSeconds(aoTwelve.getValue());
                }

                if (currentHundred.size() == 100)
                {
                    Average aoHundred = new Average(0,1);
                    aoHundred.setSolutions(currentHundred.toArray(new Solution[currentHundred.size()]));
                    ao100 = "" + SolutionUtils.formatSeconds(aoHundred.getValue());
                }

                String date = solution.getTiming().getStart().toString();
                String timeElapsed = SolutionUtils.formatSeconds(solution.getTiming().getElapsedTime());
                String scramble = solution.getScramble().getRawSequence();

                String[] rowValues = { date, scramble, timeElapsed, ao5, ao12, ao100 };
                writer.write(StringUtils.join(",", rowValues) + "\n");
            }

            writer.close();
        }
        catch(Exception e)
        {
            System.out.println(e.getMessage());
        }
    }
}
