package src.pas.tetris.agents;


import java.util.ArrayList;
// SYSTEM IMPORTS
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.junit.experimental.max.MaxCore;

// JAVA PROJECT IMPORTS
import edu.bu.pas.tetris.agents.QAgent;
import edu.bu.pas.tetris.agents.TrainerAgent.GameCounter;
import edu.bu.pas.tetris.game.Board;
import edu.bu.pas.tetris.game.Game.GameView;
import edu.bu.pas.tetris.game.minos.Mino;
import edu.bu.pas.tetris.linalg.Matrix;
import edu.bu.pas.tetris.nn.Model;
import edu.bu.pas.tetris.nn.LossFunction;
import edu.bu.pas.tetris.nn.Optimizer;
import edu.bu.pas.tetris.nn.models.Sequential;
import edu.bu.pas.tetris.nn.layers.Dense; // fully connected layer
import edu.bu.pas.tetris.nn.layers.ReLU;  // some activations (below too)
import edu.bu.pas.tetris.nn.layers.Tanh;
import edu.bu.pas.tetris.nn.layers.Sigmoid;
import edu.bu.pas.tetris.training.data.Dataset;
import edu.bu.pas.tetris.utils.Pair;


public class TetrisQAgent
    extends QAgent
{

    public static final double EXPLORATION_PROB = 0.05;

    private Random random;

    public TetrisQAgent(String name)
    {
        super(name);
        this.random = new Random(12345); // optional to have a seed
    }

    public Random getRandom() { return this.random; }

    @Override
    public Model initQFunction()
    {
        // System.out.println("initQFunction called!");
        // build a single-hidden-layer feedforward network
        // this example will create a 3-layer neural network (1 hidden layer)
        // in this example, the input to the neural network is the
        // image of the board unrolled into a giant vector
        final int numPixelsInImage = Board.NUM_ROWS * Board.NUM_COLS;
        final int numFeatures = Board.NUM_COLS + 3;
        final int hiddenDim1 = 64;
        final int hiddenDim2 = 32;
        final int outDim = 1;

        Sequential qFunction = new Sequential();
        qFunction.add(new Dense(numFeatures, hiddenDim1));
        qFunction.add(new Tanh());
        qFunction.add(new Dense(hiddenDim1, hiddenDim2));
        qFunction.add(new Tanh());
        qFunction.add(new Dense(hiddenDim2, outDim));

        return qFunction;
    }

    /**
        This function is for you to figure out what your features
        are. This should end up being a single row-vector, and the
        dimensions should be what your qfunction is expecting.
        One thing we can do is get the grayscale image
        where squares in the image are 0.0 if unoccupied, 0.5 if
        there is a "background" square (i.e. that square is occupied
        but it is not the current piece being placed), and 1.0 for
        any squares that the current piece is being considered for.
        
        We can then flatten this image to get a row-vector, but we
        can do more than this! Try to be creative: how can you measure the
        "state" of the game without relying on the pixels? If you were given
        a tetris game midway through play, what properties would you look for?
     */
    @Override
    public Matrix getQFunctionInput(final GameView game,
                                    final Mino potentialAction)
    {
        Matrix flattenedImage = null;
        try
        {
            flattenedImage = game.getGrayscaleImage(potentialAction).flatten();
        } catch(Exception e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
        int rows = flattenedImage.getShape().getNumRows();
        int cols = flattenedImage.getShape().getNumCols();

        double[] columnHeights = new double[cols];
        double holes = 0;
        double totalHeight = 0;

        for(int col = 0; col < cols; col++){
            boolean foundBlock = false;
            int colHeight = 0;
            int colHoles = 0;

            for(int row = 0; row < rows; row++){
                double cellValue = flattenedImage.get(row, col);

                //occupied cell
                if(cellValue >= .5){
                    if(!foundBlock){
                        colHeight = rows - row;
                        foundBlock = true;
                    }
                }else{
                    if(foundBlock){
                        colHoles++;
                    }
                }
            }
            columnHeights[col] = colHeight;
            totalHeight += colHeight;
            holes += colHoles;
        }

        int clearLines = 0;
        for(int row = 0; row < rows; row++){
            boolean rowFilled = true;
            for(int col = 0; col < cols; col++){
                if(flattenedImage.get(row, col) < .5){
                    rowFilled = false;
                    break;
                }
            }
            if(rowFilled){
                clearLines++;
            }
        }

        ArrayList<Double> features = new ArrayList<>();
        for(double heights : columnHeights){
            features.add(heights);
        }
        features.add(totalHeight);
        features.add(holes);
        features.add((double)clearLines);

        Matrix featureMatrix = Matrix.zeros(1, features.size());
        for(int i = 0; i < features.size(); i++){
            featureMatrix.set(0, i, features.get(i));
        }
        return featureMatrix;
    }

    /**
     * This method is used to decide if we should follow our current policy
     * (i.e. our q-function), or if we should ignore it and take a random action
     * (i.e. explore).
     *
     * Remember, as the q-function learns, it will start to predict the same "good" actions
     * over and over again. This can prevent us from discovering new, potentially even
     * better states, which we want to do! So, sometimes we should ignore our policy
     * and explore to gain novel experiences.
     *
     * The current implementation chooses to ignore the current policy around 5% of the time.
     * While this strategy is easy to implement, it often doesn't perform well and is
     * really sensitive to the EXPLORATION_PROB. I would recommend devising your own
     * strategy here.
     */
    @Override
    public boolean shouldExplore(final GameView game,
                                 final GameCounter gameCounter)
    {
        // System.out.println("cycleIdx=" + gameCounter.getCurrentCycleIdx() + "\tgameIdx=" + gameCounter.getCurrentGameIdx());
        double baseExploreProb = EXPLORATION_PROB;
        long currentGameCount = gameCounter.getCurrentGameIdx();
        //prob decays as more games are played
        double decayedExploreProb = baseExploreProb / (1.0 + 0.001 * currentGameCount);

        return this.getRandom().nextDouble() <= decayedExploreProb;
    }

    /**
     * This method is a counterpart to the "shouldExplore" method. Whenever we decide
     * that we should ignore our policy, we now have to actually choose an action.
     *
     * You should come up with a way of choosing an action so that the model gets
     * to experience something new. The current implemention just chooses a random
     * option, which in practice doesn't work as well as a more guided strategy.
     * I would recommend devising your own strategy here.
     */
    @Override
    public Mino getExplorationMove(final GameView game)
    {
        List<Mino> finalPositions = game.getFinalMinoPositions();
        int numPositions = finalPositions.size();
        double[] weights = new double[numPositions];
        double totalWeight = 0.0;
        
        for(int i = 0; i < numPositions; i++){
            Mino move = finalPositions.get(i);
            Matrix boardImage;
            try {
                boardImage = game.getGrayscaleImage(move);
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
            int rows = boardImage.getShape().getNumRows();
            int cols = boardImage.getShape().getNumCols();
            
            double totalHeight = 0.0;
            double holes = 0.0;
            for(int col = 0; col < cols; col++){
                boolean blockFound = false;
                int colHeight = 0;
                int columnHoles = 0;

                for(int row = 0; row < rows; row++){
                    double cellValue = boardImage.get(row, col);
    
                    if(cellValue >= 0.5){ 
                        if(!blockFound){
                            colHeight = rows - row;
                            blockFound = true;
                        }
                    }else{
                        if(blockFound){
                            columnHoles++;
                        }
                    }
                }
                totalHeight += colHeight;
                holes += columnHoles;
            }
            
            //higher rarity score means rarer to choose that move
            double rarityScore = totalHeight + holes;
            double weight = rarityScore + 1;
            weights[i] = weight;
            totalWeight += weight;
        }

        double r = this.getRandom().nextDouble() * totalWeight;
        double sum = 0.0;
        int chosenIdx = 0;
        for(int i = 0; i < weights.length; i++){
            sum += weights[i];
            if(r <= sum){
                chosenIdx = i;
                break;
            }
        }

        return game.getFinalMinoPositions().get(chosenIdx);
    }

    /**
     * This method is called by the TrainerAgent after we have played enough training games.
     * In between the training section and the evaluation section of a cycle, we need to use
     * the exprience we've collected (from the training games) to improve the q-function.
     *
     * You don't really need to change this method unless you want to. All that happens
     * is that we will use the experiences currently stored in the replay buffer to update
     * our model. Updates (i.e. gradient descent updates) will be applied per minibatch
     * (i.e. a subset of the entire dataset) rather than in a vanilla gradient descent manner
     * (i.e. all at once)...this often works better and is an active area of research.
     *
     * Each pass through the data is called an epoch, and we will perform "numUpdates" amount
     * of epochs in between the training and eval sections of each cycle.
     */
    @Override
    public void trainQFunction(Dataset dataset,
                               LossFunction lossFunction,
                               Optimizer optimizer,
                               long numUpdates)
    {
        for(int epochIdx = 0; epochIdx < numUpdates; ++epochIdx)
        {
            dataset.shuffle();
            Iterator<Pair<Matrix, Matrix> > batchIterator = dataset.iterator();

            while(batchIterator.hasNext())
            {
                Pair<Matrix, Matrix> batch = batchIterator.next();

                try
                {
                    Matrix YHat = this.getQFunction().forward(batch.getFirst());

                    optimizer.reset();
                    this.getQFunction().backwards(batch.getFirst(),
                                                  lossFunction.backwards(YHat, batch.getSecond()));
                    optimizer.step();
                } catch(Exception e)
                {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
        }
    }

    /**
     * This method is where you will devise your own reward signal. Remember, the larger
     * the number, the more "pleasurable" it is to the model, and the smaller the number,
     * the more "painful" to the model.
     *
     * This is where you get to tell the model how "good" or "bad" the game is.
     * Since you earn points in this game, the reward should probably be influenced by the
     * points, however this is not all. In fact, just using the points earned this turn
     * is a **terrible** reward function, because earning points is hard!!
     *
     * I would recommend you to consider other ways of measuring "good"ness and "bad"ness
     * of the game. For instance, the higher the stack of minos gets....generally the worse
     * (unless you have a long hole waiting for an I-block). When you design a reward
     * signal that is less sparse, you should see your model optimize this reward over time.
     */
    @Override
    public double getReward(final GameView game)
    {
        return game.getScoreThisTurn();
    }

}
