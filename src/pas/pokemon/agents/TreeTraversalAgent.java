package src.pas.pokemon.agents;


// SYSTEM IMPORTS....feel free to add your own imports here! You may need/want to import more from the .jar!
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.utils.Pair;
import edu.bu.pas.pokemon.core.enums.Stat;
import src.pas.pokemon.agents.TreeNode;
import src.pas.pokemon.agents.TreeNode.NodeType;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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

	public class StochasticTreeSearcher
        extends Object
        implements Callable<Pair<MoveView, Long> >  // so this object can be run in a background thread
	{

        // TODO: feel free to add any fields here! If you do, you should probably modify the constructor
        // of this class and add some getters for them. If the fields you add aren't final you should add setters too!
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
        public MoveView stochasticTreeSearch(BattleView rootView) //, int depth)
        {
            //checks if enemy or us moves first
            TreeNode root = new TreeNode(rootView, null, 1.0, NodeType.MOVE_ORDER_CHANCE); 
            root.expand(myTeamIdx);

            MoveView bestMove = null;
            double bestValue = Double.NEGATIVE_INFINITY;

            for (TreeNode child : root.getChildren()) {
                Pair<MoveView, Double> eval = expectimax(child, maxDepth);
                if (eval.getSecond() > bestValue) {
                    bestValue = eval.getSecond();
                    if(child.getMove() != null){
                        bestMove = child.getMove();
                    }else{
                        bestMove = eval.getFirst();
                    }
                }
            }
            return bestMove;
        }


        public double evaluateState(BattleView state) {
            double myHPScore = 0.0, opponentHPScore = 0.0;
            double myAtkBonus = 0.0, opponentAtkBonus = 0.0;
            double mySpAtkBonus = 0.0, opponentSpAtkBonus = 0.0;
            double myDefBonus = 0.0, opponentDefBonus = 0.0;
            double mySpDefBonus = 0.0, opponentSpDefBonus = 0.0;
            double mySpeedBonus = 0.0, opponentSpeedBonus = 0.0;
            double myStatusPenalty = 0.0, opponentStatusPenalty = 0.0;
        
            Team.TeamView myTeam = getMyTeamView(state);
            Team.TeamView opponentTeam = getOpponentTeamView(state);
        
            for (int i = 0; i < myTeam.size(); i++) {
                PokemonView pokemon = myTeam.getPokemonView(i);
                if (!myTeam.getPokemonView(i).hasFainted()) {
                    myHPScore += (double) pokemon.getCurrentStat(Stat.HP) / pokemon.getBaseStat(Stat.HP);
                    myAtkBonus += (double) pokemon.getCurrentStat(Stat.ATK) / pokemon.getBaseStat(Stat.ATK);
                    mySpAtkBonus += (double) pokemon.getCurrentStat(Stat.SPATK) / pokemon.getBaseStat(Stat.SPATK);
                    myDefBonus += (double) pokemon.getCurrentStat(Stat.DEF) / pokemon.getBaseStat(Stat.DEF);
                    mySpDefBonus += (double) pokemon.getCurrentStat(Stat.SPDEF) / pokemon.getBaseStat(Stat.SPDEF);
                    mySpeedBonus += (double) pokemon.getCurrentStat(Stat.SPD) / pokemon.getBaseStat(Stat.SPD);
                    myStatusPenalty += getStatusEffectPenalty(pokemon);
                }
            }
        
            for (int i = 0; i < opponentTeam.size(); i++) {
                PokemonView pokemon = opponentTeam.getPokemonView(i);
                if (!pokemon.hasFainted()) {
                    opponentHPScore += (double) pokemon.getCurrentStat(Stat.HP) / pokemon.getBaseStat(Stat.HP);
                    opponentAtkBonus += (double) pokemon.getCurrentStat(Stat.ATK) / pokemon.getBaseStat(Stat.ATK);
                    opponentSpAtkBonus += (double) pokemon.getCurrentStat(Stat.SPATK) / pokemon.getBaseStat(Stat.SPATK);
                    opponentDefBonus += (double) pokemon.getCurrentStat(Stat.DEF) / pokemon.getBaseStat(Stat.DEF);
                    opponentSpDefBonus += (double) pokemon.getCurrentStat(Stat.SPDEF) / pokemon.getBaseStat(Stat.SPDEF);
                    opponentSpeedBonus += (double) pokemon.getCurrentStat(Stat.SPD) / pokemon.getBaseStat(Stat.SPD);
                    opponentStatusPenalty += getStatusEffectPenalty(pokemon);
                }
            }

            //after fully using razor leaf, bulbasaur uses vine whip. could be out of mp
            //lvl doesnt seem to matter. lvl 13 could use lvl 27 moves
        
            double hpWeight = -20, atkWeight = -20, spatkWeight = 3, defWeight = 0.5, spdefWeight = 0.5, speedWeight = 1, statusPenaltyWeight = 1.2;
        
            double myScore = (hpWeight * myHPScore + atkWeight * myAtkBonus + spatkWeight * mySpAtkBonus 
                             + defWeight * myDefBonus + spdefWeight * mySpDefBonus + speedWeight * mySpeedBonus 
                             - statusPenaltyWeight * myStatusPenalty);
        
            double opponentScore = (hpWeight * opponentHPScore + atkWeight * opponentAtkBonus + spatkWeight * opponentSpAtkBonus
                                   + defWeight * opponentDefBonus + spdefWeight * opponentSpDefBonus + speedWeight * opponentSpeedBonus
                                   - statusPenaltyWeight * opponentStatusPenalty);
        
            return myScore - opponentScore;
        }

        private double getStatusEffectPenalty(PokemonView pokemon) {
            double penalty = 0.0;
            
            //persistent status stuff. check if the penalties stack
            
            return penalty;
        }

        private Pair<MoveView, Double> expectimax(TreeNode node, int depth) {
            //either reached max depth or terminal
            if(depth == 0 || node.isTerminal()){
                return new Pair<>(node.getMove(), evaluateState(node.getState()));
            }

            if(node.getChildren().isEmpty() && depth > 0){
                node.expand(myTeamIdx);
            }

            switch (node.getType()) {
                case MAX:
                    double best = Double.NEGATIVE_INFINITY;
                    MoveView bestMove = null;
                    for (TreeNode child : node.getChildren()) {
                        Pair<MoveView, Double> childEval = expectimax(child, depth - 1);
                        if(childEval.getSecond() > best){
                            best = childEval.getSecond();
                            if(child.getMove() != null){
                                bestMove = child.getMove();
                            }else{
                                bestMove = childEval.getFirst();
                            }
                        }
                    }
                    return new Pair<>(bestMove, best);

                case MIN:
                    int opponentIdx = 1; //opponent idx should be 1, could add an checker
                    double worst = Double.POSITIVE_INFINITY;
                    MoveView worstMove = null;
                    for (TreeNode child : node.getChildren()) {
                        Pair<MoveView, Double> childEval = expectimax(child, depth - 1);
                        if(childEval.getSecond() < worst){
                            worst = childEval.getSecond();
                            if(child.getMove() != null){
                                worstMove = child.getMove();
                            }else{
                                worstMove = childEval.getFirst();
                            }
                        }
                    }
                    return new Pair<>(worstMove, worst);

                case MOVE_ORDER_CHANCE:
                case MOVE_RESOLUTION_CHANCE:
                case POST_TURN_CHANCE: {
                    double expectedValue = 0.0;
                    MoveView expansion = null;
                    for (TreeNode child : node.getChildren()){
                        Pair<MoveView, Double> childEval = expectimax(child, depth - 1);
                        expectedValue += child.getProbability() * childEval.getSecond();
                        if (expansion == null && childEval.getFirst() != null){
                            expansion = childEval.getFirst();
                        }
                    }
                    return new Pair<>(expansion, expectedValue);
                }
                default:
                    return new Pair<>(node.getMove(), evaluateState(node.getState()));
            }
        }


        @Override
        public Pair<MoveView, Long> call() throws Exception
        {
            double startTime = System.nanoTime();

            MoveView move = this.stochasticTreeSearch(this.getRootView());
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
        this.maxDepth = 10; // set this however you want
    }

    /**
     * Some constants
     */
    public int getMaxDepth() { return this.maxDepth; }
    public long getMaxThinkingTimePerMoveInMS() { return this.maxThinkingTimePerMoveInMS; }

    @Override
    public Integer chooseNextPokemon(BattleView view)
    {
        
        //can improve, currently just chooses next available

        // It is likely a good idea to expand a bunch of trees with different choices as the active pokemon on your
        // team, and see which pokemon is your best choice by comparing the values of the root nodes.

        for(int idx = 0; idx < this.getMyTeamView(view).size(); ++idx)
        {
            if(!this.getMyTeamView(view).getPokemonView(idx).hasFainted())
            {
                return idx;
            }
        }
        return null;
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
