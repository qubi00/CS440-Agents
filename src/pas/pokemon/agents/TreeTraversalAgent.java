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
import src.pas.pokemon.agents.TreeTraversalAgent.TreeNode.NodeType;
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
    public static final double low_prob = 0.1;
    public static int max_depth = 1;

    public static class TreeNode {
        public enum NodeType{
            MOVE_ORDER_CHANCE,
            DETERMINISTIC, 
            MEGA_CHANCE,
            POST_TURN
        };
    
        private BattleView state;
        private MoveView move;
        private Set<MoveView> moveSet;
        private double probability;
        private List<TreeNode> children; //future states
        private NodeType type;
        private boolean isMax;
        private int depth;
    
        public TreeNode(BattleView state, MoveView move, Set<MoveView> moveSet, double probability, NodeType type, boolean isMax, int depth) {
            this.state = state;
            this.move = move;
            this.moveSet = moveSet;
            this.probability = probability;
            this.children = new ArrayList<>();
            this.type = type;
            this.isMax = isMax;
            this.depth = depth;
        }
    
    
        public BattleView getState() {return state;}
        public MoveView getMove() {return move;}
        public Set<MoveView> getMoveSet() {return moveSet;}
        public double getProbability() {return probability;}
        public List<TreeNode> getChildren() {return children;}
        public NodeType getType() {return type;}

        private boolean atMaxDepth() {
            return (this.depth >= max_depth);
        }

        public void expandMoveOrder(BattleView state, int myIdx, int opIdx){
            if(atMaxDepth()){
                return;
            }

            PokemonView p1 = state.getTeamView(myIdx).getActivePokemonView();
            PokemonView p2 = state.getTeamView(opIdx).getActivePokemonView();

            List<MoveView> p1_moves = p1.getAvailableMoves();
            List<MoveView> p2_moves = p2.getAvailableMoves();
            int p1_speed = p1.getCurrentStat(Stat.SPD);
            int p2_speed = p2.getCurrentStat(Stat.SPD);

            Map<MoveView, Set<MoveView>> playerFirst = new HashMap<>();
            Map<MoveView, Set<MoveView>> enemyFirst = new HashMap<>();

            for(MoveView m1 : p1_moves){
                for(MoveView m2 : p2_moves){
                    boolean usFirst;
                    if (m1.getPriority() > m2.getPriority()) {
                        usFirst = true;
                    }else if(m1.getPriority() < m2.getPriority()){
                        usFirst = false;
                    }else{ //equal priority
                        if(p1_speed > p2_speed){
                            usFirst = true;
                        }else if(p1_speed < p2_speed){
                            usFirst = false;
                        }else{ //both us and enemy first
                            usFirst = true;
                            enemyFirst.computeIfAbsent(m2, k -> new HashSet<>()).add(m1);
                        }
                    }
                    if(usFirst){
                        playerFirst.computeIfAbsent(m1, k -> new HashSet<>()).add(m2);
                    } else {
                        enemyFirst.computeIfAbsent(m2, k -> new HashSet<>()).add(m1);
                    }
                }
        }
            children.clear();
            //player first subtree
            for(Map.Entry<MoveView, Set<MoveView>> entry : playerFirst.entrySet()){
                MoveView ourMove = entry.getKey();
                Set<MoveView> opponentValidMoves = entry.getValue();
                children.add(new TreeNode(state, ourMove, opponentValidMoves, 1.0, NodeType.DETERMINISTIC, true, depth+1));
            }
        
            //enemy first subtree
            for(Map.Entry<MoveView, Set<MoveView>> entry : enemyFirst.entrySet()){
                Set<MoveView> ourValidMoves = entry.getValue();
                for (MoveView ourMove : ourValidMoves) {
                    children.add(new TreeNode(state, ourMove, Collections.singleton(ourMove), 1.0, NodeType.DETERMINISTIC, false, depth+1));
                }
            }
        }
    
    
    
        //expand all possible moves for max/min nodes
        public void expandDeterministic(int teamIdx, int myIdx, int opIdx){
            if(atMaxDepth()){
                return;
            }
            if(teamIdx == myIdx){
                this.isMax = true;
            }else{
                this.isMax = false;
            }

            PokemonView p1 = state.getTeamView(myIdx).getActivePokemonView();
            PokemonView p2 = state.getTeamView(opIdx).getActivePokemonView();

            List<MoveView> p1_moves = p1.getAvailableMoves();
            List<MoveView> p2_moves = p2.getAvailableMoves();
            int p1_speed = p1.getCurrentStat(Stat.SPD);
            int p2_speed = p2.getCurrentStat(Stat.SPD);

            Map<MoveView, Set<MoveView>> playerFirst = new HashMap<>();
            Map<MoveView, Set<MoveView>> enemyFirst = new HashMap<>();

            for(MoveView m1 : p1_moves){
                for(MoveView m2 : p2_moves){
                    boolean usFirst;
                    if (m1.getPriority() > m2.getPriority()) {
                        usFirst = true;
                    }else if(m1.getPriority() < m2.getPriority()){
                        usFirst = false;
                    }else{ //equal priority
                        if(p1_speed > p2_speed){
                            usFirst = true;
                        }else if(p1_speed < p2_speed){
                            usFirst = false;
                        }else{ //both us and enemy first
                            usFirst = true;
                            enemyFirst.computeIfAbsent(m2, k -> new HashSet<>()).add(m1);
                        }
                    }
                    if(usFirst){
                        playerFirst.computeIfAbsent(m1, k -> new HashSet<>()).add(m2);
                    } else {
                        enemyFirst.computeIfAbsent(m2, k -> new HashSet<>()).add(m1);
                    }
                }
        }
            children.clear();
            
            for(Map.Entry<MoveView, Set<MoveView>> entry : playerFirst.entrySet()){
                MoveView move = entry.getKey();
                Set<MoveView> oppMoveSet = entry.getValue();
                List<Pair<Double, BattleView>> outcomes = move.getPotentialEffects(state, myIdx, opIdx);
                for(Pair<Double, BattleView> outcome : outcomes){
                    TreeNode megaNode = new TreeNode(outcome.getSecond(), move, oppMoveSet, outcome.getFirst(), NodeType.MEGA_CHANCE, this.isMax, depth+1);
                    megaNode.expandMoveResolution(myIdx, opIdx);
                    children.add(megaNode);
                }
            }
            
            for(Map.Entry<MoveView, Set<MoveView>> entry : enemyFirst.entrySet()){
                MoveView move = entry.getKey();
                Set<MoveView> ourMoveSet = entry.getValue();
                List<Pair<Double, BattleView>> outcomes = move.getPotentialEffects(state, myIdx, opIdx);
                for(Pair<Double, BattleView> outcome : outcomes){
                    TreeNode megaNode = new TreeNode(outcome.getSecond(), move, ourMoveSet, outcome.getFirst(),
                        NodeType.MEGA_CHANCE, !this.isMax, depth+1);
                    megaNode.expandMoveResolution(myIdx, opIdx);
                    children.add(megaNode);
                }
            }
        }
        


        public void expandMoveResolution(int myIdx, int opIdx){
            if(atMaxDepth()){
                return;
            }
            //check this, sometimes not first
            int teamIdx = 0;
            if(isMax){
                teamIdx = myIdx;
            }else{
                teamIdx = opIdx;
            }
            
            PokemonView pokemon = state.getTeamView(teamIdx).getActivePokemonView();
            if(pokemon.hasFainted()){
                if(!pokemon.getFlag(Flag.TRAPPED)){
                    PokemonView newActive =  state.getTeamView(teamIdx).getActivePokemonView();
                    Set<MoveView> newMoveSet = new HashSet<>(newActive.getAvailableMoves());
                    TreeNode switchBranch = new TreeNode(state, null, newMoveSet, 1.0, 
                    NodeType.POST_TURN, isMax, depth + 1);
                    switchBranch.expandPostTurn(myIdx, opIdx);
                    children.add(switchBranch);
                }
                return;
            }

            switch(pokemon.getNonVolatileStatus()){
                case SLEEP:
                    double wakeProb = 0.098;
                    double sleepProb = 1.0 - wakeProb;
                    
                    TreeNode wakeBranch = new TreeNode(state, this.getMove(), this.moveSet, wakeProb, NodeType.MEGA_CHANCE, isMax, depth+1);
                    if(isMax){
                        wakeBranch.expandDeterministic(opIdx, myIdx, opIdx);
                    } else {
                        wakeBranch.expandDeterministic(myIdx, myIdx, opIdx);
                    }
                    
                    TreeNode sleepBranch = new TreeNode(state, this.getMove(), this.moveSet, sleepProb, NodeType.POST_TURN, isMax, depth+1);
                    sleepBranch.expandPostTurn(myIdx, opIdx);
                    children.add(wakeBranch);
                    children.add(sleepBranch);
                    break;
                case PARALYSIS:
                    double moveProb = 0.75;
                    double loseProb = 1.0 - moveProb;
                    TreeNode moveBranch = new TreeNode(state, this.getMove(), this.moveSet, moveProb, NodeType.MEGA_CHANCE, isMax, depth+1);
                    if(isMax){
                        moveBranch.expandDeterministic(opIdx, myIdx, opIdx);
                    } else {
                        moveBranch.expandDeterministic(myIdx, myIdx, opIdx);
                    }
                    TreeNode loseBranch = new TreeNode(state, this.getMove(), this.moveSet, loseProb, NodeType.POST_TURN, isMax, depth+1);
                    loseBranch.expandPostTurn(myIdx, opIdx);
                    children.add(moveBranch);
                    children.add(loseBranch);
                    break;
                case FREEZE:
                    double thawProb = 0.098;
                    double remainProb = 1.0 - thawProb;
                    TreeNode thawBranch = new TreeNode(state, this.getMove(), this.moveSet, thawProb, NodeType.MEGA_CHANCE, isMax, depth+1);
                    if(isMax){
                        thawBranch.expandDeterministic(opIdx, myIdx, opIdx);
                    } else {
                        thawBranch.expandDeterministic(myIdx, myIdx, opIdx);
                    }
                    TreeNode frozenBranch = new TreeNode(state, this.getMove(), this.moveSet, remainProb, NodeType.POST_TURN, isMax, depth+1);
                    frozenBranch.expandPostTurn(myIdx, opIdx);
                    children.add(thawBranch);
                    children.add(frozenBranch);
                    break;
                case POISON:
                    TreeNode poisonBranch = new TreeNode(state, this.getMove(), this.moveSet, 1.0, NodeType.POST_TURN, isMax, depth+1);
                    poisonBranch.expandPostTurn(myIdx, opIdx);
                    children.add(poisonBranch);
                    break;
                case BURN:
                    TreeNode burnBranch = new TreeNode(state, this.getMove(), this.moveSet, 1.0, NodeType.POST_TURN, isMax, depth+1);
                    burnBranch.expandPostTurn(myIdx, opIdx);
                    children.add(burnBranch);
                    break;
                case TOXIC:
                    TreeNode toxicBranch = new TreeNode(state, this.getMove(), this.moveSet, 1.0, NodeType.POST_TURN, isMax, depth+1);
                    toxicBranch.expandPostTurn(myIdx, opIdx);
                    children.add(toxicBranch);
                    break;
                case NONE:
                    TreeNode nextPhase = new TreeNode(state, this.getMove(), this.moveSet, 1.0, NodeType.DETERMINISTIC, isMax, depth + 1);
                    if(!state.isOver() && this.depth < max_depth){
                        if(isMax){
                            nextPhase.expandDeterministic(opIdx, myIdx, opIdx);
                        }else{
                            nextPhase.expandDeterministic(myIdx, myIdx, opIdx);
                        }
                    }
                    children.add(nextPhase);
                    break;
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
                .getView().getPotentialEffects(state, myIdx, opIdx);
                for(Pair<Double, BattleView> outcome : confusionDamageOutcomes){
                    TreeNode outcomeBranch = new TreeNode(outcome.getSecond(), null, this.moveSet, 
                    outcome.getFirst(), NodeType.POST_TURN, isMax, depth+1);
                    outcomeBranch.expandPostTurn(myIdx, opIdx);
                    children.add(outcomeBranch);
                }
            }else if(pokemon.getFlag(Flag.SEEDED)){
                    TreeNode seedBranch = new TreeNode(state, this.getMove(), this.moveSet, 1.0, NodeType.POST_TURN, isMax, depth+1);
                    seedBranch.expandPostTurn(myIdx, opIdx);
                    children.add(seedBranch);
            }else if(pokemon.getFlag(Flag.FLINCHED)){
                TreeNode flinchBranch = new TreeNode(state, this.getMove(), this.moveSet, 1.0, NodeType.POST_TURN, isMax, depth+1);
                flinchBranch.expandPostTurn(myIdx, opIdx);
                children.add(flinchBranch);
            }else if(pokemon.getFlag(Flag.FOCUS_ENERGY)){
                TreeNode focusEnergyBranch = new TreeNode(state, this.getMove(), this.moveSet, 1.0, NodeType.DETERMINISTIC, isMax, depth+1);
                if(isMax){
                    focusEnergyBranch.expandDeterministic(opIdx, myIdx, opIdx);
                } else {
                    focusEnergyBranch.expandDeterministic(myIdx, myIdx, opIdx);
                }
                children.add(focusEnergyBranch);
            }else if(pokemon.getFlag(Flag.TRAPPED)) {
                TreeNode trappedBranch = new TreeNode(state, this.getMove(), this.moveSet, 1.0, NodeType.DETERMINISTIC, isMax, depth+1);
                if(isMax){
                    trappedBranch.expandDeterministic(opIdx, myIdx, opIdx);
                } else {
                    trappedBranch.expandDeterministic(myIdx, myIdx, opIdx);
                }
                children.add(trappedBranch);
            }
        }


        public void expandPostTurn(int myIdx, int opIdx){
            if(atMaxDepth()){
                return;
            }
            List<BattleView> postTurnStates = this.getState().applyPostTurnConditions();
            //terminal
            if(postTurnStates.isEmpty() || this.getState().isOver()){
                return;
            }else{
                //not done, turn 2, etc.
                for(BattleView newState : postTurnStates) {
                    TreeNode moveOrderNode = new TreeNode(newState, null, null, 1.0, 
                        NodeType.MOVE_ORDER_CHANCE, this.isMax, depth+1);
                    moveOrderNode.expandMoveOrder(newState, myIdx, opIdx);
                    children.add(moveOrderNode);
                }
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
            TreeNode root = new TreeNode(rootView, null, null, 1.0, NodeType.MOVE_ORDER_CHANCE, true, 0); 

            int myIdx = getMyTeamIdx();
            int opIdx;
            if(myIdx == 0){
                opIdx = 1;
            }else{
                opIdx = 0;
            }

            root.expandMoveOrder(rootView, myIdx, opIdx);

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
        
            int myIdx = getMyTeamIdx();
            int opIdx;
            if(myIdx == 0){
                opIdx = 1;
            }else{
                opIdx = 0;
            }

            if(node.getChildren().isEmpty()){
                if(node.getType() == NodeType.DETERMINISTIC){
                    if(node.isMax){
                        node.expandDeterministic(myIdx, myIdx, opIdx);
                    }else{
                        node.expandDeterministic(opIdx, myIdx, opIdx);
                    }
                }else if(node.getType() == TreeNode.NodeType.MOVE_ORDER_CHANCE){
                    node.expandMoveOrder(node.state, myIdx, opIdx);
                }else if(node.getType() == TreeNode.NodeType.MEGA_CHANCE){
                    node.expandMoveResolution(myIdx, opIdx);
                }else if(node.getType() == TreeNode.NodeType.POST_TURN){
                    node.expandPostTurn(myIdx, opIdx);
                }
            }

            //best/worst depending on node
            if(node.getType() == TreeNode.NodeType.DETERMINISTIC){
                if(node.isMax){
                    Collections.sort(node.getChildren(), (a, b) ->
                        Double.compare(getHPAdvantage(b.getState()), getHPAdvantage(a.getState()))
                    );
                }else{
                    Collections.sort(node.getChildren(), (a, b) ->
                        Double.compare(getHPAdvantage(a.getState()), getHPAdvantage(b.getState()))
                    );
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
                case MEGA_CHANCE:
                case POST_TURN: {
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





        //for heuristic



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

            final double aliveBonus = 5000.0;
            final double hpWeight = 1000.0;
            final double typeAdvantageWeight = 2000.0;//prob need to put this in a dmg calc

            
            double myScore = 0;
            double opponentScore = 0;
            for(int i = 0; i < myTeam.size(); i++){
                PokemonView p = myTeam.getPokemonView(i);
                if (!p.hasFainted()) {
                    double hpRatio = (double) p.getCurrentStat(Stat.HP) / p.getBaseStat(Stat.HP);
                    myScore += aliveBonus + hpRatio * hpWeight;
                }
            }
            for(int i = 0; i < opponentTeam.size(); i++){
                PokemonView p = opponentTeam.getPokemonView(i);
                if (!p.hasFainted()) {
                    double hpRatio = (double) p.getCurrentStat(Stat.HP) / p.getBaseStat(Stat.HP);
                    opponentScore += aliveBonus + hpRatio * hpWeight;
                }
            }
            return 1000 * (myScore - opponentScore);
        }
        

        //terminal. if out pokemon faints, return 0 or some low number. if enemy faints, return high number
        public double evaluateState(BattleView state) {
            final double hpWeight = 1.0;
            final double faintedPenalty = 100.0;
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
                    myScore += faintedPenalty;
                }
            }

            return 1000 * (myScore - opponentScore);
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
        this.maxDepth = 1; // set this however you want
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
