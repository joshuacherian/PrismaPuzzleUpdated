package com.puzzletimer.gui;

import static com.puzzletimer.Internationalization._;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.DataLine.Info;
import javax.swing.*;

import com.puzzletimer.util.ExportUtils;
import net.miginfocom.swing.MigLayout;

import com.puzzletimer.graphics.Panel3D;
import com.puzzletimer.gui.SolutionEditingDialog.SolutionEditingDialogListener;
import com.puzzletimer.models.Category;
import com.puzzletimer.models.ColorScheme;
import com.puzzletimer.models.Scramble;
import com.puzzletimer.models.Solution;
import com.puzzletimer.models.Timing;
import com.puzzletimer.parsers.ScrambleParserProvider;
import com.puzzletimer.puzzles.Puzzle;
import com.puzzletimer.puzzles.PuzzleProvider;
import com.puzzletimer.scramblers.Scrambler;
import com.puzzletimer.scramblers.ScramblerProvider;
import com.puzzletimer.state.CategoryManager;
import com.puzzletimer.state.ColorManager;
import com.puzzletimer.state.ConfigurationManager;
import com.puzzletimer.state.MessageManager;
import com.puzzletimer.state.ScrambleManager;
import com.puzzletimer.state.SessionManager;
import com.puzzletimer.state.SolutionManager;
import com.puzzletimer.state.TimerManager;
import com.puzzletimer.state.MessageManager.MessageType;
import com.puzzletimer.statistics.Average;
import com.puzzletimer.statistics.Best;
import com.puzzletimer.statistics.BestAverage;
import com.puzzletimer.statistics.BestMean;
import com.puzzletimer.statistics.Mean;
import com.puzzletimer.statistics.Percentile;
import com.puzzletimer.statistics.StandardDeviation;
import com.puzzletimer.statistics.StatisticalMeasure;
import com.puzzletimer.statistics.Worst;
import com.puzzletimer.timer.ControlKeysTimer;
import com.puzzletimer.timer.SpaceKeyTimer;
import com.puzzletimer.timer.StackmatTimer;
import com.puzzletimer.tips.TipProvider;
import com.puzzletimer.util.SolutionUtils;

@SuppressWarnings("serial")
public class MainFrame extends JFrame {
    private class ScramblePanel extends JPanel {
        private ScrambleViewerPanel scrambleViewerPanel;

        public ScramblePanel(ScrambleManager scrambleManager) {
            createComponents();

            scrambleManager.addListener(new ScrambleManager.Listener() {
                @Override
                public void scrambleChanged(Scramble scramble) {
                    setScramble(scramble);
                }
            });
        }

        public void setScrambleViewerPanel(ScrambleViewerPanel scrambleViewerPanel) {
            this.scrambleViewerPanel = scrambleViewerPanel;
        }

        private void createComponents() {
            setLayout(new WrapLayout(10, 3));
        }

        private void setScramble(final Scramble scramble) {
            removeAll();

            final JLabel[] labels = new JLabel[scramble.getSequence().length];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = new JLabel(scramble.getSequence()[i]);
                labels[i].setFont(new Font("Arial", Font.PLAIN, 18));
                labels[i].setCursor(new Cursor(Cursor.HAND_CURSOR));

                final int index = i;
                labels[i].addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        for (int i = 0; i < labels.length; i++) {
                            labels[i].setForeground(
                                i <= index ? Color.BLACK : Color.LIGHT_GRAY);
                        }

                        ScramblePanel.this.scrambleViewerPanel.setScramble(
                            new Scramble(
                                scramble.getScramblerId(),
                                Arrays.copyOf(scramble.getSequence(), index + 1)));
                    }
                });

                add(labels[i], "gap 10");
            }

            revalidate();
            repaint();
        }
    }

    private class TimerPanel extends JPanel {
        private HandImage leftHand;
        private TimeLabel timeLabel;
        private HandImage rightHand;

        public TimerPanel(TimerManager timerManager) {
            createComponents();

            timerManager.addListener(new TimerManager.Listener() {
                @Override
                public void timerReset() {
                    TimerPanel.this.timeLabel.setForeground(Color.BLACK);
                    TimerPanel.this.timeLabel.setText(
                        SolutionUtils.formatMinutes(0));
                }

                @Override
                public void leftHandPressed() {
                    TimerPanel.this.leftHand.setPressed(true);
                }

                @Override
                public void leftHandReleased() {
                    TimerPanel.this.leftHand.setPressed(false);
                }

                @Override
                public void rightHandPressed() {
                    TimerPanel.this.rightHand.setPressed(true);
                }

                @Override
                public void rightHandReleased() {
                    TimerPanel.this.rightHand.setPressed(false);
                }

                @Override
                public void inspectionRunning(long remainingTime) {
                    Color startColor = Color.BLACK;
                    Color endColor = new Color(0xD4, 0x11, 0x11);

                    Color color;
                    if (remainingTime > 7000) {
                        color = startColor;
                    } else if (remainingTime > 0) {
                        double x = remainingTime / 7000.0;
                        color = new Color(
                            (int) (x * startColor.getRed()   + (1 - x) * endColor.getRed()),
                            (int) (x * startColor.getGreen() + (1 - x) * endColor.getGreen()),
                            (int) (x * startColor.getBlue()  + (1 - x) * endColor.getBlue()));
                    } else {
                        color = endColor;
                        remainingTime = 0;
                    }

                    TimerPanel.this.timeLabel.setForeground(color);
                    TimerPanel.this.timeLabel.setText(
                        Long.toString((long)Math.ceil(remainingTime / 1000.0)));
                }

                @Override
                public void solutionRunning(Timing timing) {
                    TimerPanel.this.timeLabel.setForeground(Color.BLACK);
                    TimerPanel.this.timeLabel.setText(
                        SolutionUtils.formatMinutes(timing.getElapsedTime()));
                }

                @Override
                public void solutionFinished(Timing timing, String penalty) {
                    TimerPanel.this.timeLabel.setForeground(Color.BLACK);
                    TimerPanel.this.timeLabel.setText(
                        SolutionUtils.formatMinutes(timing.getElapsedTime()));
                }
            });
        }

        private void createComponents() {
            setLayout(new MigLayout("fill", "2%[19%]1%[56%]1%[19%]2%"));

            // leftHand
            this.leftHand = new HandImage(false);
            add(this.leftHand, "grow");

            // timeLabel
            this.timeLabel = new TimeLabel("00:00.00");
            this.timeLabel.setFont(new Font("Arial", Font.BOLD, 108));
            add(this.timeLabel, "grow");

            // rightHand
            this.rightHand = new HandImage(true);
            add(this.rightHand, "grow");
        }
    }

    private class TimesScrollPane extends JScrollPane {
        private SolutionManager solutionManager;

        private JPanel panel;

        public TimesScrollPane(SolutionManager solutionManager, SessionManager sessionManager) {
            this.solutionManager = solutionManager;

            createComponents();

            sessionManager.addListener(new SessionManager.Listener() {
                @Override
                public void solutionsUpdated(Solution[] solutions) {
                    setSolutions(solutions);
                }
            });
        }

        private void createComponents() {
            // scroll doesn't work without this
            setPreferredSize(new Dimension(0, 0));

            // panel
            this.panel = new JPanel(
                new MigLayout(
                    "center",
                    "0[right]8[pref!]16[pref!]8[pref!]16[pref!]0",
                    ""));
        }

        private void setSolutions(final Solution[] solutions) {
            this.panel.removeAll();

            for (int i = 0; i < solutions.length; i++) {
                final Solution solution = solutions[i];

                JLabel labelIndex = new JLabel(Integer.toString(solutions.length - i) + ".");
                labelIndex.setFont(new Font("Tahoma", Font.BOLD, 13));
                this.panel.add(labelIndex);

                JLabel labelTime = new JLabel(SolutionUtils.formatMinutes(solutions[i].getTiming().getElapsedTime()));
                labelTime.setFont(new Font("Tahoma", Font.PLAIN, 13));
                this.panel.add(labelTime);

                final JLabel labelPlus2 = new JLabel("+2");
                labelPlus2.setFont(new Font("Tahoma", Font.PLAIN, 13));
                if (!solution.getPenalty().equals("+2")) {
                    labelPlus2.setForeground(Color.LIGHT_GRAY);
                }
                labelPlus2.setCursor(new Cursor(Cursor.HAND_CURSOR));
                labelPlus2.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (!solution.getPenalty().equals("+2")) {
                            TimesScrollPane.this.solutionManager.updateSolution(
                                solution.setPenalty("+2"));
                        } else if (solution.getPenalty().equals("+2")) {
                            TimesScrollPane.this.solutionManager.updateSolution(
                                solution.setPenalty(""));
                        }
                    }
                });
                this.panel.add(labelPlus2);

                final JLabel labelDNF = new JLabel("DNF");
                labelDNF.setFont(new Font("Tahoma", Font.PLAIN, 13));
                if (!solution.getPenalty().equals("DNF")) {
                    labelDNF.setForeground(Color.LIGHT_GRAY);
                }
                labelDNF.setCursor(new Cursor(Cursor.HAND_CURSOR));
                labelDNF.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (!solution.getPenalty().equals("DNF")) {
                            TimesScrollPane.this.solutionManager.updateSolution(
                                solution.setPenalty("DNF"));
                        } else if (solution.getPenalty().equals("DNF")) {
                            TimesScrollPane.this.solutionManager.updateSolution(
                                solution.setPenalty(""));
                        }
                    }
                });
                this.panel.add(labelDNF);

                JLabel labelX = new JLabel();
                labelX.setIcon(new ImageIcon(getClass().getResource("/com/puzzletimer/resources/x.png")));
                labelX.setCursor(new Cursor(Cursor.HAND_CURSOR));
                labelX.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        TimesScrollPane.this.solutionManager.removeSolution(solution);
                    }
                });
                this.panel.add(labelX, "wrap");
            }

            setViewportView(this.panel);
        }
    }

    private class StatisticsPanel extends JPanel {
        private JLabel labelMean;
        private JLabel labelAverage;
        private JLabel labelBestTime;
        private JLabel labelMedian;
        private JLabel labelWorstTime;
        private JLabel labelStandardDeviation;
        private JLabel labelMeanOf3;
        private JLabel labelBestMeanOf3;
        private JLabel labelAverageOf5;
        private JLabel labelBestAverageOf5;
        private JLabel labelAverageOf12;
        private JLabel labelBestAverageOf12;

        private StatisticsPanel(SessionManager sessionManager) {
            createComponents();

            final JLabel[] labels = {
                this.labelMean,
                this.labelAverage,
                this.labelBestTime,
                this.labelMedian,
                this.labelWorstTime,
                this.labelStandardDeviation,
                this.labelMeanOf3,
                this.labelBestMeanOf3,
                this.labelAverageOf5,
                this.labelBestAverageOf5,
                this.labelAverageOf12,
                this.labelBestAverageOf12,
            };

            final StatisticalMeasure[] measures = {
                new Mean(1, Integer.MAX_VALUE),
                new Average(3, Integer.MAX_VALUE),
                new Best(1, Integer.MAX_VALUE),
                new Percentile(1, Integer.MAX_VALUE, 0.5),
                new Worst(1, Integer.MAX_VALUE),
                new StandardDeviation(1, Integer.MAX_VALUE),
                new Mean(3, 3),
                new BestMean(3, Integer.MAX_VALUE),
                new Average(5, 5),
                new BestAverage(5, Integer.MAX_VALUE),
                new Average(12, 12),
                new BestAverage(12, Integer.MAX_VALUE),
            };

            sessionManager.addListener(new SessionManager.Listener() {
                @Override
                public void solutionsUpdated(Solution[] solutions) {
                    for (int i = 0; i < labels.length; i++) {
                        if (solutions.length >= measures[i].getMinimumWindowSize()) {
                            int size = Math.min(solutions.length, measures[i].getMaximumWindowSize());

                            Solution[] window = new Solution[size];
                            for (int j = 0; j < size; j++) {
                                window[j] = solutions[j];
                            }

                            measures[i].setSolutions(window);
                            labels[i].setText(SolutionUtils.formatMinutes(measures[i].getValue()));
                        } else {
                            labels[i].setText("XX:XX.XX");
                        }
                    }
                }
            });
        }

        private void createComponents() {
            setLayout(
                new MigLayout(
                    "center",
                    "[pref!,right]8[pref!]",
                    "1[pref!]1[pref!]1[pref!]1[pref!]1[pref!]1[pref!]6[pref!]1[pref!]6[pref!]1[pref!]6[pref!]1[pref!]1"));

            // labelMean
            JLabel labelMeanDescription = new JLabel(_("statistics.mean"));
            labelMeanDescription.setFont(new Font("Tahoma", Font.BOLD, 11));
            add(labelMeanDescription);

            this.labelMean = new JLabel("XX:XX.XX");
            add(this.labelMean, "wrap");

            // labelAverage
            JLabel labelAverageDescription = new JLabel(_("statistics.average"));
            labelAverageDescription.setFont(new Font("Tahoma", Font.BOLD, 11));
            add(labelAverageDescription);

            this.labelAverage= new JLabel("XX:XX.XX");
            add(this.labelAverage, "wrap");

            // labelBestTime
            JLabel labelBestTimeDescription = new JLabel(_("statistics.best_time"));
            labelBestTimeDescription.setFont(new Font("Tahoma", Font.BOLD, 11));
            add(labelBestTimeDescription);

            this.labelBestTime = new JLabel("XX:XX.XX");
            add(this.labelBestTime, "wrap");

            // labelMedian
            JLabel labelMedianDescription = new JLabel(_("statistics.median"));
            labelMedianDescription.setFont(new Font("Tahoma", Font.BOLD, 11));
            add(labelMedianDescription);

            this.labelMedian = new JLabel("XX:XX.XX");
            add(this.labelMedian, "wrap");

            // labelWorstTime
            JLabel labelWorstTimeDescription = new JLabel(_("statistics.worst_time"));
            labelWorstTimeDescription.setFont(new Font("Tahoma", Font.BOLD, 11));
            add(labelWorstTimeDescription);

            this.labelWorstTime = new JLabel("XX:XX.XX");
            add(this.labelWorstTime, "wrap");

            // labelStandardDeviation
            JLabel labelStandardDeviationDescription = new JLabel(_("statistics.standard_deviation"));
            labelStandardDeviationDescription.setFont(new Font("Tahoma", Font.BOLD, 11));
            add(labelStandardDeviationDescription);

            this.labelStandardDeviation = new JLabel("XX:XX.XX");
            add(this.labelStandardDeviation, "wrap");

            // labelMeanOf3
            JLabel labelMeanOf3Description = new JLabel(_("statistics.mean_of_3"));
            labelMeanOf3Description.setFont(new Font("Tahoma", Font.BOLD, 11));
            add(labelMeanOf3Description);

            this.labelMeanOf3 = new JLabel("XX:XX.XX");
            add(this.labelMeanOf3, "wrap");

            // labelBestMeanOf3
            JLabel labelBestMeanOf3Description = new JLabel(_("statistics.best_mean_of_3"));
            labelBestMeanOf3Description.setFont(new Font("Tahoma", Font.BOLD, 11));
            add(labelBestMeanOf3Description);

            this.labelBestMeanOf3 = new JLabel("XX:XX.XX");
            add(this.labelBestMeanOf3, "wrap");

            // labelAverageOf5
            JLabel labelAverageOf5Description = new JLabel(_("statistics.average_of_5"));
            labelAverageOf5Description.setFont(new Font("Tahoma", Font.BOLD, 11));
            add(labelAverageOf5Description);

            this.labelAverageOf5 = new JLabel("XX:XX.XX");
            add(this.labelAverageOf5, "wrap");

            // labelBestAverageOf5
            JLabel labelBestAverageOf5Description = new JLabel(_("statistics.best_average_of_5"));
            labelBestAverageOf5Description.setFont(new Font("Tahoma", Font.BOLD, 11));
            add(labelBestAverageOf5Description);

            this.labelBestAverageOf5 = new JLabel("XX:XX.XX");
            add(this.labelBestAverageOf5, "wrap");

            // labelAverageOf12
            JLabel labelAverageOf12Description = new JLabel(_("statistics.average_of_12"));
            labelAverageOf12Description.setFont(new Font("Tahoma", Font.BOLD, 11));
            add(labelAverageOf12Description);

            this.labelAverageOf12 = new JLabel("XX:XX.XX");
            add(this.labelAverageOf12, "wrap");

            // labelBestAverageOf12
            JLabel labelBestAverageOf12Description = new JLabel(_("statistics.best_average_of_12"));
            labelBestAverageOf12Description.setFont(new Font("Tahoma", Font.BOLD, 11));
            add(labelBestAverageOf12Description);

            this.labelBestAverageOf12 = new JLabel("XX:XX.XX");
            add(this.labelBestAverageOf12, "wrap");
        }
    }

    private class ScrambleViewerPanel extends JPanel {
        private PuzzleProvider puzzleProvider;
        private ColorManager colorManager;
        private ScramblerProvider scramblerProvider;

        private Panel3D panel3D;

        public ScrambleViewerPanel(
                PuzzleProvider puzzleProvider,
                ColorManager colorManager,
                ScramblerProvider scramblerProvider,
                ScrambleManager scrambleManager) {
            this.puzzleProvider = puzzleProvider;
            this.colorManager = colorManager;
            this.scramblerProvider = scramblerProvider;

            createComponents();

            scrambleManager.addListener(new ScrambleManager.Listener() {
                @Override
                public void scrambleChanged(Scramble scramble) {
                    setScramble(scramble);
                }
            });
        }

        private void createComponents() {
            setLayout(new MigLayout("fill", "0[fill]0", "0[fill]0"));

            // panel 3d
            this.panel3D = new Panel3D();
            this.panel3D.setFocusable(false);
            add(this.panel3D);
        }

        public void setScramble(Scramble scramble) {
            Scrambler scrambler = this.scramblerProvider.get(scramble.getScramblerId());
            Puzzle puzzle = this.puzzleProvider.get(scrambler.getScramblerInfo().getPuzzleId());
            ColorScheme colorScheme = this.colorManager.getColorScheme(puzzle.getPuzzleInfo().getPuzzleId());
            this.panel3D.setMesh(puzzle.getScrambledPuzzleMesh(colorScheme, scramble.getSequence()));
        }
    }

    private MessageManager messageManager;
    private ConfigurationManager configurationManager;
    private TimerManager timerManager;
    private PuzzleProvider puzzleProvider;
    private ColorManager colorManager;
    private ScrambleParserProvider scrambleParserProvider;
    private ScramblerProvider scramblerProvider;
    private TipProvider tipProvider;
    private CategoryManager categoryManager;
    private ScrambleManager scrambleManager;
    private SolutionManager solutionManager;
    private SessionManager sessionManager;

    private JMenu menuFile;
    private JMenuItem menuItemAddSolution;
    private JMenuItem menuItemExit;
    private JMenuItem menuItemTips;
    private JMenuItem menuItemScrambleQueue;
    private JMenuItem menuItemHistory;
    private JMenuItem menuItemSessionSummary;
    private JMenu menuCategory;
    private JMenuItem menuItemColorScheme;
    private JCheckBoxMenuItem menuItemInspectionTime;
    private JMenu stackmatTimerInputDevice;
    private ButtonGroup stackmatTimerInputDeviceGroup;
    private JRadioButtonMenuItem menuItemCtrlKeys;
    private JRadioButtonMenuItem menuItemSpaceKey;
    private JRadioButtonMenuItem menuItemStackmatTimer;
    private JMenuItem menuItemAbout;
    private JLabel labelMessage;
    private ScramblePanel scramblePanel;
    private TimerPanel timerPanel;
    private TimesScrollPane timesScrollPane;
    private StatisticsPanel statisticsPanel;
    private ScrambleViewerPanel scrambleViewerPanel;

    private TipsFrame tipsFrame;
    private ScrambleQueueFrame scrambleQueueFrame;
    private HistoryFrame historyFrame;
    private SessionSummaryFrame sessionSummaryFrame;
    private CategoryManagerFrame categoryManagerDialog;
    private ColorSchemeFrame colorSchemeFrame;

    private AudioFormat audioFormat;
    private Mixer.Info mixerInfo;

    private JMenu toolsMenu;
    private JMenuItem exportSolutionsToCSV;

    public MainFrame(
            MessageManager messageManager,
            ConfigurationManager configurationManager,
            TimerManager timerManager,
            PuzzleProvider puzzleProvider,
            ColorManager colorManager,
            ScrambleParserProvider scrambleParserProvider,
            ScramblerProvider scramblerProvider,
            TipProvider tipProvider,
            CategoryManager categoryManager,
            ScrambleManager scrambleManager,
            final SolutionManager solutionManager,
            SessionManager sessionManager) {
        this.messageManager = messageManager;
        this.puzzleProvider = puzzleProvider;
        this.scrambleParserProvider = scrambleParserProvider;
        this.scramblerProvider = scramblerProvider;
        this.tipProvider = tipProvider;
        this.configurationManager = configurationManager;
        this.timerManager = timerManager;
        this.categoryManager = categoryManager;
        this.scrambleManager = scrambleManager;
        this.solutionManager = solutionManager;
        this.sessionManager = sessionManager;
        this.colorManager = colorManager;

        setMinimumSize(new Dimension(800, 600));
        setPreferredSize(getMinimumSize());

        createComponents();

        // timer configuration
        this.audioFormat = new AudioFormat(8000, 8, 1, true, false);
        this.mixerInfo = null;

        String stackmatTimerInputDeviceName =
            this.configurationManager.getConfiguration("STACKMAT-TIMER-INPUT-DEVICE");
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            if (stackmatTimerInputDeviceName.equals(mixerInfo.getName())) {
                this.mixerInfo = mixerInfo;
                break;
            }
        }

        setTimerTrigger(this.configurationManager.getConfiguration("TIMER-TRIGGER"));

        // inspection time sounds
        try {
            final Clip[] inspectionClips = new Clip[4];

            String[] fileNames = { "eight_seconds.wav", "go.wav", "plus_two.wav", "dnf.wav" };
            for (int i = 0; i < inspectionClips.length; i++) {
                inspectionClips[i] = AudioSystem.getClip();
                inspectionClips[i].open(
                    AudioSystem.getAudioInputStream(
                        MainFrame.class.getResourceAsStream("/com/puzzletimer/resources/inspection/" + fileNames[i])));
            }

            this.timerManager.addListener(new TimerManager.Listener() {
                private int next;

                @Override
                public void inspectionStarted() {
                    this.next = 0;
                }

                @Override
                public void inspectionRunning(long remainingTime) {
                    int[] soundStartTimes = { 7000, 3000, 0, -2000, Integer.MIN_VALUE };
                    if (remainingTime <= soundStartTimes[this.next]) {
                        inspectionClips[this.next].setFramePosition(0);
                        inspectionClips[this.next].start();
                        this.next++;
                    }
                }
            });
        } catch (Exception e) {
        }

        // title
        this.categoryManager.addListener(new CategoryManager.Listener() {
            @Override
            public void categoriesUpdated(Category[] categories, Category currentCategory) {
                setTitle(
                    String.format(
                        _("main.prisma_puzzle_time_category"),
                        currentCategory.getDescription()));
            }
        });

        // menuItemAddSolution
        this.menuItemAddSolution.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Date now = new Date();
                Solution solution = new Solution(
                    UUID.randomUUID(),
                    MainFrame.this.categoryManager.getCurrentCategory().getCategoryId(),
                    MainFrame.this.scrambleManager.getCurrentScramble(),
                    new Timing(now, now),
                    "");

                SolutionEditingDialogListener listener =
                    new SolutionEditingDialogListener() {
                        @Override
                        public void solutionEdited(Solution solution) {
                            MainFrame.this.solutionManager.addSolution(solution);
                            MainFrame.this.scrambleManager.changeScramble();
                        }
                    };

                SolutionEditingDialog solutionEditingDialog =
                    new SolutionEditingDialog(MainFrame.this, true, solution, listener);
                solutionEditingDialog.setTitle(_("main.add_solution_title"));
                solutionEditingDialog.setLocationRelativeTo(null);
                solutionEditingDialog.setVisible(true);
            }
        });

        // menuItemExit
        this.menuItemExit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        // menuItemTips
        this.menuItemTips.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MainFrame.this.tipsFrame.setVisible(true);
            }
        });

        // menuItemScrambleQueue
        this.menuItemScrambleQueue.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MainFrame.this.scrambleQueueFrame.setVisible(true);
            }
        });

        // menuItemHistory
        this.menuItemHistory.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MainFrame.this.historyFrame.setVisible(true);
            }
        });

        // menuItemSessionSummary
        this.menuItemSessionSummary.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MainFrame.this.sessionSummaryFrame.setVisible(true);
            }
        });

        // menuCategory
        this.categoryManager.addListener(new CategoryManager.Listener() {
            @Override
            public void categoriesUpdated(Category[] categories, Category currentCategory) {
                int menuShortcutKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

                MainFrame.this.menuCategory.removeAll();

                // category manager
                JMenuItem menuItemCategoryManager = new JMenuItem(_("main.category_manager"));
                menuItemCategoryManager.setMnemonic(KeyEvent.VK_M);
                menuItemCategoryManager.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuShortcutKey | InputEvent.ALT_MASK));
                menuItemCategoryManager.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        MainFrame.this.categoryManagerDialog.setVisible(true);
                    }
                });
                MainFrame.this.menuCategory.add(menuItemCategoryManager);

                MainFrame.this.menuCategory.addSeparator();

                ButtonGroup categoryGroup = new ButtonGroup();

                // built-in categories
                class BuiltInCategory {
                    public final Category category;
                    public final char mnemonic;
                    public final char accelerator;

                    public BuiltInCategory(Category category, char mnemonic, char accelerator) {
                        this.category = category;
                        this.mnemonic = mnemonic;
                        this.accelerator = accelerator;
                    }
                }

                BuiltInCategory[] builtInCategories = {
                    new BuiltInCategory(categories[0], '2', '2'),
                    new BuiltInCategory(categories[1], 'R', '3'),
                    new BuiltInCategory(categories[2], 'O', 'O'),
                    new BuiltInCategory(categories[3], 'B', 'B'),
                    new BuiltInCategory(categories[4], 'F', 'F'),
                    new BuiltInCategory(categories[5], '4', '4'),
                    new BuiltInCategory(categories[6], 'B', '\0'),
                    new BuiltInCategory(categories[7], '5', '5'),
                    new BuiltInCategory(categories[8], 'B', '\0'),
                    new BuiltInCategory(categories[9], '6', '6'),
                    new BuiltInCategory(categories[10], '7', '7'),
                    new BuiltInCategory(categories[11], 'C', 'K'),
                    new BuiltInCategory(categories[12], 'M', 'M'),
                    new BuiltInCategory(categories[13], 'P', 'P'),
                    new BuiltInCategory(categories[14], 'S', '1'),
                    new BuiltInCategory(categories[15], 'M', 'G'),
                    new BuiltInCategory(categories[16], 'M', 'A'),
                };

                for (final BuiltInCategory builtInCategory : builtInCategories) {
                    JRadioButtonMenuItem menuItemCategory = new JRadioButtonMenuItem(builtInCategory.category.getDescription());
                    menuItemCategory.setMnemonic(builtInCategory.mnemonic);
                    if (builtInCategory.accelerator != '\0') {
                        menuItemCategory.setAccelerator(KeyStroke.getKeyStroke(builtInCategory.accelerator, menuShortcutKey));
                    }
                    menuItemCategory.setSelected(builtInCategory.category == currentCategory);
                    menuItemCategory.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            MainFrame.this.categoryManager.setCurrentCategory(builtInCategory.category);
                        }
                    });
                    MainFrame.this.menuCategory.add(menuItemCategory);
                    categoryGroup.add(menuItemCategory);
                }

                MainFrame.this.menuCategory.addSeparator();

                // user defined categories
                for (final Category category : categories) {
                    if (category.isUserDefined()) {
                        JRadioButtonMenuItem menuItemCategory = new JRadioButtonMenuItem(category.getDescription());
                        menuItemCategory.setSelected(category == currentCategory);
                        menuItemCategory.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                MainFrame.this.categoryManager.setCurrentCategory(category);
                            }
                        });
                        MainFrame.this.menuCategory.add(menuItemCategory);
                        categoryGroup.add(menuItemCategory);
                    }
                }
            }
        });

        // menuColorScheme
        this.menuItemColorScheme.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MainFrame.this.colorSchemeFrame.setVisible(true);
            }
        });

        // menuItemInspectionTime
        this.menuItemInspectionTime.setSelected(timerManager.isInspectionEnabled());
        this.menuItemInspectionTime.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MainFrame.this.timerManager.setInspectionEnabled(
                    MainFrame.this.menuItemInspectionTime.isSelected());
            }
        });

        // menuItemCtrlKeys
        this.menuItemCtrlKeys.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setTimerTrigger("KEYBOARD-TIMER-CONTROL");
            }
        });

        // menuItemSpaceKey
        this.menuItemSpaceKey.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setTimerTrigger("KEYBOARD-TIMER-SPACE");
            }
        });

        // menuItemStackmatTimer
        this.menuItemStackmatTimer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setTimerTrigger("STACKMAT-TIMER");
            }
        });

        // menuItemDevice
        for (final Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            Line.Info[] targetLinesInfo =
                AudioSystem.getTargetLineInfo(new Info(TargetDataLine.class, this.audioFormat));

            boolean validMixer = false;
            for (Line.Info lineInfo : targetLinesInfo) {
                if (AudioSystem.getMixer(mixerInfo).isLineSupported(lineInfo)) {
                    validMixer = true;
                    break;
                }
            }

            if (!validMixer) {
                continue;
            }

            JRadioButtonMenuItem menuItemDevice = new JRadioButtonMenuItem(mixerInfo.getName());
            menuItemDevice.setSelected(stackmatTimerInputDeviceName.equals(mixerInfo.getName()));
            menuItemDevice.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent arg0) {
                    MainFrame.this.mixerInfo = mixerInfo;
                    MainFrame.this.configurationManager.setConfiguration(
                        "STACKMAT-TIMER-INPUT-DEVICE", mixerInfo.getName());

                    String timerTrigger =
                        MainFrame.this.configurationManager.getConfiguration("TIMER-TRIGGER");
                    if (timerTrigger.equals("STACKMAT-TIMER")) {
                        setTimerTrigger("STACKMAT-TIMER");
                    }
                }
            });
            this.stackmatTimerInputDevice.add(menuItemDevice);
            this.stackmatTimerInputDeviceGroup.add(menuItemDevice);

            if (MainFrame.this.mixerInfo == null) {
                menuItemDevice.setSelected(true);
                MainFrame.this.mixerInfo = mixerInfo;
                this.configurationManager.setConfiguration(
                    "STACKMAT-TIMER-INPUT-DEVICE", mixerInfo.getName());
            }
        }

        // menuItemAbout
        this.menuItemAbout.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                AboutDialog aboutDialog = new AboutDialog(MainFrame.this, true);
                aboutDialog.setLocationRelativeTo(null);
                aboutDialog.setVisible(true);
            }
        });

        // menuItemExport
        this.exportSolutionsToCSV.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                    ExportUtils.ExportToFile(solutionManager);
            }
        });

        // labelMessage
        this.messageManager.addListener(new MessageManager.Listener() {
            @Override
            public void messagesCleared() {
                MainFrame.this.labelMessage.setPreferredSize(new Dimension());
                MainFrame.this.labelMessage.setVisible(false);
            }

            @Override
            public void messageReceived(MessageType messageType, String message) {
                MainFrame.this.labelMessage.setPreferredSize(new Dimension(10000, 30));
                if (messageType == MessageType.INFORMATION) {
                    MainFrame.this.labelMessage.setBackground(new Color(0x45, 0x73, 0xD5));
                } else if (messageType == MessageType.ERROR) {
                    MainFrame.this.labelMessage.setBackground(new Color(0xFF, 0x40, 0x40));
                }
                MainFrame.this.labelMessage.setText(message);
                MainFrame.this.labelMessage.setVisible(true);
            }
        });
    }

    private void setTimerTrigger(String timerTriggerId) {
        if (timerTriggerId.equals("KEYBOARD-TIMER-CONTROL")) {
            this.menuItemCtrlKeys.setSelected(true);
            this.timerManager.setTimer(
                new ControlKeysTimer(this, this.timerManager));
        } else if (timerTriggerId.equals("KEYBOARD-TIMER-SPACE")) {
            this.menuItemSpaceKey.setSelected(true);
            this.timerManager.setTimer(
                new SpaceKeyTimer(this, this.timerManager));
        } else if (timerTriggerId.equals("STACKMAT-TIMER")) {
            if (this.mixerInfo != null) {
                TargetDataLine targetDataLine = null;
                try {
                    targetDataLine = AudioSystem.getTargetDataLine(MainFrame.this.audioFormat, MainFrame.this.mixerInfo);
                    targetDataLine.open(MainFrame.this.audioFormat);
                    this.menuItemStackmatTimer.setSelected(true);
                    this.timerManager.setTimer(
                        new StackmatTimer(targetDataLine, this.timerManager));
                } catch (LineUnavailableException e) {
                    // select the default timer
                    this.menuItemSpaceKey.setSelected(true);
                    this.timerManager.setTimer(
                        new SpaceKeyTimer(this, this.timerManager));

                    MainFrame.this.messageManager.enqueueMessage(
                        MessageType.ERROR,
                        _("main.stackmat_timer_error_message"));
                }
            } else {
                // select the default timer
                this.menuItemSpaceKey.setSelected(true);
                this.timerManager.setTimer(
                    new SpaceKeyTimer(this, this.timerManager));

                MainFrame.this.messageManager.enqueueMessage(
                    MessageType.ERROR,
                    _("main.stackmat_timer_error_message"));
            }
        }
    }

    private void createComponents() {
        int menuShortcutKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        // menuBar
        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        // menuFile
        this.menuFile = new JMenu(_("main.file"));
        this.menuFile.setMnemonic(KeyEvent.VK_F);
        menuBar.add(this.menuFile);

        // menuItemAddSolution
        this.menuItemAddSolution = new JMenuItem(_("main.add_solution"));
        this.menuItemAddSolution.setMnemonic(KeyEvent.VK_A);
        this.menuItemAddSolution.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, menuShortcutKey));
        this.menuFile.add(this.menuItemAddSolution);

        this.menuFile.addSeparator();

        // menuItemExit
        this.menuItemExit = new JMenuItem(_("main.exit"));
        this.menuItemExit.setMnemonic(KeyEvent.VK_X);
        this.menuFile.add(this.menuItemExit);

        // menuView
        JMenu menuView = new JMenu(_("main.view"));
        menuView.setMnemonic(KeyEvent.VK_V);
        menuBar.add(menuView);

        // menuItemTips
        this.menuItemTips = new JMenuItem(_("main.tips"));
        this.menuItemTips.setMnemonic(KeyEvent.VK_T);
        this.menuItemTips.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, menuShortcutKey | KeyEvent.ALT_MASK));
        menuView.add(this.menuItemTips);

        // menuItemScrambleQueue
        this.menuItemScrambleQueue = new JMenuItem(_("main.scramble_queue"));
        this.menuItemScrambleQueue.setMnemonic(KeyEvent.VK_Q);
        this.menuItemScrambleQueue.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, menuShortcutKey | KeyEvent.ALT_MASK));
        menuView.add(this.menuItemScrambleQueue);

        // menuItemHistory
        this.menuItemHistory = new JMenuItem(_("main.history"));
        this.menuItemHistory.setMnemonic(KeyEvent.VK_H);
        this.menuItemHistory.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, menuShortcutKey | KeyEvent.ALT_MASK));
        menuView.add(this.menuItemHistory);

        // menuItemSessionSummary
        this.menuItemSessionSummary = new JMenuItem(_("main.session_summary"));
        this.menuItemSessionSummary.setMnemonic(KeyEvent.VK_S);
        this.menuItemSessionSummary.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuShortcutKey | KeyEvent.ALT_MASK));
        menuView.add(this.menuItemSessionSummary);

        // menuCategory
        this.menuCategory = new JMenu(_("main.category"));
        this.menuCategory.setMnemonic(KeyEvent.VK_C);
        menuBar.add(this.menuCategory);

        // menuOptions
        JMenu menuOptions = new JMenu(_("main.options"));
        menuOptions.setMnemonic(KeyEvent.VK_O);
        menuBar.add(menuOptions);

        // menuColorScheme
        this.menuItemColorScheme = new JMenuItem(_("main.color_scheme"));
        this.menuItemColorScheme.setMnemonic(KeyEvent.VK_C);
        this.menuItemColorScheme.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K, menuShortcutKey | KeyEvent.ALT_MASK));
        menuOptions.add(this.menuItemColorScheme);

        // menuItemInspectionTime
        this.menuItemInspectionTime = new JCheckBoxMenuItem(_("main.inspection_time"));
        this.menuItemInspectionTime.setMnemonic(KeyEvent.VK_I);
        this.menuItemInspectionTime.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, menuShortcutKey | KeyEvent.ALT_MASK));
        menuOptions.add(this.menuItemInspectionTime);

        // menuTimerTrigger
        JMenu menuTimerTrigger = new JMenu(_("main.timer_trigger"));
        menuTimerTrigger.setMnemonic(KeyEvent.VK_T);
        menuOptions.add(menuTimerTrigger);
        ButtonGroup timerTriggerGroup = new ButtonGroup();

        // menuItemCtrlKeys
        this.menuItemCtrlKeys = new JRadioButtonMenuItem(_("main.ctrl_keys"));
        this.menuItemCtrlKeys.setMnemonic(KeyEvent.VK_C);
        this.menuItemCtrlKeys.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuShortcutKey));
        menuTimerTrigger.add(this.menuItemCtrlKeys);
        timerTriggerGroup.add(this.menuItemCtrlKeys);

        // menuItemSpaceKey
        this.menuItemSpaceKey = new JRadioButtonMenuItem(_("main.space_key"));
        this.menuItemSpaceKey.setMnemonic(KeyEvent.VK_S);
        this.menuItemSpaceKey.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, menuShortcutKey));
        menuTimerTrigger.add(this.menuItemSpaceKey);
        timerTriggerGroup.add(this.menuItemSpaceKey);

        // menuItemStackmatTimer
        this.menuItemStackmatTimer = new JRadioButtonMenuItem(_("main.stackmat_timer"));
        this.menuItemStackmatTimer.setMnemonic(KeyEvent.VK_T);
        this.menuItemStackmatTimer.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, menuShortcutKey));
        menuTimerTrigger.add(this.menuItemStackmatTimer);
        timerTriggerGroup.add(this.menuItemStackmatTimer);

        // menuStackmatTimerInputDevice
        this.stackmatTimerInputDevice = new JMenu(_("main.stackmat_timer_input_device"));
        menuTimerTrigger.setMnemonic(KeyEvent.VK_S);
        menuOptions.add(this.stackmatTimerInputDevice);
        this.stackmatTimerInputDeviceGroup = new ButtonGroup();

        //menuTools
        this.toolsMenu = new JMenu(_("main.tools"));
        this.exportSolutionsToCSV = new JMenuItem(_("main.export_csv"));
        toolsMenu.add(this.exportSolutionsToCSV);
        menuBar.add(this.toolsMenu);

        //menuHelp
        JMenu menuHelp = new JMenu(_("main.help"));
        menuHelp.setMnemonic(KeyEvent.VK_H);
        menuBar.add(menuHelp);

        // menuItemAbout
        this.menuItemAbout = new JMenuItem(_("main.about"));
        this.menuItemAbout.setMnemonic(KeyEvent.VK_A);
        menuHelp.add(this.menuItemAbout);

        // panelMain
        JPanel panelMain = new JPanel(
            new MigLayout(
                "fill, hidemode 1, insets 2 3 2 3",
                "[fill]",
                "[pref!][pref!][fill, growprio 200][pref!]"));
        add(panelMain);

        // labelMessage
        this.labelMessage = new JLabel();
        this.labelMessage.setPreferredSize(new Dimension());
        this.labelMessage.setOpaque(true);
        this.labelMessage.setHorizontalAlignment(JLabel.CENTER);
        this.labelMessage.setForeground(new Color(0xFF, 0xFF, 0xFF));
        this.labelMessage.setVisible(false);
        panelMain.add(this.labelMessage, "wrap");

        // panelScramble
        this.scramblePanel = new ScramblePanel(this.scrambleManager);
        panelMain.add(this.scramblePanel, "wrap");

        // timer panel
        this.timerPanel = new TimerPanel(this.timerManager);
        panelMain.add(this.timerPanel, "wrap");

        // times scroll pane
        this.timesScrollPane = new TimesScrollPane(this.solutionManager, this.sessionManager);
        this.timesScrollPane.setBorder(BorderFactory.createTitledBorder(_("main.times")));
        this.timesScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panelMain.add(this.timesScrollPane, "w 30%, growy, gapright 0, split 3");

        // statistics panel
        this.statisticsPanel = new StatisticsPanel(this.sessionManager);
        this.statisticsPanel.setBorder(BorderFactory.createTitledBorder(_("main.session_statistics")));
        panelMain.add(this.statisticsPanel, "w 40%, growy, gapright 0");

        // scramble viewer panel
        this.scrambleViewerPanel = new ScrambleViewerPanel(
            this.puzzleProvider,
            this.colorManager,
            this.scramblerProvider,
            this.scrambleManager);
        this.scrambleViewerPanel.setBorder(BorderFactory.createTitledBorder(_("main.scramble")));
        panelMain.add(this.scrambleViewerPanel, "w 30%, growy");

        this.scramblePanel.setScrambleViewerPanel(this.scrambleViewerPanel);

        Image icon = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/com/puzzletimer/resources/icon.png"));

        // tips frame
        this.tipsFrame = new TipsFrame(
            this.puzzleProvider,
            this.tipProvider,
            this.scramblerProvider,
            this.categoryManager,
            this.scrambleManager);
        this.tipsFrame.setLocationRelativeTo(null);
        this.tipsFrame.setIconImage(icon);

        // scramble queue frame
        this.scrambleQueueFrame = new ScrambleQueueFrame(
            this.scrambleParserProvider,
            this.scramblerProvider,
            this.categoryManager,
            this.scrambleManager);
        this.scrambleQueueFrame.setLocationRelativeTo(null);
        this.scrambleQueueFrame.setIconImage(icon);

        // history frame
        this.historyFrame = new HistoryFrame(
            this.scramblerProvider,
            this.scrambleParserProvider,
            this.categoryManager,
            this.scrambleManager,
            this.solutionManager,
            this.sessionManager);
        this.historyFrame.setLocationRelativeTo(null);
        this.historyFrame.setIconImage(icon);

        // session summary frame
        this.sessionSummaryFrame = new SessionSummaryFrame(
            this.categoryManager,
            this.sessionManager);
        this.sessionSummaryFrame.setLocationRelativeTo(null);
        this.sessionSummaryFrame.setIconImage(icon);

        // category manager dialog
        this.categoryManagerDialog = new CategoryManagerFrame(
            this.puzzleProvider,
            this.scramblerProvider,
            this.categoryManager,
            this.tipProvider);
        this.categoryManagerDialog.setLocationRelativeTo(null);
        this.categoryManagerDialog.setIconImage(icon);

        // color scheme frame
        this.colorSchemeFrame = new ColorSchemeFrame(
            this.puzzleProvider,
            this.colorManager);
        this.colorSchemeFrame.setLocationRelativeTo(null);
        this.colorSchemeFrame.setIconImage(icon);
    }
}
