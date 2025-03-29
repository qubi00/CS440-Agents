package src.pas.pokemon.agents;


// SYSTEM IMPORTS....feel free to add your own imports here! You may need/want to import more from the .jar!
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.SwitchMove;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.utils.Pair;
import src.pas.pokemon.agents.TreeTraversalAgent.MoveCache.BoundType;
import edu.bu.pas.pokemon.core.enums.Flag;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.core.callbacks.Callback;
import edu.bu.pas.pokemon.core.callbacks.DoDamageCallback;
import edu.bu.pas.pokemon.core.callbacks.MultiCallbackCallback;
import edu.bu.pas.pokemon.core.callbacks.ResetLastDamageDealtCallback;
import java.util.Random;
import java.util.Set;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


// JAVA PROJECT IMPORTS


public class TreeTraversalAgent
    extends Agent
{
    public static final double low_prob = 0.01;
    public static int max_depth = 2;

    public static class MoveCache {
        public enum BoundType {
            EXACT, LOWER_BOUND, UPPER_BOUND
        }
    
        public final int depth;
        public final MoveView bestMove;
        public final double value;
        public final BoundType boundType;
    
        public MoveCache(int depth, MoveView bestMove, double value, BoundType boundType){
            this.depth = depth;
            this.bestMove = bestMove;
            this.value = value;
            this.boundType = boundType;
        }
    }

    public static class TreeNode {
    
        private BattleView state;
        private MoveView move;
        private double probability;
        private boolean isMax;
    
        public TreeNode(BattleView state, MoveView move, double probability, boolean isMax) {
            this.state = state;
            this.move = move;
            this.probability = probability;
            this.isMax = isMax;
        }
    
    
        public BattleView getState() {return state;}
        public MoveView getMove() {return move;}
        public double getProbability() {return probability;}

    }
   

	public class StochasticTreeSearcher
        extends Object
        implements Callable<Pair<MoveView, Long> >  // so this object can be run in a background thread
	{

        private Map<BattleView, MoveCache> moveCache = new HashMap<>();

		private final BattleView rootView;
        private final int maxDepth;
        private final int myTeamIdx;

        // If you change the parameters of the constructor, you will also have to change
        // the getMove(...) method of TreeTraversalAgent!
		public StochasticTreeSearcher(BattleView rootView, int maxDepth, int myTeamIdx)
        {
            this.rootView = rootView;
            this.maxDepth = maxDepth;
            this.myTeamIdx = myTeamIdx;
        }

        // Getter methods. Since the default fields are declared final, we don't need setters
        // but if you make any fields that aren't final you should give them setters!
		public BattleView getRootView() { return this.rootView; }
        public int getMaxDepth() { return this.maxDepth; }
        public int getMyTeamIdx() { return this.myTeamIdx; }


		/*
		 * @param node the node to perform the search on (i.e. the root of the entire tree)
		 * @return The MoveView that your agent should execute
		 */
        public MoveView stochasticTreeSearch(BattleView rootView, int depth)
        {

            int myIdx = getMyTeamIdx();
            int opIdx;
            if(myIdx == 0){
                opIdx = 1;
            }else{
                opIdx = 0;
            }

            PokemonView active = rootView.getTeamView(myIdx).getActivePokemonView();
            List<MoveView> moves = active.getAvailableMoves();
            if (moves.isEmpty()) return null;

            MoveView bestMove = null;
            double bestValue = Double.NEGATIVE_INFINITY;

            for(MoveView move : moves){
                double moveValue = 0.0;
                List<Pair<Double, BattleView>> outcomes = move.getPotentialEffects(rootView, myIdx, opIdx);
                for(Pair<Double, BattleView> outcome : outcomes) {
                    TreeNode child = new TreeNode(outcome.getSecond(), move, outcome.getFirst(), false);
                    moveValue += outcome.getFirst() * expectimax(child, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
                }
                if(moveValue > bestValue){
                    bestValue = moveValue;
                    bestMove = move;
                }
            }
            return bestMove;
        }


        public double expectimax(TreeNode node, int depth, double alpha, double beta) {
            int myIdx = getMyTeamIdx();
            int opIdx;
            if(myIdx == 0){
                opIdx = 1;
            }else{
                opIdx = 0;
            }

            if (moveCache.containsKey(node.getState())) {
                MoveCache entry = moveCache.get(node.getState());
                if (entry.depth >= depth) {
                    switch(entry.boundType) {
                        case EXACT:
                            return entry.value;
                        case LOWER_BOUND:
                            alpha = Math.max(alpha, entry.value);
                            break;
                        case UPPER_BOUND:
                            beta = Math.min(beta, entry.value);
                            break;
                    }
                    if (alpha >= beta) {
                        return entry.value;
                    }
                }
            }

            if(depth == 0 || node.getState().isOver()){
                double eval = evaluateState(node.getState());
                moveCache.put(node.getState(), new MoveCache(depth, node.getMove(), eval, MoveCache.BoundType.EXACT));
                return eval;
            }

            double result;
            if(node.isMax){
                //max score
                double best = Double.NEGATIVE_INFINITY;
                PokemonView active = node.state.getTeamView(myIdx).getActivePokemonView();
                List<MoveView> moves = active.getAvailableMoves();
                if(moves.isEmpty()){
                    return evaluateState(node.state);
                }
                for(MoveView move : moves){
                    double value = 0.0;
                    List<Pair<Double, BattleView>> outcomes = move.getPotentialEffects(node.state, myIdx, opIdx);
                    for(Pair<Double, BattleView> outcome : outcomes){
                        TreeNode child = new TreeNode(outcome.getSecond(), move, outcome.getFirst(), false);
                        value += outcome.getFirst() * expectimax(child, depth - 1, alpha, beta);
                    }
                    best = Math.max(best, value);
                    alpha = Math.max(alpha, best);
                    if(alpha >= beta){
                        break;
                    }
                }
                result = best;
            }else{
                double expected = 0.0;
                PokemonView oppActive = node.state.getTeamView(opIdx).getActivePokemonView();
                List<MoveView> moves = oppActive.getAvailableMoves();
                int n = moves.size();
                if(n == 0){
                    return evaluateState(node.state);
                }
                
                for(int i = 0; i < n; i++){
                    MoveView move = moves.get(i);
                    double value = 0.0;
                    List<Pair<Double, BattleView>> outcomes = move.getPotentialEffects(node.state, opIdx, myIdx);
                    for(Pair<Double, BattleView> outcome : outcomes){
                        TreeNode child = new TreeNode(outcome.getSecond(), move, outcome.getFirst(), true);
                        value += outcome.getFirst() * expectimax(child, depth - 1, alpha, beta);
                    }
                    double weighted = value / n;
                    expected += weighted;
                    
                    //max possible contribution
                    int remaining = n - i - 1;
                    double maxPossible = expected + remaining * beta / n;
                    if(maxPossible < alpha){
                        break;
                    }
                }
                result = expected;
            }
            MoveCache.BoundType boundType;
            if(result <= alpha) {
                boundType = MoveCache.BoundType.UPPER_BOUND;
            } else if(result >= beta) {
                boundType = MoveCache.BoundType.LOWER_BOUND;
            } else {
                boundType = MoveCache.BoundType.EXACT;
            }
            moveCache.put(node.getState(), new MoveCache(depth, node.getMove(), result, boundType));

            return result;
        }


        //for heuristic
        private double evaluateState(BattleView state) {
            double score = 0.0;
            int myIdx = getMyTeamIdx();
            int opIdx;
            if(myIdx == 0){
                opIdx = 1;
            }else{
                opIdx = 0;
            }
    
            Team.TeamView myTeam = state.getTeamView(myIdx);
            Team.TeamView oppTeam = state.getTeamView(opIdx);
    
            for(int i = 0; i < myTeam.size(); i++){
                PokemonView pokemon = myTeam.getPokemonView(i);
                double hpRatio = (double) pokemon.getCurrentStat(Stat.HP) / pokemon.getInitialStat(Stat.HP);
                score += (100 * hpRatio);
            }
            for(int i = 0; i < oppTeam.size(); i++){
                PokemonView pokemon = oppTeam.getPokemonView(i);
                double hpRatio = (double) pokemon.getCurrentStat(Stat.HP) / pokemon.getInitialStat(Stat.HP);
                score -= (100 * hpRatio);
            }
            return score;
        }


        @Override
        public Pair<MoveView, Long> call() throws Exception
        {
            double startTime = System.nanoTime();

            MoveView move = this.stochasticTreeSearch(this.getRootView(), maxDepth);
            double endTime = System.nanoTime();

            return new Pair<MoveView, Long>(move, (long)((endTime-startTime)/1000000));
        }
		
	}

	private final int maxDepth;
    private long maxThinkingTimePerMoveInMS;

	public TreeTraversalAgent()
    {
        super();
        this.maxThinkingTimePerMoveInMS = 180000 * 2; // 6 min/move
        this.maxDepth = 2; // set this however you want
    }

    /**
     * Some constants
     */
    public int getMaxDepth() { return this.maxDepth; }
    public long getMaxThinkingTimePerMoveInMS() { return this.maxThinkingTimePerMoveInMS; }

    public double getPokeEffectiveness(String moveType, String defenderType) {
        switch(moveType){
            case "NORMAL":
                if(defenderType.equals("ROCK")){
                    return .5;
                }else if(defenderType.equals("GHOST")){
                    return 0.0;
                }
                return 1.0;
            case "FIRE":
                if(defenderType.equals("GRASS")||
                defenderType.equals("ICE")||
                defenderType.equals("BUG")){
                    return 2.0;
                }else if(defenderType.equals("FIRE")||
                defenderType.equals("WATER")||
                defenderType.equals("ROCK")||
                defenderType.equals("DRAGON")){
                    return .5;
                }
                return 1.0;
            case "WATER":
                if(defenderType.equals("FIRE")||
                defenderType.equals("GROUND")||
                defenderType.equals("ROCK")){
                    return 2.0;
                }else if(defenderType.equals("WATER")||
                defenderType.equals("GRASS")||
                defenderType.equals("DRAGON")){
                    return .5;
                }
                return 1.0;
            case "ELECTRIC":
                if(defenderType.equals("WATER")||
                defenderType.equals("FLYING")){
                    return 2.0;
                }else if(defenderType.equals("ELECTRIC")||
                defenderType.equals("GRASS")||
                defenderType.equals("DRAGON")){
                    return .5;
                }else if(defenderType.equals("GROUND")){
                    return 0;
                }
                return 1.0;
            case "GRASS":
                if(defenderType.equals("WATER")||
                defenderType.equals("GROUND")||
                defenderType.equals("ROCK")){
                    return 2.0;
                }else if(defenderType.equals("FIRE")||
                defenderType.equals("GRASS")||
                defenderType.equals("FLYING")||
                defenderType.equals("BUG")||
                defenderType.equals("DRAGON")||
                defenderType.equals("POISON")){
                    return .5;
                }
                return 1.0;
            case "ICE":
                if(defenderType.equals("GRASS")||
                defenderType.equals("GROUND")||
                defenderType.equals("DRAGON")||
                defenderType.equals("FLYING")){
                    return 2.0;
                }else if(defenderType.equals("WATER")||
                defenderType.equals("ICE")){
                    return .5;
                }
                return 1.0;
            case "FIGHTING":
                if(defenderType.equals("NORMAL")||
                defenderType.equals("ICE")||
                defenderType.equals("ROCK")){
                    return 2.0;
                }else if(defenderType.equals("POISON")||
                defenderType.equals("PSYCHIC")||
                defenderType.equals("BUG")||
                defenderType.equals("FLYING")){
                    return .5;
                }else if(defenderType.equals("GHOST")){
                    return 0;
                }
                return 1.0;
            case "POISON":
                if(defenderType.equals("GRASS")||
                defenderType.equals("BUG")){
                    return 2.0;
                }else if(defenderType.equals("POISON")||
                defenderType.equals("GROUND")||
                defenderType.equals("ROCK")||
                defenderType.equals("GHOST")){
                    return .5;
                }
                return 1.0;
            case "GROUND":
                if(defenderType.equals("FIRE")||
                defenderType.equals("ELECTRIC")||
                defenderType.equals("POISON")||
                defenderType.equals("ROCK")){
                    return 2.0;
                }else if(defenderType.equals("GRASS")||
                defenderType.equals("BUG")){
                    return .5;
                }else if(defenderType.equals("FLYING")){
                    return 0.0;
                }
                return 1;
            case "FLYING":
                if(defenderType.equals("GRASS")||
                defenderType.equals("FIGHTING")||
                defenderType.equals("BUG")){
                    return 2.0;
                }else if(defenderType.equals("ELECTRIC")||
                defenderType.equals("ROCK")){
                    return .5;
                }
                return 1;
            case "PSYCHIC":
                if(defenderType.equals("FIGHTING")||
                defenderType.equals("POISON")){
                    return 2.0;
                }else if(defenderType.equals("PSYCHIC")){
                    return .5;
                }
                return 1;
            case "BUG":
                if(defenderType.equals("GRASS")||
                defenderType.equals("POISON")||
                defenderType.equals("PSYCHIC")){
                    return 2.0;
                }else if(defenderType.equals("FIRE")||
                defenderType.equals("FLYING")||
                defenderType.equals("GHOST")||
                defenderType.equals("FIGHTING")){
                    return .5;
                }
                return 1;
            case "ROCK":
                if(defenderType.equals("FIRE")||
                defenderType.equals("ICE")||
                defenderType.equals("FLYING")||
                defenderType.equals("BUG")){
                    return 2.0;
                }else if(defenderType.equals("FIGHTING")||
                defenderType.equals("GROUND")){
                    return .5;
                }
                return 1;
            case "GHOST":
                if(defenderType.equals("GHOST")){
                    return 2.0;
                }else if(defenderType.equals("NORMAL")||
                defenderType.equals("PSYCHIC")){
                    return 0;
                }
                return 1;
            case "DRAGON":
                if(defenderType.equals("DRAGON")){
                    return 2.0;
                }
                return 1;
        }
        return 1.0;
    }

    @Override
    public Integer chooseNextPokemon(BattleView view)
    {
        
        //can improve, currently just chooses next available

        // It is likely a good idea to expand a bunch of trees with different choices as the active pokemon on your
        // team, and see which pokemon is your best choice by comparing the values of the root nodes.
        List<Pair<Integer, Double>> effectivenessList = new ArrayList<>();

        for(int idx = 0; idx < this.getMyTeamView(view).size(); ++idx)
        {   
            if(!this.getMyTeamView(view).getPokemonView(idx).hasFainted())
            {
                Type mType1 = this.getMyTeamView(view).getPokemonView(idx).getCurrentType1();
                Type oType1 = this.getOpponentTeamView(view).getActivePokemonView().getCurrentType1();
                double effectiveness = getPokeEffectiveness(mType1.toString(), oType1.toString());
                effectivenessList.add(new Pair<> (idx, effectiveness));
            }
        }
        effectivenessList.sort(Comparator.comparingDouble(Pair::getSecond));

        return effectivenessList.get(0).getFirst();
    }

    /**
     * This method is responsible for getting a move selected via the minimax algorithm.
     * There is some setup for this to work, namely making sure the agent doesn't run out of time.
     * Please do not modify.
     */
    @Override
    public MoveView getMove(BattleView battleView)
    {

        // will run the minimax algorithm in a background thread with a timeout
        ExecutorService backgroundThreadManager = Executors.newSingleThreadExecutor();

        // preallocate so we don't spend precious time doing it when we are recording duration
        MoveView move = null;
        long durationInMs = 0;

        // this obj will run in the background
        StochasticTreeSearcher searcherObject = new StochasticTreeSearcher(
            battleView,
            this.getMaxDepth(),
            this.getMyTeamIdx()
        );

        // submit the job
        Future<Pair<MoveView, Long> > future = backgroundThreadManager.submit(searcherObject);

        try
        {
            // set the timeout
            Pair<MoveView, Long> moveAndDuration = future.get(
                this.getMaxThinkingTimePerMoveInMS(),
                TimeUnit.MILLISECONDS
            );

            // if we get here the move was chosen quick enough! :)
            move = moveAndDuration.getFirst();
            durationInMs = moveAndDuration.getSecond();

            // convert the move into a text form (algebraic notation) and stream it somewhere
            // Streamer.getStreamer(this.getFilePath()).streamMove(move, Planner.getPlanner().getGame());
        } catch(TimeoutException e)
        {
            // timeout = out of time...you lose!
            System.err.println("Timeout!");
            System.err.println("Team [" + (this.getMyTeamIdx()+1) + " loses!");
            System.exit(-1);
        } catch(InterruptedException e)
        {
            e.printStackTrace();
            System.exit(-1);
        } catch(ExecutionException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
        return move;
    }
}
