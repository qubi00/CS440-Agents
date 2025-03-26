package src.pas.pokemon.agents;


import edu.bu.labs.pokemon.core.enums.NonVolatileStatus;
// SYSTEM IMPORTS....feel free to add your own imports here! You may need/want to import more from the .jar!
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.utils.Pair;
import src.pas.pokemon.agents.TreeTraversalAgent.TreeNode.NodeType;
import edu.bu.pas.pokemon.core.enums.Flag;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.core.callbacks.Callback;
import edu.bu.pas.pokemon.core.callbacks.DoDamageCallback;
import edu.bu.pas.pokemon.core.callbacks.MultiCallbackCallback;
import edu.bu.pas.pokemon.core.callbacks.ResetLastDamageDealtCallback;

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
    public static class TreeNode {
        public enum NodeType{
            DETERMINISTIC,
            MOVE_ORDER_CHANCE,
            MOVE_RESOLUTION_CHANCE,
            POST_TURN_CHANCE
        };
    
        private BattleView state;
        private MoveView lastMove;
        private double probability;
        private List<TreeNode> children; //future states
        private NodeType type;
        private boolean isMax;
    
        public TreeNode(BattleView state, MoveView move, double probability, NodeType type, boolean isMax) {
            this.state = state;
            this.lastMove = move;
            this.probability = probability;
            this.children = new ArrayList<>();
            this.type = type;
            this.isMax = isMax;
        }
    
    
        public BattleView getState() {return state;}
        public MoveView getMove() {return lastMove;}
        public double getProbability() {return probability;}
        public List<TreeNode> getChildren() {return children;}
        public NodeType getType() {return type;}


        public void expandMoveOrder(BattleView state, int speed1, int speed2){
            //check speed
            double probPlayer = 0;
            double probEnemy = 0;

            if(speed1 > speed2){
                probPlayer = 1;
            }else if(speed2 > speed1){
                probEnemy = 1;
            }else{
                probPlayer = .5;
                probEnemy = .5;
            }
            //can prune here with speed
            TreeNode playerNode = new TreeNode(state, null, probPlayer, NodeType.DETERMINISTIC, true);
            TreeNode enemyNode = new TreeNode(state, null, probEnemy, NodeType.DETERMINISTIC, false);

            if(probPlayer == 0){
                children.add(enemyNode);
            }else if(probEnemy == 0){
                children.add(playerNode);
            }else{
                children.add(playerNode);
                children.add(enemyNode);
            }
        }
    
    
    
        //expand all possible moves for max/min nodes
        public void expandDeterministic(int teamIdx) {
            List<MoveView> legalMoves = state.getTeamView(teamIdx).getActivePokemonView().getAvailableMoves();
            for(MoveView move : legalMoves){
                List<Pair<Double, BattleView>> outcomes = move.getPotentialEffects(state, 0, 0);
                for(Pair<Double, BattleView> outcome : outcomes){
                    TreeNode child = new TreeNode(outcome.getSecond(), move, outcome.getFirst(), NodeType.MOVE_RESOLUTION_CHANCE, !isMax);
                    child.expandMoveResolution();
                    children.add(child);
                }
            }
        }


        public void expandMoveResolution(){
            int teamIdx = 0;
            if(isMax){
                teamIdx = 0;
            }else{
                teamIdx = 1;
            }
            
            PokemonView pokemon = state.getTeamView(teamIdx).getActivePokemonView();

            switch(pokemon.getNonVolatileStatus()){
                case SLEEP:
                case PARALYSIS:
                case FREEZE:
                    double successProb = 0.7;
                    double failProb = 0.3;
                    //success
                    TreeNode successBranch = new TreeNode(state, lastMove, successProb, NodeType.MOVE_RESOLUTION_CHANCE, isMax);
                    successBranch.expandMoveResolutionChance();
                    //fail
                    TreeNode failBranch = new TreeNode(state, lastMove, failProb, NodeType.POST_TURN_CHANCE, isMax);
                    children.add(successBranch);
                    children.add(failBranch);
                    break;
                case POISON:
                    break;
                case BURN:
                    break;
                case TOXIC:
                    break;
                case NONE:
                    this.expandMoveResolutionChance();
            }
            if(pokemon.getFlag(Flag.CONFUSED) == true){
                Move hurtYourselfMove = new Move(
                "SelfDamage",          // Move name
                Type.NORMAL,           // Damage type (treated as typeless)
                Move.Category.PHYSICAL,// Move category
                40,                    // Base power (standard for confusion)
                null,                  // Infinite accuracy
                Integer.MAX_VALUE,      // PP (number of uses)
                1,                     // Critical hit ratio
                0                      // Priority
                ).addCallback(
                    new MultiCallbackCallback(
                    new  ResetLastDamageDealtCallback(), // Reset last damage so it calculates fresh.
                    new DoDamageCallback(
                        edu.bu.pas.pokemon.core.enums.Target.CASTER, // Hurt yourself
                        false, // Don't include STAB term in damage calculation
                        false, // Ignore type effectiveness
                        true   // Damage ignores substitutes
                    )));
                List<Pair<Double, BattleView>> confusionDamageOutcomes = hurtYourselfMove
                .getView().getPotentialEffects(state, 0, 0);
                for(Pair<Double, BattleView> outcome : confusionDamageOutcomes){
                    TreeNode outcomeBranch = new TreeNode(outcome.getSecond(), lastMove, outcome.getFirst(), NodeType.POST_TURN_CHANCE, isMax);
                    outcomeBranch.expandPostTurnChance();
                    children.add(outcomeBranch);
                }
        
            }
        }


        public void expandMoveResolutionChance(){
            List<Pair<Double, BattleView>> outcomes = lastMove.getPotentialEffects(state, 0, 0);
            for (Pair<Double, BattleView> outcome : outcomes) {
                double moveProb = outcome.getFirst();
                BattleView moveState = outcome.getSecond();
        
                double finalProb = this.probability * moveProb;
                TreeNode postTurnNode = new TreeNode(moveState, lastMove, finalProb, NodeType.POST_TURN_CHANCE, isMax);
                children.add(postTurnNode);
            }
        }


        public void expandPostTurnChance(){
            if(state.isOver()){
                return;
            }else{
                //not done, turn 2, etc.
                int speed1 = state.getTeamView(0).getActivePokemonView().getCurrentStat(Stat.SPD);
                int speed2 = state.getTeamView(1).getActivePokemonView().getCurrentStat(Stat.SPD);
                TreeNode moveOrderNode = new TreeNode(state, null, 1.0, NodeType.MOVE_ORDER_CHANCE, true);
                moveOrderNode.expandMoveOrder(state, speed1, speed2);
                children.add(moveOrderNode);
            }
        }
    
    }
    

	public class StochasticTreeSearcher
        extends Object
        implements Callable<Pair<MoveView, Long> >  // so this object can be run in a background thread
	{

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
            TreeNode root = new TreeNode(rootView, null, 1.0, NodeType.MOVE_ORDER_CHANCE, true); 
            int speed1 = rootView.getTeam1View().getActivePokemonView().getCurrentStat(Stat.SPD);
            int speed2 = rootView.getTeam2View().getActivePokemonView().getCurrentStat(Stat.SPD);
            root.expandMoveOrder(rootView, speed1, speed2);

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


        public Pair<MoveView, Double> expectimax(TreeNode node, int depth) {
            //either reached max depth or terminal
            if(depth == 0){
                return new Pair<>(node.getMove(), getHPAdvantage(node.getState()));
            }else if(node.getState().isOver()){
                return new Pair<>(node.getMove(), evaluateState(node.getState()));
            }

            if(node.getChildren().isEmpty()){
                if(node.getType() == NodeType.DETERMINISTIC){
                    if(node.isMax){
                        node.expandDeterministic(getMyTeamIdx());
                    }else{
                        node.expandDeterministic(1 - getMyTeamIdx());
                    }
                }else if(node.getType() == TreeNode.NodeType.MOVE_ORDER_CHANCE){
                    int speed1 = node.state.getTeamView(0).getActivePokemonView().getCurrentStat(Stat.SPD);
                    int speed2 = node.state.getTeamView(1).getActivePokemonView().getCurrentStat(Stat.SPD);
                    node.expandMoveOrder(node.state, speed1, speed2);
                }else if(node.getType() == TreeNode.NodeType.MOVE_RESOLUTION_CHANCE){
                    node.expandMoveResolution();
                }else if(node.getType() == TreeNode.NodeType.POST_TURN_CHANCE){
                    node.expandPostTurnChance();
                }
            }

            switch(node.getType()){
                case DETERMINISTIC:
                    if(node.isMax){
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
                    }
                    else if(!node.isMax){
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
                    }

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


        public double getTypeEffectiveness(String moveType, String defenderType) {
            switch(moveType){
                case "NORMAL":
                    if(defenderType.equals("ROCK")){
                        return 1.0/2;
                    }else if(defenderType.equals("GHOST")){
                        return 0.0;
                    }
                    return 1.0;
                case "FIRE":
                    break;
                case "WATER":
                    break;
                case "ELECTRIC":
                    break;
                case "GRASS":
                    break;
                case "ICE":
                    break;
                case "FIGHTING":
                    break;
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
                case "FLYING":
                    break;
                case "PSYCHIC":
                    break;
                case "BUG":
                    break;
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
                case "GHOST":
                    break;
                case "DRAGON":
                    break;
            }
            return 1.0;
        }

        //use as utility if we cant reach a point where a pokemon faints
        public double getHPAdvantage(BattleView state) {
            TeamView myTeam = state.getTeam1View();
            TeamView opponentTeam = state.getTeam2View();

            boolean myAllFainted = true;
            for (int i = 0; i < myTeam.size(); i++) {
                if (!myTeam.getPokemonView(i).hasFainted()){
                    myAllFainted = false;
                    break;
                }
            }
            boolean oppAllFainted = true;
            for (int i = 0; i < opponentTeam.size(); i++) {
                if (!opponentTeam.getPokemonView(i).hasFainted()){
                    oppAllFainted = false;
                    break;
                }
            }
            if (myAllFainted) {
                return Double.NEGATIVE_INFINITY;
            }
            if (oppAllFainted) {
                return Double.POSITIVE_INFINITY;
            }

            
            double myHPRatio = 0;
            double opponentHPRatio = 0;
            for(int i = 0; i < myTeam.size(); i++){
                PokemonView p = myTeam.getPokemonView(i);
                int currentHP = p.getCurrentStat(Stat.HP);
                int maxHP = p.getBaseStat(Stat.HP);
                myHPRatio += (double)currentHP/maxHP;
            }
            for(int i = 0; i < opponentTeam.size(); i++){
                PokemonView p = opponentTeam.getPokemonView(i);
                int currentHP = p.getCurrentStat(Stat.HP);
                int maxHP = p.getBaseStat(Stat.HP);
                myHPRatio += (double)currentHP/maxHP;
            }
            return 100 * (myHPRatio - opponentHPRatio);
        }
        

        //terminal. if out pokemon faints, return 0 or some low number. if enemy faints, return high number
        public double evaluateState(BattleView state) {
            final double hpWeight = 1.0;
            final double faintedPenalty = 100.0;  // heavy penalty if one of our Pokémon is fainted
            double myScore = 0.0;
            double opponentScore = 0.0;
            
            Team.TeamView myTeam = getMyTeamView(state);
            Team.TeamView opponentTeam = getOpponentTeamView(state);
            
            for (int i = 0; i < myTeam.size(); i++) {
                PokemonView pokemon = myTeam.getPokemonView(i);
                if (!pokemon.hasFainted()) {
                    double hpRatio = (double) pokemon.getCurrentStat(Stat.HP) / pokemon.getBaseStat(Stat.HP);
                    myScore += hpWeight * hpRatio;
                } else {
                    myScore -= faintedPenalty;
                }
            }
            
            for(int i = 0; i < opponentTeam.size(); i++){
                PokemonView pokemon = opponentTeam.getPokemonView(i);
                if(!pokemon.hasFainted()){
                    double hpRatio = (double) pokemon.getCurrentStat(Stat.HP) / pokemon.getBaseStat(Stat.HP);
                    opponentScore += hpWeight * hpRatio;
                }else{
                    opponentScore += faintedPenalty;
                }
            }

            return myScore - opponentScore;
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
        this.maxDepth = 5; // set this however you want
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
